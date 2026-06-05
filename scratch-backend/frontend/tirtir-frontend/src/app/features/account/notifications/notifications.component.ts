import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { NotificationService } from '../../../core/services/notification.service';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { INotification } from '../../../core/models/notification.model';

interface NotificationGroup {
  label: string;
  items: INotification[];
}

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, RouterModule, TimeAgoPipe],
  template: `
    <div class="notif-page">

      <!-- Header -->
      <div class="page-header">
        <div class="header-left">
          <div class="eyebrow">My Account</div>
          <div class="page-title">Notifications</div>
        </div>
        <div class="header-right" *ngIf="!loading">
          <div class="unread-count">
            <span class="unread-num">{{ unreadCount }}</span> Unread
          </div>
          <button class="mark-all-btn" *ngIf="unreadCount > 0" (click)="markAllRead()">
            Mark all as read
          </button>
        </div>
      </div>

      <!-- Filter Tabs -->
      <div class="filter-tabs">
        <button class="tab" [class.active]="activeFilter === 'all'" (click)="setFilter('all')">
          All <span class="tab-count">{{ counts.all }}</span>
        </button>
        <button class="tab" [class.active]="activeFilter === 'order'" (click)="setFilter('order')">
          Orders <span class="tab-count">{{ counts.order }}</span>
        </button>
        <button class="tab" [class.active]="activeFilter === 'promotion'" (click)="setFilter('promotion')">
          Promotions <span class="tab-count">{{ counts.promotion }}</span>
        </button>
        <button class="tab" [class.active]="activeFilter === 'system'" (click)="setFilter('system')">
          Reminders <span class="tab-count">{{ counts.system }}</span>
        </button>
      </div>

      <!-- Loading -->
      <div class="loading-state" *ngIf="loading">
        <div class="spinner"></div>
      </div>

      <!-- Empty -->
      <div class="empty-state" *ngIf="!loading && filteredNotifications.length === 0">
        <svg viewBox="0 0 24 24" fill="none"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
        <p>No notifications yet</p>
      </div>

      <!-- Grouped Notification List -->
      <div class="notif-list" *ngIf="!loading && pagedGroups.length > 0">
        <div class="notif-group" *ngFor="let group of pagedGroups">
          <div class="group-label">{{ group.label }}</div>

          <div class="notif-row" *ngFor="let item of group.items"
               [class.unread]="!item.isRead"
               (click)="onNotificationClick(item)">

            <!-- Unread dot -->
            <div class="unread-dot" *ngIf="!item.isRead"></div>

            <!-- Icon -->
            <div class="notif-icon" [ngClass]="getNotifIcon(item)">

              <!-- order_placed: cart + checkmark -->
              <svg *ngIf="getNotifIcon(item) === 'order_placed'" viewBox="0 0 18 18" fill="none">
                <path d="M2 2h1.5l2 8h7l1.5-5.5H4.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="7" cy="13.5" r="1.2" fill="currentColor"/>
                <circle cx="12" cy="13.5" r="1.2" fill="currentColor"/>
                <path d="M11 5l1.5 1.5L15 3.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>

              <!-- pending: clock -->
              <svg *ngIf="getNotifIcon(item) === 'pending'" viewBox="0 0 18 18" fill="none">
                <circle cx="9" cy="9" r="7" stroke="currentColor" stroke-width="1.2"/>
                <path d="M9 5.5V9l2.5 2.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>

              <!-- processing: sync circle arrows -->
              <svg *ngIf="getNotifIcon(item) === 'processing'" viewBox="0 0 18 18" fill="none">
                <path d="M15 9A6 6 0 1 1 9 3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <path d="M9 3l2-2M9 3l-2-2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>

              <!-- shipped: truck + dash line -->
              <svg *ngIf="getNotifIcon(item) === 'shipped'" viewBox="0 0 18 18" fill="none">
                <path d="M1 5h10v7H1z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                <path d="M11 7.5l4 2V12h-4V7.5z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                <circle cx="4" cy="13.5" r="1.2" stroke="currentColor" stroke-width="1.1"/>
                <circle cx="13.5" cy="13.5" r="1.2" stroke="currentColor" stroke-width="1.1"/>
                <path d="M1 3h6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>

              <!-- delivered: truck + checkmark -->
              <svg *ngIf="getNotifIcon(item) === 'delivered'" viewBox="0 0 18 18" fill="none">
                <path d="M1 5h10v7H1z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                <path d="M11 7.5l4 2V12h-4V7.5z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                <circle cx="4" cy="13.5" r="1.2" stroke="currentColor" stroke-width="1.1"/>
                <circle cx="13.5" cy="13.5" r="1.2" stroke="currentColor" stroke-width="1.1"/>
                <path d="M3 8.5l1.5 1.5L7 7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>

              <!-- cancelled: cart + X -->
              <svg *ngIf="getNotifIcon(item) === 'cancelled'" viewBox="0 0 18 18" fill="none">
                <path d="M2 2h1.5l2 8h7l1.5-5.5H4.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="7" cy="13.5" r="1.2" fill="currentColor"/>
                <circle cx="12" cy="13.5" r="1.2" fill="currentColor"/>
                <path d="M11 3l2 2M13 3l-2 2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>

              <!-- review: star -->
              <svg *ngIf="getNotifIcon(item) === 'review'" viewBox="0 0 18 18" fill="none">
                <path d="M9 2l2.09 4.26L16 7.27l-3.5 3.41.83 4.83L9 13.27l-4.33 2.27.83-4.83L2 7.27l4.91-.71L9 2z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
              </svg>

              <!-- refund: arrow back + line -->
              <svg *ngIf="getNotifIcon(item) === 'refund'" viewBox="0 0 18 18" fill="none">
                <path d="M3 9h12" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <path d="M7 5L3 9l4 4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M15 3v3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>

              <!-- promotion: star (filled feel) -->
              <svg *ngIf="getNotifIcon(item) === 'promotion'" viewBox="0 0 18 18" fill="none">
                <path d="M9 2l2.09 4.26L16 7.27l-3.5 3.41.83 4.83L9 13.27l-4.33 2.27.83-4.83L2 7.27l4.91-.71L9 2z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
              </svg>

              <!-- system/cart reminder -->
              <svg *ngIf="getNotifIcon(item) === 'system'" viewBox="0 0 18 18" fill="none">
                <path d="M2 2h1.5l2 8h7l1.5-5.5H4.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="7" cy="13.5" r="1.2" fill="currentColor"/>
                <circle cx="12" cy="13.5" r="1.2" fill="currentColor"/>
              </svg>

            </div>

            <!-- Content -->
            <div class="notif-content">
              <div class="notif-top">
                <span class="notif-title" [class.unread-title]="!item.isRead">{{ item.title }}</span>
                <span class="notif-time">{{ item.createdAt | timeAgo }}</span>
              </div>
              <p class="notif-msg">{{ item.message }}</p>

              <!-- Order tag -->
              <div class="order-tag" *ngIf="item.type === 'order' && getOrderId(item)">
    
                #{{ getOrderId(item) }}
              </div>

              <!-- CTA Row -->
              <div class="cta-row" (click)="$event.stopPropagation()" *ngIf="item.link">
                <!-- Order CTA -->
                <a *ngIf="item.type === 'order' && item.link" [routerLink]="item.link" class="cta-link">
                  View order
                  <svg viewBox="0 0 10 10" fill="none"><path d="M3 1l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>
                </a>
                <!-- Promotion CTA -->
                <a *ngIf="item.type === 'promotion' && item.link" [routerLink]="item.link" class="cta-btn">
                  Shop now
                </a>
                <!-- Cart/Reminder CTA -->
                <a *ngIf="item.type === 'system' && item.link" [routerLink]="item.link" class="cta-btn cta-btn--primary">
                  Checkout now
                </a>
              </div>
            </div>

          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div class="pagination" *ngIf="!loading && totalPages > 1">
        <span class="pg-info">Page {{ currentPage }}/{{ totalPages }} · {{ filteredNotifications.length }} notifications</span>
        <div class="pg-controls">
          <button class="pg-btn" (click)="goToPage(currentPage - 1)" [disabled]="currentPage === 1">
            <svg viewBox="0 0 10 10" fill="none"><path d="M7 1L3 5L7 9" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
          <button class="pg-btn pg-num" *ngFor="let p of pageNumbers"
                  [class.active]="p === currentPage"
                  (click)="goToPage(p)">{{ p }}</button>
          <button class="pg-btn" (click)="goToPage(currentPage + 1)" [disabled]="currentPage === totalPages">
            <svg viewBox="0 0 10 10" fill="none"><path d="M3 1l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
        </div>
      </div>

    </div>
  `,
  styles: [`
    .notif-page { padding: 0; max-width: 100%; }

    /* ── Header ── */
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1.5rem;
    }
    .eyebrow {
      font-size: 9px;
      letter-spacing: 0.2em;
      text-transform: uppercase;
      color: var(--color-accent, #D32F2F);
      margin-bottom: 6px;
    }
    .page-title {
      font-family: var(--font-display, 'Playfair Display', Georgia, serif);
      font-size: 32px;
      font-weight: 500;
      line-height: 1.1;
      color: var(--color-text, #111);
    }
    .header-right { text-align: right; }
    .unread-count {
      font-size: 11px;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--color-text-muted, #888);
      margin-bottom: 4px;
    }
    .unread-num {
      font-weight: 600;
      color: var(--color-text, #111);
    }
    .mark-all-btn {
      background: none;
      border: none;
      padding: 0;
      font-size: 11px;
      color: var(--color-text-muted, #888);
      cursor: pointer;
      text-decoration: underline;
      text-underline-offset: 2px;
      transition: color 0.15s;
    }
    .mark-all-btn:hover { color: var(--color-text, #111); }

    /* ── Filter Tabs ── */
    .filter-tabs {
      display: flex;
      gap: 0;
      border-bottom: 1px solid #e8e6e1;
      margin-bottom: 0;
    }
    .tab {
      font-size: 10px;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      font-weight: 500;
      padding: 8px 16px 9px;
      border: none;
      border-bottom: 2px solid transparent;
      background: transparent;
      color: var(--color-text-muted, #888);
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 5px;
      margin-bottom: -1px;
      transition: all 0.15s;
    }
    .tab:hover { color: var(--color-text, #111); }
    .tab.active {
      color: var(--color-text, #111);
      border-bottom-color: var(--color-text, #111);
    }
    .tab-count {
      font-size: 9px;
      color: var(--color-text-muted, #aaa);
    }
    .tab.active .tab-count { color: var(--color-text-muted, #888); }

    /* ── Group Label ── */
    .group-label {
      font-size: 9px;
      letter-spacing: 0.18em;
      text-transform: uppercase;
      color: var(--color-text-muted, #aaa);
      padding: 20px 0 8px;
    }

    /* ── Notification Row ── */
    .notif-row {
      display: flex;
      align-items: flex-start;
      gap: 14px;
      padding: 16px 0;
      border-bottom: 1px solid #e8e6e1;
      cursor: pointer;
      position: relative;
      transition: background 0.15s;
    }
    .notif-row:hover { background: #fafaf8; }
    .notif-row.unread { background: #fafaf8; }

    /* Unread left accent dot */
    .unread-dot {
      position: absolute;
      left: -16px;
      top: 50%;
      transform: translateY(-50%);
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: var(--color-accent, #D32F2F);
    }

    /* Icon */
    .notif-icon {
      width: 36px;
      height: 36px;
      background: transparent;
      border: 1px solid #e8e6e1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    justify-content: center;
    }
    .notif-icon svg { width: 16px; height: 16px; }
    .notif-icon.order_placed  { color: #059669; }
    .notif-icon.pending       { color: #b45309; }
    .notif-icon.processing    { color: #1d4ed8; }
    .notif-icon.shipped       { color: #1d4ed8; }
    .notif-icon.delivered     { color: #059669; }
    .notif-icon.cancelled     { color: #dc2626; }
    .notif-icon.review        { color: #b45309; }
    .notif-icon.refund        { color: #7c3aed; }
    .notif-icon.promotion     { color: #b45309; }
    .notif-icon.system        { border-color: #e8e6e1; color: var(--color-text-muted, #888); }

    /* Content */
    .notif-content { flex: 1; min-width: 0; }
    .notif-top {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: 12px;
      margin-bottom: 4px;
    }
    .notif-title {
      font-size: 12px;
      font-weight: 500;
      color: var(--color-text-muted, #888);
    }
    .notif-title.unread-title { color: var(--color-text, #111); }
    .notif-time {
      font-size: 10px;
      color: var(--color-text-muted, #aaa);
      white-space: nowrap;
      flex-shrink: 0;
    }
    .notif-msg {
      font-size: 13px;
      color: var(--color-text, #111);
      line-height: 1.5;
      margin: 0 0 8px;
    }
    .notif-row:not(.unread) .notif-msg { color: var(--color-text-muted, #888); }

    /* Order tag */
    .order-tag {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.06em;
      color: var(--color-text-muted, #000000);
      margin-bottom: 8px;
    }
    .order-tag svg { width: 10px; height: 10px; }

    /* CTA */
    .cta-row { display: flex; align-items: center; gap: 8px; justify-content: flex-end; }
    .cta-link {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 10px;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      font-weight: 500;
      color: var(--color-text, #111);
      text-decoration: none;
      transition: gap 0.15s;
    }
    .cta-link svg { width: 8px; height: 8px; }
    .cta-link:hover { gap: 7px; }
    .cta-btn {
      display: inline-block;
      font-size: 9px;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      font-weight: 500;
      padding: 6px 14px;
      border: 1px solid var(--color-text, #111);
      color: var(--color-text, #111);
      text-decoration: none;
      transition: all 0.15s;
    }
    .cta-btn:hover { background: var(--color-text, #111); color: #fff; }
    .cta-btn--primary {
      background: var(--color-text, #111);
      color: #fff;
    }
    .cta-btn--primary:hover { background: #333; }

    /* ── Pagination ── */
    .pagination {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px 0 4px;
      border-top: 1px solid #e8e6e1;
      margin-top: 4px;
    }
    .pg-info {
      font-size: 11px;
      color: var(--color-text-muted, #aaa);
    }
    .pg-controls { display: flex; align-items: center; gap: 2px; }
    .pg-btn {
      min-width: 30px;
      height: 30px;
      padding: 0 6px;
      border: 1px solid transparent;
      background: transparent;
      color: var(--color-text-muted, #888);
      font-size: 11px;
      font-weight: 500;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s;
    }
    .pg-btn svg { width: 10px; height: 10px; }
    .pg-btn:hover:not(:disabled):not(.active) { color: var(--color-text, #111); border-color: #e8e6e1; }
    .pg-btn.active { background: var(--color-text, #111); color: #fff; border-color: var(--color-text, #111); }
    .pg-btn:disabled { opacity: 0.3; cursor: default; }

    /* ── Loading / Empty ── */
    .loading-state { padding: 60px 0; display: flex; justify-content: center; }
    .spinner {
      width: 28px; height: 28px;
      border: 2px solid #e8e6e1;
      border-top-color: var(--color-text, #111);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .empty-state { padding: 60px 0; text-align: center; color: var(--color-text-muted, #ccc); }
    .empty-state svg { width: 36px; height: 36px; margin-bottom: 12px; }
    .empty-state p { font-size: 12px; letter-spacing: 0.08em; text-transform: uppercase; margin: 0; }
  `]
})
export class NotificationsComponent implements OnInit, OnDestroy {
  private notificationService = inject(NotificationService);
  private router = inject(Router);
  private sub?: Subscription;

  notifications: INotification[] = [];
  loading = true;
  activeFilter = 'all';
  currentPage = 1;
  readonly pageSize = 8;

  // ── Derived state ──────────────────────────────────────────

  get unreadCount(): number {
    return this.notifications.filter(n => !n.isRead).length;
  }

  get counts() {
    return {
      all: this.notifications.length,
      order: this.notifications.filter(n => n.type === 'order').length,
      promotion: this.notifications.filter(n => n.type === 'promotion').length,
      system: this.notifications.filter(n => n.type === 'system').length,
    };
  }

  get filteredNotifications(): INotification[] {
    if (this.activeFilter === 'all') return this.notifications;
    return this.notifications.filter(n => n.type === this.activeFilter);
  }

  get totalPages(): number {
    return Math.ceil(this.filteredNotifications.length / this.pageSize);
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  get pagedGroups(): NotificationGroup[] {
    const start = (this.currentPage - 1) * this.pageSize;
    const paged = this.filteredNotifications.slice(start, start + this.pageSize);
    return this.groupByDate(paged);
  }

  // ── Lifecycle ──────────────────────────────────────────────

  ngOnInit() {
    this.notificationService.fetchNotifications().subscribe();
    this.sub = this.notificationService.notifications$.subscribe(data => {
      this.notifications = data;
      this.loading = false;
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  // ── Actions ────────────────────────────────────────────────

  setFilter(filter: string) {
    this.activeFilter = filter;
    this.currentPage = 1;
  }

  goToPage(page: number) {
    if (page >= 1 && page <= this.totalPages) this.currentPage = page;
  }

  onNotificationClick(item: INotification) {
    if (!item.isRead) {
      this.notificationService.markAsRead(item._id).subscribe();
    }
    if (item.link) {
      this.router.navigateByUrl(item.link);
    }
  }

  markAllRead() {
    this.notificationService.markAllAsRead().subscribe();
  }

  // ── Helpers ────────────────────────────────────────────────

  getOrderId(item: INotification): string | null {
    // Try to extract short order ID from link e.g. /account/orders/69bcfb8c7452379b8c1ba4f2
    const match = item.link?.match(/orders\/([a-f0-9]{24})$/i);
    if (match) return match[1].slice(-6).toUpperCase();
    return null;
  }

  /** Returns a CSS class / icon key based on notification content */
  getNotifIcon(item: INotification): string {
    if (item.type === 'promotion') return 'promotion';
    if (item.type === 'system') return 'system';

    const lc = (item.title + ' ' + item.message).toLowerCase();

    if (lc.includes('refund') || lc.includes('hoàn tiền')) return 'refund';
    if (lc.includes('review') || lc.includes('rate') || lc.includes('đánh giá')) return 'review';
    if (lc.includes('cancelled') || lc.includes('canceled') || lc.includes('hủy')) return 'cancelled';
    if (lc.includes('delivered') || lc.includes('thành công') || lc.includes('giao thành')) return 'delivered';
    if (lc.includes('shipped') || lc.includes('on its way') || lc.includes('carrier') || lc.includes('giao hàng') && lc.includes('đang')) return 'shipped';
    if (lc.includes('processing') || lc.includes('confirmed') || lc.includes('xử lý')) return 'processing';
    if (lc.includes('placed') || lc.includes('awaiting') || lc.includes('đặt hàng')) return 'order_placed';
    if (lc.includes('pending') || lc.includes('waiting') || lc.includes('chờ')) return 'pending';

    return 'order_placed'; // fallback for generic order notifications
  }

  isDelivered(item: INotification): boolean {
    return this.getNotifIcon(item) === 'delivered';
  }

  isProcessing(item: INotification): boolean {
    const icon = this.getNotifIcon(item);
    return icon === 'processing' || icon === 'pending';
  }

  private groupByDate(items: INotification[]): NotificationGroup[] {
    const groups = new Map<string, INotification[]>();
    const now = new Date();

    for (const item of items) {
      const date = new Date(item.createdAt);
      const diffMs = now.getTime() - date.getTime();
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      const diffMonths = Math.floor(diffDays / 30);

      let label: string;
      if (diffDays === 0) label = 'Today';
      else if (diffDays === 1) label = 'Yesterday';
      else if (diffDays < 30) label = `${diffDays} days ago`;
      else if (diffMonths === 1) label = '1 month ago';
      else label = `${diffMonths} months ago`;

      if (!groups.has(label)) groups.set(label, []);
      groups.get(label)!.push(item);
    }

    return Array.from(groups.entries()).map(([label, items]) => ({ label, items }));
  }
}
