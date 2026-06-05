import { Component, EventEmitter, OnInit, Output, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatService, ChatMessage } from '../../../core/services/chat.service';
import { LanguageService } from '../../../core/services/language.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
    selector: 'app-chat-messages',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './chat-messages.component.html',
    styleUrls: ['./chat-messages.component.scss']
})
export class ChatMessagesComponent implements OnInit {
    @Output() startChat = new EventEmitter<void>();

    messages: ChatMessage[] = [];
    isLoading = true;

    private destroyRef = inject(DestroyRef);

    constructor(
        private chatService: ChatService,
        public langService: LanguageService
    ) { }

    get hasHistory(): boolean {
        return this.messages.length > 0;
    }

    /** Last message for timestamp display */
    get lastMessage(): ChatMessage | null {
        return this.messages[this.messages.length - 1] ?? null;
    }

    /** Last bot message text used as the conversation preview */
    get previewText(): string {
        const lastBot = [...this.messages].reverse().find(m => m.sender === 'bot');
        return lastBot?.text ?? this.lastMessage?.text ?? '';
    }

    ngOnInit() {
        this.chatService.messages$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(msgs => {
            this.messages = msgs;
        });

        this.chatService.loadHistory().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => { this.isLoading = false; },
            error: () => { this.isLoading = false; }
        });
    }

    openConversation() {
        this.startChat.emit();
    }

    onStartNewChat() {
        this.startChat.emit();
    }
}
