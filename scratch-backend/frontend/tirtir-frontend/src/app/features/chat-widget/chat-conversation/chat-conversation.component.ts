import { Component, EventEmitter, Output, OnInit, ViewChild, ElementRef, AfterViewChecked, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService, ChatMessage, QuickReply, ChatStreamEvent } from '../../../core/services/chat.service';
import { CartService } from '../../../core/services/cart.service';
import { ToastService } from '../../../core/services/toast.service';
import { LanguageService } from '../../../core/services/language.service';

@Component({
    selector: 'app-chat-conversation',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './chat-conversation.component.html',
    styleUrls: ['./chat-conversation.component.scss']
})
export class ChatConversationComponent implements OnInit, AfterViewChecked {
    @Output() back = new EventEmitter<void>();
    @ViewChild('scrollContainer') private scrollContainer!: ElementRef;

    messages: ChatMessage[] = [];
    newMessage = '';
    quickReplies: QuickReply[] = [];
    isTyping = false;
    currentBotMessage = '';

    private destroyRef = inject(DestroyRef);

    constructor(
        private chatService: ChatService,
        private cartService: CartService,
        private toastService: ToastService,
        public langService: LanguageService
    ) { }

    ngOnInit() {
        // FE-03: takeUntilDestroyed() prevents the memory leak when the component is destroyed
        this.chatService.messages$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(msgs => {
            this.messages = msgs;
            this.scrollToBottom();
        });

        this.chatService.getQuickReplies().subscribe(replies => {
            this.quickReplies = replies;
        });

        // Load history first; only show welcome message if no prior history exists
        this.chatService.loadHistory().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => {
                if (this.messages.length === 0) {
                    this.chatService.initWithWelcome(
                        'Thank you for reaching out to TIRTIR Global Customer Service Team❤️. How may we assist you today?✨'
                    );
                }
            },
            error: () => {
                if (this.messages.length === 0) {
                    this.chatService.initWithWelcome(
                        'Thank you for reaching out to TIRTIR Global Customer Service Team❤️. How may we assist you today?✨'
                    );
                }
            }
        });
    }

    ngAfterViewChecked() {
        this.scrollToBottom();
    }

    scrollToBottom(): void {
        try {
            this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
        } catch (err) { }
    }

    goBack() {
        this.back.emit();
    }

    sendMessage() {
        if (!this.newMessage.trim()) return;

        const msg = this.newMessage;
        this.newMessage = '';

        // FE-04: Set isTyping BEFORE subscribe so the indicator shows while waiting for the response
        this.isTyping = true;

        this.chatService.sendMessage(msg).subscribe({
            next: (event: ChatStreamEvent) => {
                if (event.type === 'chunk') {
                    this.currentBotMessage += event.text || '';
                    this.scrollToBottom();
                    return;
                }

                if (event.type === 'done') {
                    this.currentBotMessage = '';
                    this.isTyping = false;
                    this.scrollToBottom();
                    return;
                }

                if (event.type === 'error') {
                    this.currentBotMessage = '';
                    this.isTyping = false;
                    this.toastService.error(event.message || 'Chatbot service đang gặp sự cố.');
                    this.scrollToBottom();
                }
            },
            error: (err) => {
                console.error('Failed to send message:', err);
                this.currentBotMessage = '';
                this.isTyping = false;
            }
        });
    }

    sendQuickReply(reply: QuickReply) {
        this.newMessage = reply.value;
        this.sendMessage();
    }

    addToCart(productData: any) {
        if (!productData || !productData.id) return;

        this.cartService.addToCart({
            productId: productData.id,
            quantity: 1
        }).subscribe({
            next: () => {
                this.toastService.success(`Đã thêm ${productData.name} vào giỏ hàng!`);
            },
            error: (err) => {
                this.toastService.error(err.message || 'Không thể thêm vào giỏ hàng');
            }
        });
    }
}
