import { Component, Output, EventEmitter, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { ChatService } from '../../../core/services/chat.service';
import { LanguageService, Language } from '../../../core/services/language.service';
import { Router } from '@angular/router';

@Component({
    selector: 'app-chat-settings',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './chat-settings.component.html',
    styleUrls: ['./chat-settings.component.scss']
})
export class ChatSettingsComponent implements OnInit {
    authService = inject(AuthService);
    chatService = inject(ChatService);
    langService = inject(LanguageService);
    router = inject(Router);

    @Output() closeChat = new EventEmitter<void>();

    ngOnInit() {
        // Initialization handled in ChatService constructor
    }

    setLanguage(lang: Language) {
        this.langService.setLanguage(lang);
    }

    toggleAlertSound(event: any) {
        this.chatService.toggleAlertSound(event.target.checked);
    }

    async toggleDesktopNotifications(event: any) {
        if (event.target.checked) {
            await this.chatService.requestDesktopNotification();
            if (!this.chatService.desktopNotificationEnabled()) {
                event.target.checked = false;
            }
        } else {
            alert('To completely disable notifications, please update your browser site settings.');
            event.target.checked = true;
        }
    }

    getAvatarUrl(avatarPath: string | undefined): string {
        if (!avatarPath) return '';
        if (avatarPath.startsWith('http')) return avatarPath;
        const cleanPath = avatarPath.startsWith('/') ? avatarPath.substring(1) : avatarPath;
        return `http://localhost:5001/${cleanPath}`;
    }

    handleAction() {
        this.closeChat.emit();
        if (this.authService.isAuthenticated()) {
            this.router.navigate(['/account/profile']);
        } else {
            this.router.navigate(['/login']);
        }
    }
}
