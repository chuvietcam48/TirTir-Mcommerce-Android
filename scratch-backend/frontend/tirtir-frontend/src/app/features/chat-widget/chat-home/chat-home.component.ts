import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LanguageService } from '../../../core/services/language.service';

@Component({
    selector: 'app-chat-home',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './chat-home.component.html',
    styleUrls: ['./chat-home.component.scss']
})
export class ChatHomeComponent {
    @Output() startChat = new EventEmitter<void>();

    constructor(public langService: LanguageService) {}

    onStartChat() {
        this.startChat.emit();
    }
}
