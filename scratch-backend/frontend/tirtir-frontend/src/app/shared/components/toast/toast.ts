import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../../core/services/toast.service';

@Component({
    selector: 'app-toast',
    standalone: true,
    imports: [CommonModule],
    template: `
        <div class="toast-container">
            <div *ngFor="let toast of toastService.toasts()" 
                 class="toast-item" 
                 [class]="toast.type"
                 (click)="toastService.remove(toast.id)">
                <div class="toast-content">
                    <span class="toast-icon" [ngSwitch]="toast.type">
                        <span *ngSwitchCase="'success'">✓</span>
                        <span *ngSwitchCase="'error'">✕</span>
                        <span *ngSwitchCase="'warning'">⚠</span>
                        <span *ngSwitchDefault>ℹ</span>
                    </span>
                    <span class="toast-message">{{ toast.message }}</span>
                </div>
            </div>
        </div>
    `,
    styles: [`
        .toast-container {
            position: fixed;
            top: 2rem;
            right: 2rem;
            z-index: 10000;
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
            pointer-events: none;
        }

        .toast-item {
            min-width: 280px;
            max-width: 400px;
            padding: 1rem 1.25rem;
            background: #fff;
            color: #000;
            border: 1px solid #e0e0e0;
            box-shadow: 0 4px 20px rgba(0,0,0,0.08);
            pointer-events: auto;
            cursor: pointer;
            animation: slideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
            transition: all 0.2s ease;
        }

        .toast-item:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 24px rgba(0,0,0,0.12);
        }

        .toast-content {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        .toast-icon {
            font-size: 1.1rem;
            font-weight: bold;
        }

        .success { border-left: 4px solid #000; }
        .error { border-left: 4px solid #ff4d4d; color: #ff4d4d; }
        .warning { border-left: 4px solid #ffcc00; }
        .info { border-left: 4px solid #000; }

        .toast-message {
            font-size: 0.85rem;
            font-weight: 500;
            letter-spacing: 0.02em;
            text-transform: uppercase;
        }

        @keyframes slideIn {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }
    `]
})
export class ToastComponent {
    public toastService = inject(ToastService);
}
