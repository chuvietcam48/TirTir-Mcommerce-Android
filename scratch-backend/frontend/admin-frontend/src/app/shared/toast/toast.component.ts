import {
    Component, OnInit, OnDestroy,
    ChangeDetectionStrategy, ChangeDetectorRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    trigger, state, style, animate, transition
} from '@angular/animations';
import { Subscription } from 'rxjs';
import { NotificationService, AdminNotification } from '../../core/services/notification.service';

@Component({
    selector: 'app-toast',
    standalone: true,
    imports: [CommonModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
    animations: [
        trigger('slideIn', [
            transition(':enter', [
                style({ transform: 'translateX(110%)', opacity: 0 }),
                animate('240ms ease-out', style({ transform: 'translateX(0)', opacity: 1 }))
            ]),
            transition(':leave', [
                animate('200ms ease-in', style({ transform: 'translateX(110%)', opacity: 0 }))
            ])
        ])
    ],
    template: `
    <div class="toast-container">
      @for (toast of toasts; track toast.id) {
        <div class="toast" [@slideIn] [class]="'toast--' + toast.type">
          <div class="toast-border"></div>
          <div class="toast-body">
            <span class="toast-icon">{{ iconFor(toast.type) }}</span>
            <div class="toast-text">
              <strong>{{ toast.title }}</strong>
              <span>{{ toast.message }}</span>
            </div>
            <button class="toast-close" (click)="dismiss(toast.id)">✕</button>
          </div>
        </div>
      }
    </div>
  `,
    styles: [`
    .toast-container {
      position: fixed;
      bottom: 24px;
      right: 24px;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 10px;
      max-width: 340px;
    }
    .toast {
      background: #1e2330;
      border-radius: 8px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.4);
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }
    .toast-border {
      height: 3px;
      width: 100%;
    }
    .toast--new_order    .toast-border { background: #00bfa5; }
    .toast--low_stock    .toast-border { background: #ffb300; }
    .toast--cart_recovered .toast-border { background: #43a047; }
    .toast-body {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 12px 14px;
    }
    .toast-icon { font-size: 18px; flex-shrink: 0; margin-top: 1px; }
    .toast-text { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .toast-text strong { color: #e2e8f0; font-size: 13px; }
    .toast-text span { color: #94a3b8; font-size: 12px; }
    .toast-close {
      background: none; border: none; color: #64748b;
      cursor: pointer; font-size: 14px; padding: 0; flex-shrink: 0;
    }
    .toast-close:hover { color: #e2e8f0; }
  `]
})
export class ToastComponent implements OnInit, OnDestroy {
    toasts: AdminNotification[] = [];
    private sub!: Subscription;

    constructor(
        private notifService: NotificationService,
        private cdr: ChangeDetectorRef
    ) { }

    ngOnInit(): void {
        this.sub = this.notifService.toasts$.subscribe(toasts => {
            this.toasts = toasts;
            this.cdr.markForCheck();
        });
    }

    iconFor(type: string): string {
        const icons: Record<string, string> = {
            new_order: '🛒',
            low_stock: '⚠️',
            cart_recovered: '✅'
        };
        return icons[type] ?? '🔔';
    }

    dismiss(id: string): void {
        this.notifService.dismissToast(id);
    }

    ngOnDestroy(): void {
        this.sub?.unsubscribe();
    }
}
