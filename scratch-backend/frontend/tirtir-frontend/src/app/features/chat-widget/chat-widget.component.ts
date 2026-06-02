import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatHomeComponent } from './chat-home/chat-home.component';
import { ChatMessagesComponent } from './chat-messages/chat-messages.component';
import { ChatSettingsComponent } from './chat-settings/chat-settings.component';
import { ChatConversationComponent } from './chat-conversation/chat-conversation.component';
import { animate, state, style, transition, trigger } from '@angular/animations';

type Tab = 'home' | 'messages' | 'settings';

@Component({
    selector: 'app-chat-widget',
    standalone: true,
    imports: [
        CommonModule,
        ChatHomeComponent,
        ChatMessagesComponent,
        ChatSettingsComponent,
        ChatConversationComponent
    ],
    templateUrl: './chat-widget.component.html',
    styleUrls: ['./chat-widget.component.scss'],
    animations: [
        trigger('toggleChat', [
            state('closed', style({
                opacity: 0,
                transform: 'translateY(20px) scale(0.9)',
                display: 'none'
            })),
            state('open', style({
                opacity: 1,
                transform: 'translateY(0) scale(1)',
                display: 'block'
            })),
            transition('closed => open', [
                style({ display: 'block' }),
                animate('300ms cubic-bezier(0.16, 1, 0.3, 1)')
            ]),
            transition('open => closed', [
                animate('200ms ease-in', style({ opacity: 0, transform: 'translateY(20px) scale(0.9)' })),
                style({ display: 'none' })
            ])
        ]),
        trigger('rotateIcon', [
            state('default', style({ transform: 'rotate(0)' })),
            state('rotated', style({ transform: 'rotate(90deg)' })),
            transition('default <=> rotated', animate('300ms ease-out'))
        ])
    ]
})
export class ChatWidgetComponent {
    isOpen = false;
    activeTab: Tab = 'home';
    showConversation = false;

    toggleChat() {
        this.isOpen = !this.isOpen;
    }

    setActiveTab(tab: Tab) {
        this.activeTab = tab;
        this.showConversation = false; // Reset to tabs view if navigating bottom bar
    }

    startChat() {
        this.showConversation = true;
    }

    backToHome() {
        this.showConversation = false;
        this.activeTab = 'home';
    }
}
