import { Injectable, signal, computed } from '@angular/core';

export type Language = 'en' | 'vi';

const STORAGE_KEY = 'tirtir_lang';

const translations: Record<Language, Record<string, string>> = {
    en: {
        // Chat Home
        'home.greeting': 'Thank you for reaching out to TIRTIR Global Customer Service Team❤️',
        'home.question': 'How may we assist you today?✨',
        'home.startChat': 'Start a chat',
        'home.poweredBy': 'Powered by Channel Talk',

        // Chat Messages
        'messages.header': 'Messages',
        'messages.newChat': 'Start a new chat',

        // Chat Settings
        'settings.header': 'Settings',
        'settings.contactInfo': 'Contact information',
        'settings.editInfo': 'Edit information',
        'settings.signIn': 'Sign in',
        'settings.welcomeGuest': 'Welcome to Chatbot',
        'settings.csLanguage': 'CS Language',
        'settings.language': 'Language',
        'settings.showTranslation': 'Show message translation',
        'settings.desktopNotifications': 'Desktop notifications',
        'settings.alertSound': 'Alert sound',

        // Conversation
        'conversation.inputPlaceholder': 'Type a message...',
        'conversation.back': 'Back',
    },
    vi: {
        // Chat Home
        'home.greeting': 'Cảm ơn bạn đã liên hệ với Đội ngũ CSKH TIRTIR Global❤️',
        'home.question': 'Chúng tôi có thể giúp gì cho bạn hôm nay?✨',
        'home.startChat': 'Bắt đầu trò chuyện',
        'home.poweredBy': 'Được hỗ trợ bởi Channel Talk',

        // Chat Messages
        'messages.header': 'Tin nhắn',
        'messages.newChat': 'Bắt đầu cuộc trò chuyện mới',

        // Chat Settings
        'settings.header': 'Cài đặt',
        'settings.contactInfo': 'Thông tin liên hệ',
        'settings.editInfo': 'Chỉnh sửa thông tin',
        'settings.signIn': 'Đăng nhập',
        'settings.welcomeGuest': 'Chào mừng đến với Chatbot',
        'settings.csLanguage': 'Ngôn ngữ CSKH',
        'settings.language': 'Ngôn ngữ',
        'settings.showTranslation': 'Hiển thị bản dịch tin nhắn',
        'settings.desktopNotifications': 'Thông báo trên màn hình',
        'settings.alertSound': 'Âm thanh cảnh báo',

        // Conversation
        'conversation.inputPlaceholder': 'Nhập tin nhắn...',
        'conversation.back': 'Quay lại',
    }
};

@Injectable({ providedIn: 'root' })
export class LanguageService {
    private _lang = signal<Language>(
        (localStorage.getItem(STORAGE_KEY) as Language) || 'en'
    );

    currentLang = this._lang.asReadonly();

    setLanguage(lang: Language): void {
        this._lang.set(lang);
        localStorage.setItem(STORAGE_KEY, lang);
    }

    t(key: string): string {
        return translations[this._lang()][key] ?? key;
    }
}
