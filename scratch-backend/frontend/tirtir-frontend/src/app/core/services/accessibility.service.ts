import { Injectable, signal, computed } from '@angular/core';

@Injectable({
    providedIn: 'root'
})
export class AccessibilityService {
    private highContrastSignal = signal<boolean>(false);
    public isHighContrast = computed(() => this.highContrastSignal());

    constructor() {
        this.initMode();
    }

    private initMode() {
        const saved = localStorage.getItem('tirtir-high-contrast');
        if (saved === 'true') {
            this.highContrastSignal.set(true);
            document.body.classList.add('high-contrast-mode');
        }
    }

    toggleHighContrast() {
        const newState = !this.highContrastSignal();
        this.highContrastSignal.set(newState);
        localStorage.setItem('tirtir-high-contrast', newState.toString());

        if (newState) {
            document.body.classList.add('high-contrast-mode');
        } else {
            document.body.classList.remove('high-contrast-mode');
        }
    }
}
