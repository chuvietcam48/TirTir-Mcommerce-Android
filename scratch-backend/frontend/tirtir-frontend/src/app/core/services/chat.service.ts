import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export interface ChatMessage {
    id?: string;
    text: string;
    sender: 'user' | 'bot';
    timestamp: Date;
    productData?: {
        id: string;
        name: string;
        price: number;
        image: string;
        desc: string;
        slug: string;
    };
}

export interface QuickReply {
    label: string;
    value: string;
    icon?: string;
}

export interface ChatStreamEvent {
    type: 'chunk' | 'done' | 'error';
    text?: string;
    payload?: any;
    message?: string;
}

const GUEST_HISTORY_KEY = 'guest_chat_history';

@Injectable({
    providedIn: 'root'
})
export class ChatService {
    private http = inject(HttpClient);
    private authService = inject(AuthService);
    private apiUrl = `${environment.apiUrl}/chat`;

    private messagesSubject = new BehaviorSubject<ChatMessage[]>([]);
    messages$ = this.messagesSubject.asObservable();

    // Store active audio to prevent overlapping and allow stopping
    private activeAudio: HTMLAudioElement | null = null;

    // Signal-based state for settings
    private alertSoundEnabledSignal = signal<boolean>(true);
    public alertSoundEnabled = computed(() => this.alertSoundEnabledSignal());

    private desktopNotificationEnabledSignal = signal<boolean>(false);
    public desktopNotificationEnabled = computed(() => this.desktopNotificationEnabledSignal());

    constructor() {
        this.loadSettings();
        this.checkNotificationPermission();
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    private loadSettings(): void {
        const savedSound = localStorage.getItem('chatAlertSound');
        if (savedSound !== null) {
            this.alertSoundEnabledSignal.set(savedSound === 'true');
        }
    }

    public toggleAlertSound(enabled: boolean): void {
        this.alertSoundEnabledSignal.set(enabled);
        localStorage.setItem('chatAlertSound', enabled.toString());
    }

    public async requestDesktopNotification(): Promise<void> {
        if (!('Notification' in window)) {
            console.warn('This browser does not support desktop notification');
            return;
        }
        if (Notification.permission === 'granted') {
            this.desktopNotificationEnabledSignal.set(true);
        } else if (Notification.permission !== 'denied') {
            const permission = await Notification.requestPermission();
            this.desktopNotificationEnabledSignal.set(permission === 'granted');
        }
    }

    private checkNotificationPermission(): void {
        if ('Notification' in window && Notification.permission === 'granted') {
            this.desktopNotificationEnabledSignal.set(true);
        }
    }

    // ─── History Persistence ──────────────────────────────────────────────────

    /**
     * Load history based on auth status:
     * - Authenticated → GET /api/v1/chat/history from MongoDB
     * - Guest         → parse sessionStorage
     * Returns an Observable that resolves when messages are loaded.
     */
    loadHistory(): Observable<ChatMessage[]> {
        if (this.authService.isAuthenticated()) {
            // Authenticated: fetch from the backend
            return this.http.get<{ success: boolean; data: ChatMessage[] }>(`${this.apiUrl}/history`).pipe(
                tap(response => {
                    if (response.success && response.data.length > 0) {
                        // Timestamps arrive as strings from JSON — coerce to Date
                        const history = response.data.map(msg => ({
                            ...msg,
                            timestamp: new Date(msg.timestamp)
                        }));
                        this.messagesSubject.next(history);
                    }
                }),
                catchError(err => {
                    console.error('[CHAT] Failed to load history from API:', err);
                    return of({ success: false, data: [] as ChatMessage[] });
                }),
                // Extract just the messages array for consumers
                catchError(() => of({ success: false, data: [] as ChatMessage[] })),
            ).pipe(
                tap(() => { }),
                catchError(() => of([] as ChatMessage[]))
            ) as Observable<ChatMessage[]>;
        } else {
            // Guest: load from sessionStorage
            try {
                const raw = sessionStorage.getItem(GUEST_HISTORY_KEY);
                if (raw) {
                    const parsed: ChatMessage[] = JSON.parse(raw).map((msg: any) => ({
                        ...msg,
                        timestamp: new Date(msg.timestamp)
                    }));
                    if (parsed.length > 0) {
                        this.messagesSubject.next(parsed);
                        return of(parsed);
                    }
                }
            } catch (e) {
                console.warn('[CHAT] Failed to parse guest session history:', e);
            }
            return of([]);
        }
    }

    /** Persist the current message list to sessionStorage for guest users. */
    private persistGuestHistory(messages: ChatMessage[]): void {
        try {
            sessionStorage.setItem(GUEST_HISTORY_KEY, JSON.stringify(messages));
        } catch (e) {
            console.warn('[CHAT] Failed to write guest history to sessionStorage:', e);
        }
    }

    /** Clear guest history from sessionStorage (e.g. when user logs in). */
    public clearGuestHistory(): void {
        sessionStorage.removeItem(GUEST_HISTORY_KEY);
    }

    /** Reset the in-memory messages (used when switching auth state). */
    public resetMessages(): void {
        this.messagesSubject.next([]);
    }

    /**
     * Silently set a welcome bot message as the first message if the stream is
     * still empty.  Does NOT play sound or trigger desktop notifications.
     */
    public initWithWelcome(text: string): void {
        if (this.messagesSubject.value.length === 0) {
            this.messagesSubject.next([{
                text,
                sender: 'bot',
                timestamp: new Date()
            }]);
        }
    }

    // ─── Messaging ────────────────────────────────────────────────────────────

    public handleIncomingMessage(message: ChatMessage): void {
        const currentMessages = this.messagesSubject.value;
        const updated = [...currentMessages, message];
        this.messagesSubject.next(updated);

        if (message.sender === 'bot') {
            // For guests: keep sessionStorage in sync after every bot reply
            if (!this.authService.isAuthenticated()) {
                this.persistGuestHistory(updated);
            }
            // Defer sound/notification until after Angular renders the bot message.
            // Without setTimeout(0), the sound fires while the typing indicator is
            // still visible because Angular CD hasn't run yet at this point in the
            // observable pipeline.
            setTimeout(() => {
                this.playAlertSound();
                this.showDesktopNotification(message);
            }, 0);
        }
    }

    /**
     * Send a message to the backend.
     * - Authenticated: backend auto-saves user + bot messages to MongoDB.
     * - Guest: after the bot reply, sessionStorage is updated via handleIncomingMessage.
     */
    sendMessage(text: string): Observable<ChatStreamEvent> {
        const newMessage: ChatMessage = {
            text,
            sender: 'user',
            timestamp: new Date()
        };

        // For guests, also persist the user message immediately
        if (!this.authService.isAuthenticated()) {
            const current = this.messagesSubject.value;
            const updated = [...current, newMessage];
            this.messagesSubject.next(updated);
            this.persistGuestHistory(updated);
        } else {
            // Optimistic update for auth users (backend will save async)
            this.handleIncomingMessage(newMessage);
        }

        return new Observable<ChatStreamEvent>((observer) => {
            const controller = new AbortController();

            const parseSseBlock = (eventBlock: string): { eventName: string; payload: any } | null => {
                const lines = eventBlock
                    .split('\n')
                    .map(line => line.trim())
                    .filter(Boolean);

                if (!lines.length) {
                    return null;
                }

                const eventLine = lines.find(line => line.startsWith('event:'));
                const eventName = eventLine ? eventLine.slice(6).trim() : 'message';
                const dataRaw = lines
                    .filter(line => line.startsWith('data:'))
                    .map(line => line.slice(5).trim())
                    .join('\n');

                if (!dataRaw) {
                    return null;
                }

                try {
                    return { eventName, payload: JSON.parse(dataRaw) };
                } catch {
                    return { eventName, payload: { text: dataRaw } };
                }
            };

            const toFriendlyError = (payload: any): string => {
                return payload?.message || payload?.error || '❌ Chatbot service đang gặp sự cố. Vui lòng thử lại sau.';
            };

            (async () => {
                let streamedText = '';
                try {
                    const token = this.authService.getToken();
                    const response = await fetch(`${this.apiUrl}`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            Accept: 'text/event-stream',
                            ...(token ? { Authorization: `Bearer ${token}` } : {})
                        },
                        body: JSON.stringify({ message: text }),
                        signal: controller.signal,
                    });

                    if (!response.ok) {
                        throw new Error(`Chat API failed with status ${response.status}`);
                    }

                    if (!response.body) {
                        throw new Error('SSE stream body is missing');
                    }

                    const reader = response.body.getReader();
                    const decoder = new TextDecoder();
                    let buffer = '';

                    while (true) {
                        const { value, done } = await reader.read();
                        if (done) {
                            if (buffer.trim()) {
                                const parsed = parseSseBlock(buffer);
                                if (parsed) {
                                    if (parsed.eventName === 'chunk') {
                                        const chunkText = parsed.payload?.text || '';
                                        streamedText += chunkText;
                                        observer.next({ type: 'chunk', text: chunkText });
                                    } else if (parsed.eventName === 'done') {
                                        const botData = parsed.payload?.data;
                                        const botText = botData?.message || streamedText;
                                        if (botText) {
                                            const botMessage: ChatMessage = {
                                                text: botText,
                                                sender: 'bot',
                                                timestamp: new Date(),
                                                ...(botData?.type === 'product' && botData?.data
                                                    ? { productData: botData.data }
                                                    : {})
                                            };
                                            this.handleIncomingMessage(botMessage);
                                        }
                                        observer.next({ type: 'done', payload: parsed.payload });
                                    } else if (parsed.eventName === 'error') {
                                        const friendly = toFriendlyError(parsed.payload);
                                        this.handleIncomingMessage({
                                            text: friendly,
                                            sender: 'bot',
                                            timestamp: new Date()
                                        });
                                        observer.next({ type: 'error', message: friendly, payload: parsed.payload });
                                    }
                                }
                            }
                            break;
                        }

                        buffer += decoder.decode(value, { stream: true });
                        let separatorIndex = buffer.indexOf('\n\n');

                        while (separatorIndex !== -1) {
                            const eventBlock = buffer.slice(0, separatorIndex);
                            buffer = buffer.slice(separatorIndex + 2);

                            const parsed = parseSseBlock(eventBlock);
                            if (parsed) {
                                if (parsed.eventName === 'chunk') {
                                    const chunkText = parsed.payload?.text || '';
                                    streamedText += chunkText;
                                    observer.next({ type: 'chunk', text: chunkText });
                                } else if (parsed.eventName === 'done') {
                                    const botData = parsed.payload?.data;
                                    const botText = botData?.message || streamedText;
                                    if (botText) {
                                        const botMessage: ChatMessage = {
                                            text: botText,
                                            sender: 'bot',
                                            timestamp: new Date(),
                                            ...(botData?.type === 'product' && botData?.data
                                                ? { productData: botData.data }
                                                : {})
                                        };
                                        this.handleIncomingMessage(botMessage);
                                    }
                                    observer.next({ type: 'done', payload: parsed.payload });
                                    observer.complete();
                                    controller.abort();
                                    return;
                                } else if (parsed.eventName === 'error') {
                                    const friendly = toFriendlyError(parsed.payload);
                                    this.handleIncomingMessage({
                                        text: friendly,
                                        sender: 'bot',
                                        timestamp: new Date()
                                    });
                                    observer.next({ type: 'error', message: friendly, payload: parsed.payload });
                                    observer.complete();
                                    controller.abort();
                                    return;
                                }
                            }

                            separatorIndex = buffer.indexOf('\n\n');
                        }
                    }

                    observer.complete();
                } catch (error: any) {
                    if (error?.name === 'AbortError') {
                        observer.complete();
                        return;
                    }

                    console.error('Backend SSE /chat failed:', error?.message || error);
                    const errorText = '❌ Chatbot service is not available. Please try again in a moment.';
                    this.handleIncomingMessage({
                        text: errorText,
                        sender: 'bot',
                        timestamp: new Date()
                    });
                    observer.next({ type: 'error', message: errorText });
                    observer.complete();
                }
            })();

            return () => controller.abort();
        });
    }

    // ─── Sound & Notifications ────────────────────────────────────────────────

    private playAlertSound(): void {
        if (this.alertSoundEnabled()) {
            if (this.activeAudio) {
                this.activeAudio.pause();
                this.activeAudio.currentTime = 0;
            }
            console.log('Attempting to play sound from: /assets/sounds/notification.mp3');
            this.activeAudio = new Audio('/assets/sounds/notification.mp3');
            this.activeAudio.play().then(() => {
                console.log('Sound played successfully 🔔');
                setTimeout(() => {
                    if (this.activeAudio) {
                        this.activeAudio.pause();
                        this.activeAudio.currentTime = 0;
                    }
                }, 15000);
            }).catch(e => {
                console.error('Audio play failed:', e.message);
            });
        }
    }

    private showDesktopNotification(message: ChatMessage): void {
        if (this.desktopNotificationEnabled() && document.hidden) {
            new Notification('New message from Support', {
                body: message.text,
                icon: '/assets/logo.png'
            });
        }
    }

    // ─── Misc ─────────────────────────────────────────────────────────────────

    initiateChat(userData?: any): Observable<any> {
        return of({ success: true, message: 'Chat initiated' });
    }

    /** @deprecated Use loadHistory() instead */
    getChatHistory(): Observable<ChatMessage[]> {
        return this.http.get<ChatMessage[]>(`${this.apiUrl}/history`).pipe(
            catchError(error => {
                console.error('Failed to load chat history:', error);
                return of([]);
            })
        );
    }

    getQuickReplies(): Observable<QuickReply[]> {
        const staticReplies: QuickReply[] = [
            { label: 'Tư vấn loại da', value: 'Tôi muốn tư vấn sản phẩm theo loại da.', icon: '🧴' },
            { label: 'Mã giảm giá', value: 'Có mã giảm giá nào đang áp dụng không?', icon: '🏷️' },
            { label: 'Kiểm tra đơn', value: 'Kiểm tra trạng thái đơn hàng gần nhất của tôi.', icon: '📦' }
        ];

        return of(staticReplies);
    }
}
