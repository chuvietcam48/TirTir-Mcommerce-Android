import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { OrderService } from '../../../core/services/order.service';
import { UserService } from '../../../core/services/user.service';
import { CartService } from '../../../core/services/cart.service';
import { ToastService } from '../../../core/services/toast.service';
import { catchError } from 'rxjs/operators';
import { forkJoin, of } from 'rxjs';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <!-- Loading State -->
    <div class="loading-state" *ngIf="loading">
      <div class="spinner"></div>
      <p>Loading order details...</p>
    </div>

    <!-- Error State -->
    <div class="error-state" *ngIf="error && !loading">
      <div class="error-icon">⚠</div>
      <h3>Order Not Found</h3>
      <p>{{ error }}</p>
      <button class="btn-outline" routerLink="/shop">Continue Shopping</button>
    </div>

    <!-- Order Content -->
    <div *ngIf="order && !loading && !error">

      <!-- Breadcrumb -->
      <div class="breadcrumb" routerLink="/account/orders">
        <svg viewBox="0 0 10 10" fill="none"><path d="M7 1L3 5L7 9" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
        Order History
      </div>

      <!-- Header -->
      <div class="main-header">
        <div class="main-eyebrow">TirTir · My Orders</div>
        <div class="main-title">Order Details</div>
        <div class="main-sub">Placed on {{ order.createdAt | date:'dd/MM/yyyy' }} · {{ order.createdAt | date:'hh:mm a' }}</div>
      </div>

      <!-- Status Row -->
      <div class="status-row">
        <div class="sc">
          <div class="sc-l">Order ID</div>
          <div class="sc-v">#{{ order._id?.slice(-6) | uppercase }}</div>
        </div>
        <div class="sc">
          <div class="sc-l">Date Placed</div>
          <div class="sc-v">{{ order.createdAt | date:'dd/MM/yyyy' }}</div>
        </div>
        <div class="sc">
          <div class="sc-l">Total</div>
          <div class="sc-v accent">{{ order.totalAmount | currency:'USD':'symbol':'1.2-2' }}</div>
        </div>
        <div class="sc">
          <div class="sc-l">Status</div>
          <div class="sc-v">
            <span class="status-badge" [ngClass]="'badge-' + order.status?.toLowerCase().replace(' ', '-')">{{ getStatusLabel(order.status) }}</span>
          </div>
        </div>
      </div>

      <!-- Delivery Block -->
      <div class="delivery-block" *ngIf="order.status !== 'Order Placed' && order.status !== 'Pending' && order.status !== 'Cancelled'"
           [ngClass]="'db-status-' + latestStatus.toLowerCase().replace(' ', '-')">
        <div class="db-left">
          <svg class="db-icon" viewBox="0 0 18 18" fill="none">
            <path d="M1 4h11v8H1z" stroke="black" stroke-width="1.1" stroke-linejoin="round"/>
            <path d="M12 6.5l4 2V12h-4V6.5z" stroke="black" stroke-width="1.1" stroke-linejoin="round"/>
            <circle cx="4.5" cy="13" r="1.3" stroke="black" stroke-width="1.1"/>
            <circle cx="14" cy="13" r="1.3" stroke="black" stroke-width="1.1"/>
          </svg>
          <div>
            <div class="db-lbl">Delivery Status</div>
            <div class="db-val">{{ getShippingStatusText(latestStatus) }}</div>
          </div>
        </div>
        <div class="db-right">
          <div class="db-time" *ngIf="latestTimestamp">{{ latestTimestamp | date:'dd/MM/yyyy' }}<br>{{ latestTimestamp | date:'HH:mm' }}</div>
          <div class="tracking-info" *ngIf="order.trackingNumber">
            <span class="tracking-label">Tracking No.</span>
            <div class="tracking-row-inner">
              <span class="tracking-num">{{ order.trackingNumber }}</span>
              <button class="copy-btn" (click)="copyTracking(order.trackingNumber)">{{ copied ? '✓' : 'Copy' }}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Status History Timeline -->
      <div class="sec-hd"><div class="sec-title">Order Timeline</div><div class="sec-line"></div></div>

      <div class="tl" *ngFor="let step of fullTimeline; let last = last">
        <div class="tl-col">
          <div class="tl-dot" [ngClass]="step.state">
            <svg *ngIf="step.state === 'completed'" viewBox="0 0 8 8" fill="none"><path d="M1.5 4L3 5.5L6.5 2" stroke="white" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <div *ngIf="step.state === 'active'" class="active-inner"></div>
          </div>
          <div class="tl-bar" *ngIf="!last" [ngClass]="{'completed-bar': step.state === 'completed'}"></div>
        </div>
        <div class="tl-body">
          <div class="tl-nm" [ngClass]="step.state + '-text'">{{ getStatusLabel(step.status) }}</div>
          <div class="tl-tm" *ngIf="step.timestamp">{{ step.timestamp | date:'dd/MM/yyyy · HH:mm' }}</div>
          <div class="tl-note" *ngIf="step.note">{{ step.note }}</div>
        </div>
      </div>

      <!-- Products Section -->
      <div class="sec-hd"><div class="sec-title">Items Purchased</div><div class="sec-line"></div></div>

      <div class="item-row" *ngFor="let item of order.items">
        <div class="item-img">
          <img [src]="getImageUrl(item)" [alt]="item.name" onerror="this.onerror=null; this.src='data:image/svg+xml;charset=UTF-8,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'100\' height=\'100\'%3E%3Crect width=\'100\' height=\'100\' fill=\'%23f3f4f6\'/%3E%3Cpath d=\'M50 35a15 15 0 1 0 0 30 15 15 0 0 0 0-30zm0 25a10 10 0 1 1 0-20 10 10 0 0 1 0 20z\' fill=\'%23d1d5db\'/%3E%3C/svg%3E'">
        </div>
        <div class="item-info">
          <div class="item-nm">{{ item.name }}</div>
          <div class="item-shade" *ngIf="item.shade">{{ item.shade }}</div>
          <div class="item-qty">Qty · {{ item.quantity }}</div>

        </div>
        <div class="item-price">{{ (item.price * item.quantity) | currency:'USD':'symbol':'1.2-2' }}</div>
      </div>

      <!-- Totals -->
      <div class="totals">
        <div class="t-row"><span>Subtotal</span><span>{{ order.totalAmount | currency:'USD':'symbol':'1.2-2' }}</span></div>
        <div class="t-row"><span>Shipping</span><span>{{ (order.shippingFee > 0) ? (order.shippingFee | currency:'USD':'symbol':'1.2-2') : 'Free' }}</span></div>
        <div class="t-grand"><span>Total</span><span>{{ order.totalAmount | currency:'USD':'symbol':'1.2-2' }}</span></div>
      </div>

      <!-- Info Columns -->
      <div class="info-cols">
        <div *ngIf="order.shippingAddress">
          <div class="ib-title">Shipping Address</div>
          <div class="ib-val">
            {{ order.shippingAddress.fullName || order.user?.name }}<br>
            {{ order.shippingAddress.phone }}<br>
            {{ order.shippingAddress.address }}<br>
            {{ order.shippingAddress.city }}
          </div>
        </div>
        <div>
          <div class="ib-title">Payment Method</div>
          <div class="ib-val">
            {{ order.paymentMethod || 'COD' }}<br>
            <span class="payment-status">Confirmed</span>
          </div>
          <div class="ib-title" style="margin-top:14px">Full Order ID</div>
          <div class="ib-mono">{{ order._id }}</div>
        </div>
      </div>

      <!-- Review Strip -->
      <div class="review-strip" *ngIf="order.status === 'Delivered'">
        <div>
          <div class="rev-ey">Share Your Experience</div>
          <div class="rev-txt">Your review helps our beauty community.</div>
        </div>
        <div class="detail-actions">
          <!-- Buy Again Button -->
          <button class="btn-buy-again-detail"
                  [class.loading]="isReordering"
                  (click)="buyAgain(order)">
            <span class="btn-text">
              <svg class="redo-icon" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 3a5 5 0 1 0 4.546 2.914.5.5 0 0 1 .908-.417A6 6 0 1 1 8 2v1z"/>
                <path d="M8 4.466V.534a.25.25 0 0 1 .41-.192l2.36 1.966c.12.1.12.284 0 .384L8.41 4.658a.25.25 0 0 1-.41-.192z"/>
              </svg>
              Buy Again
            </span>
            <span class="btn-spinner" *ngIf="isReordering"></span>
          </button>

          <button class="rev-btn" (click)="goToReview()" *ngIf="order.status === 'Delivered'">
            {{ hasReviewedAllItems ? 'View Review' : 'Write a Review' }}
          </button>
        </div>
      </div>

    </div>
  `,
  styles: [`
    /* ── Breadcrumb ── */
    .breadcrumb {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 10px;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--color-text-muted, #888);
      margin-bottom: 1.75rem;
      cursor: pointer;
      width: fit-content;
    }
    .breadcrumb svg { width: 10px; height: 10px; }
    .breadcrumb:hover { color: var(--color-text, #111); }

    /* ── Header ── */
    .main-header { margin-bottom: 2rem; }
    .main-eyebrow {
      font-size: 9px;
      letter-spacing: 0.2em;
      text-transform: uppercase;
      color: var(--color-accent, #D32F2F);
      margin-bottom: 6px;
    }
    .main-title {
      font-family: var(--font-display, 'Playfair Display', Georgia, serif);
      font-size: 28px;
      font-weight: 500;
      color: var(--color-text, #111);
      line-height: 1.1;
    }
    .main-sub {
      font-size: 12px;
      color: var(--color-text-muted, #888);
      margin-top: 6px;
    }

    /* ── Status Row ── */
    .status-row {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      border-top: 1px solid var(--color-text, #111);
      border-bottom: 1px solid #e8e6e1;
      margin-bottom: 2rem;
    }
    .sc {
      padding: 12px 0;
      border-right: 1px solid #e8e6e1;
    }
    .sc:last-child { border-right: none; }
    .sc-l {
      font-size: 9px;
      letter-spacing: 0.15em;
      text-transform: uppercase;
      color: var(--color-text-muted, #888);
      margin-bottom: 5px;
    }
    .sc-v {
      font-size: 13px;
      font-weight: 500;
      color: var(--color-text, #111);
    }
    .sc-v.accent { var(--color-text, #111); }

    /* Status Badge */
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 11px;
      font-weight: 500;
    }
    .status-badge::before {
      content: '';
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: currentColor;
    }
    .badge-delivered { color: #2a6f07; }
    .badge-shipped { color: #1d4ed8; }
    .badge-processing { color: #b45309; }
    .badge-pending { color: #d97706; }
    .badge-order-placed { color: var(--color-text-muted, #888); }
    .badge-cancelled { color: #6b7280; }

    /* ── Delivery Block ── */
    .delivery-block {
      background: var(--color-surface, #f5f5f5);
      padding: 14px 18px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 2rem;
      gap: 1rem;
      border-left: 3px solid transparent;
    }
    .db-status-delivered { border-left-color: #2a6f07; }
    .db-status-shipped   { border-left-color: #1d4ed8; }
    .db-status-processing { border-left-color: #b45309; }
    .db-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .db-icon {
      width: 18px;
      height: 18px;
      opacity: 0.6;
      flex-shrink: 0;
    }
    .db-lbl {
      font-size: 9px;
      letter-spacing: 0.15em;
      text-transform: uppercase;
      color: rgba(37, 37, 37, 0.4);
      margin-bottom: 3px;
    }
    .db-val {
      font-size: 13px;
      color: #000000ff;
    }
    .db-right {
      text-align: right;
      flex-shrink: 0;
    }
    .db-time {
      font-size: 11px;
      color: rgba(255,255,255,0.35);
      line-height: 1.6;
    }
    .tracking-info {
      margin-top: 8px;
    }
    .tracking-label {
      font-size: 9px;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: rgba(255,255,255,0.35);
      display: block;
      margin-bottom: 3px;
    }
    .tracking-row-inner {
      display: flex;
      align-items: center;
      gap: 8px;
      justify-content: flex-end;
    }
    .tracking-num {
      font-size: 11px;
      color: rgba(255,255,255,0.7);
      font-family: monospace;
    }
    .copy-btn {
      background: rgba(255,255,255,0.1);
      border: none;
      padding: 3px 8px;
      cursor: pointer;
      font-size: 10px;
      font-weight: 500;
      color: rgba(255,255,255,0.6);
      letter-spacing: 0.08em;
      text-transform: uppercase;
      transition: all 0.15s;
    }
    .copy-btn:hover {
      background: rgba(255,255,255,0.2);
      color: #fff;
    }

    /* ── Section Headings ── */
    .sec-hd {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 1.25rem;
      margin-top: 1.75rem;
    }
    .sec-title {
      font-size: 9px;
      letter-spacing: 0.2em;
      text-transform: uppercase;
      color: var(--color-text, #111);
      font-weight: 500;
      white-space: nowrap;
    }
    .sec-line {
      flex: 1;
      height: 1px;
      background: #e8e6e1;
    }

    /* ── Timeline ── */
    .tl {
      display: flex;
      gap: 14px;
      padding-bottom: 16px;
    }
    .tl:last-of-type { padding-bottom: 0; }
    .tl-col {
      display: flex;
      flex-direction: column;
      align-items: center;
      width: 16px;
      flex-shrink: 0;
    }
    .tl-dot {
      width: 16px;
      height: 16px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .tl-dot.completed {
      background: #10b981;
      border: 2px solid #10b981;
    }
    .tl-dot.active {
      background: #fff;
      border: 2px solid var(--color-text, #111);
    }
    .tl-dot.future {
      background: #fff;
      border: 2px solid #e8e6e1;
    }
    .active-inner {
      width: 6px;
      height: 6px;
      background: var(--color-text, #111);
      border-radius: 50%;
    }
    .tl-dot svg { width: 8px; height: 8px; stroke-width: 1.5; }
    .tl-bar {
      flex: 1;
      width: 1.5px;
      background: #e8e6e1;
      margin-top: 3px;
      min-height: 12px;
    }
    .tl-bar.completed-bar {
      background: #10b981;
    }
    .tl-body { padding-top: 1px; }
    .tl-nm {
      font-size: 12px;
      font-weight: 500;
      color: var(--color-text, #111);
    }
    .tl-nm.completed-text {
      color: var(--color-text-muted, #888);
    }
    .tl-nm.future-text {
      color: #b0b0b0;
    }
    .tl-tm {
      font-size: 10px;
      color: var(--color-text-muted, #888);
      margin-top: 3px;
    }
    .tl-note {
      font-size: 11px;
      color: var(--color-text-muted, #888);
      margin-top: 4px;
    }

    /* ── Items ── */
    .item-row {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 0;
      border-bottom: 1px solid #e8e6e1;
    }
    .item-row:last-of-type { border-bottom: none; }
    .item-img {
      width: 56px;
      height: 56px;
      background: #fafaf8;
      border: 1px solid #e8e6e1;
      flex-shrink: 0;
      overflow: hidden;
    }
    .item-img img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .item-info { flex: 1; }
    .item-nm {
      font-size: 13px;
      font-weight: 500;
      color: var(--color-text, #111);
      line-height: 1.4;
    }
    .item-shade {
      font-size: 11px;
      color: var(--color-text-muted, #888);
      margin-top: 3px;
    }
    .item-qty {
      font-size: 10px;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--color-text-muted, #888);
      margin-top: 4px;
    }
    .item-price {
      font-size: 13px;
      font-weight: 500;
      color: var(--color-text, #111);
      margin-left: auto;
      flex-shrink: 0;
    }

    /* ── Totals ── */
    .totals {
      padding-top: 12px;
      border-top: 1px solid #e8e6e1;
    }
    .t-row {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--color-text-muted, #888);
      padding: 2px 0;
    }
    .t-grand {
      display: flex;
      justify-content: space-between;
      font-size: 14px;
      font-weight: 500;
      color: var(--color-text, #111);
      padding-top: 10px;
      border-top: 1px solid var(--color-text, #111);
      margin-top: 8px;
    }

    /* ── Info Columns ── */
    .info-cols {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 2rem;
      margin-top: 1.75rem;
    }
    .ib-title {
      font-size: 9px;
      letter-spacing: 0.18em;
      text-transform: uppercase;
      color: var(--color-text-muted, #888);
      margin-bottom: 8px;
    }
    .ib-val {
      font-size: 12px;
      color: var(--color-text, #111);
      line-height: 1.9;
    }
    .payment-status {
      font-size: 11px;
      color: var(--color-text-muted, #888);
    }
    .ib-mono {
      font-size: 10px;
      color: var(--color-text-muted, #888);
      font-family: monospace;
      word-break: break-all;
      line-height: 1.6;
      margin-top: 4px;
    }

    /* ── Review Strip ── */
    .review-strip {
      margin-top: 2rem;
      border-top: 1px solid #e8e6e1;
      padding-top: 1.5rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }
    .rev-ey {
      font-size: 9px;
      letter-spacing: 0.15em;
      text-transform: uppercase;
      color: var(--color-accent, #D32F2F);
      margin-bottom: 4px;
    }
    .rev-txt {
      font-size: 12px;
      color: var(--color-text-muted, #888);
    }
    .rev-btn {
      font-size: 9px;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      font-weight: 500;
      padding: 9px 20px;
      border: 1px solid var(--color-text, #111);
      background: transparent;
      color: var(--color-text, #111);
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.15s;
    }
    .rev-btn:hover {
      background: var(--color-text, #111);
      color: #fff;
    }

    /* ── Review Strip Actions ── */
    .detail-actions {
      display: flex;
      gap: 10px;
      align-items: center;
    }

    /* ── Buy Again Button (Detail) ── */
    .btn-buy-again-detail {
      font-size: 9px;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      font-weight: 500;
      padding: 9px 20px;
      border: 1px solid #000000ff;
      background: transparent;
      color: #000000ff;
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.3s;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      position: relative;
      min-width: 120px;
    }
    .btn-buy-again-detail:hover {
      background: #000000ff;
      color: #fff;
    }
    .btn-buy-again-detail.loading {
      color: transparent;
      pointer-events: none;
    }
    .btn-buy-again-detail .btn-text {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .btn-buy-again-detail .redo-icon {
      width: 12px;
      height: 12px;
    }
    .btn-buy-again-detail .btn-spinner {
      position: absolute;
      width: 16px;
      height: 16px;
      border: 2px solid #ff6b6b;
      border-top-color: transparent;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    /* ── Loading & Error ── */
    .loading-state, .error-state {
      text-align: center;
      padding: 80px 20px;
    }
    .spinner {
      width: 32px;
      height: 32px;
      border: 2px solid #e8e6e1;
      border-top: 2px solid var(--color-text, #111);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin: 0 auto 16px;
    }
    .loading-state p {
      font-size: 11px;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--color-text-muted, #888);
    }
    .error-icon {
      font-size: 36px;
      margin-bottom: 16px;
    }
    .error-state h3 {
      font-family: var(--font-display, 'Playfair Display', Georgia, serif);
      font-size: 20px;
      font-weight: 500;
      color: var(--color-text, #111);
      margin-bottom: 8px;
    }
    .error-state p {
      font-size: 13px;
      color: var(--color-text-muted, #888);
      margin-bottom: 20px;
    }
    .btn-outline {
      font-size: 10px;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      font-weight: 500;
      padding: 10px 24px;
      border: 1px solid var(--color-text, #111);
      background: transparent;
      color: var(--color-text, #111);
      cursor: pointer;
      transition: all 0.15s;
    }
    .btn-outline:hover {
      background: var(--color-text, #111);
      color: #fff;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }

    @media (max-width: 600px) {
      .status-row { grid-template-columns: 1fr 1fr; }
      .info-cols { grid-template-columns: 1fr; }
      .delivery-block { flex-direction: column; align-items: flex-start; }
      .db-right { text-align: left; }
    }
  `]
})
export class OrderDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private orderService = inject(OrderService);
  private userService = inject(UserService);
  private cartService = inject(CartService);
  private toastService = inject(ToastService);

  order: any = null;
  loading: boolean = true;
  isReordering: boolean = false;
  error: string | null = null;
  copied: boolean = false;
  myReviewedProductIds = new Set<string>();

  readonly statusLabels: Record<string, string> = {
    'Order Placed': 'Order Placed',
    'Pending': 'Pending',
    'Processing': 'Processing',
    'Shipped': 'Shipped',
    'Delivered': 'Delivered',
    'Cancelled': 'Cancelled'
  };

  // Canonical workflow order for sorting/deduplication
  private readonly statusOrder = ['Order Placed', 'Pending', 'Processing', 'Shipped', 'Delivered', 'Cancelled'];

  get timelineHistory(): any[] {
    const history = this.order?.statusHistory;
    if (!Array.isArray(history) || history.length === 0) {
      return [{ status: this.order?.status || 'Order Placed', timestamp: this.order?.updatedAt || this.order?.createdAt, note: '' }];
    }

    // Deduplicate: for each status key, keep the entry with the latest timestamp
    const seen = new Map<string, any>();
    for (const entry of history) {
      const existing = seen.get(entry.status);
      if (!existing || new Date(entry.timestamp) > new Date(existing.timestamp)) {
        seen.set(entry.status, entry);
      }
    }

    // Sort by canonical workflow order, then by timestamp as tiebreaker
    return Array.from(seen.values()).sort((a, b) => {
      const ai = this.statusOrder.indexOf(a.status);
      const bi = this.statusOrder.indexOf(b.status);
      if (ai !== bi) return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
      return new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime();
    });
  }

  get fullTimeline(): any[] {
    const currentStatus = this.latestStatus;
    let baseOrder = ['Order Placed', 'Pending', 'Processing', 'Shipped', 'Delivered'];

    if (currentStatus === 'Cancelled') {
      baseOrder = ['Order Placed', 'Cancelled'];
    }

    let currentIndex = baseOrder.indexOf(currentStatus);
    if (currentIndex === -1) {
      baseOrder.push(currentStatus);
      currentIndex = baseOrder.length - 1;
    }

    // Build a map of status → history entry, keeping the latest timestamp
    // for each status so duplicate entries never cause stale timestamps.
    const historyMap = new Map();
    if (Array.isArray(this.order?.statusHistory)) {
      for (const h of this.order.statusHistory) {
        const existing = historyMap.get(h.status);
        if (!existing || new Date(h.timestamp) > new Date(existing.timestamp)) {
          historyMap.set(h.status, h);
        }
      }
    }

    return baseOrder.map((status, index) => {
      const historyItem = historyMap.get(status);
      let state = 'future';
      if (index < currentIndex) state = 'completed';
      else if (index === currentIndex) state = 'active';

      return {
        status: status,
        state: state,
        timestamp: historyItem?.timestamp || (index === 0 ? this.order?.createdAt : null),
        note: historyItem?.note || ''
      };
    });
  }

  // Single source of truth: always the DB field, not derived from statusHistory.
  // statusHistory is a log of past events and may be incomplete; order.status
  // is what the backend authoritatively set last.
  get latestStatus(): string {
    return this.order?.status || 'Order Placed';
  }

  get latestTimestamp(): string | undefined {
    // Prefer the recorded timestamp for the current status if available,
    // otherwise fall back to updatedAt (which the backend always updates).
    const currentStatus = this.latestStatus;
    const historyEntry = (this.order?.statusHistory as any[])
      ?.find((h: any) => h.status === currentStatus);
    return historyEntry?.timestamp || this.order?.updatedAt;
  }

  get hasReviewedAllItems(): boolean {
    if (!this.order?.items?.length) return false;
    return this.order.items.every((item: any) => {
      const productId = typeof item.product === 'object' ? item.product?._id : item.product;
      return !!productId && this.myReviewedProductIds.has(String(productId));
    });
  }

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.fetchOrderDetails(id);
      } else {
        this.error = "Invalid order ID.";
        this.loading = false;
      }
    });
  }

  fetchOrderDetails(id: string) {
    this.loading = true;
    this.error = null;
    forkJoin({
      order: this.orderService.getOrderById(id),
      myReviews: this.userService.getMyReviews().pipe(catchError(() => of([])))
    }).pipe(
      catchError((err: any) => {
        this.error = err.message || "Failed to load order details.";
        this.loading = false;
        return of(null);
      })
    ).subscribe((payload: any) => {
      if (payload?.order) {
        this.order = payload.order;
      }
      const reviews = payload?.myReviews || [];
      this.myReviewedProductIds = new Set(
        reviews
          .map((r: any) => r?.product?._id || r?.product)
          .filter((x: any) => !!x)
          .map((x: any) => String(x))
      );
      this.loading = false;
    });
  }

  copyTracking(trackingNumber: string) {
    if (navigator.clipboard && trackingNumber) {
      navigator.clipboard.writeText(trackingNumber).then(() => {
        this.copied = true;
        setTimeout(() => this.copied = false, 2000);
      });
    }
  }

  buyAgain(order: any): void {
    if (this.isReordering) return;

    this.isReordering = true;
    this.orderService.getReorderData(order._id).subscribe({
      next: (data) => {
        const availableItems = data.items.filter((item: any) => item.isAvailable);
        const skippedCount = data.items.length - availableItems.length;

        if (availableItems.length === 0) {
          this.toastService.error('Sorry, all items in this order are currently out of stock.');
          this.isReordering = false;
          return;
        }

        const addOps = availableItems.map((item: any) =>
          this.cartService.addToCart({
            productId: item.productId,
            quantity: item.quantity,
            shade: item.shade
          })
        );

        forkJoin(addOps).subscribe({
          next: () => {
            if (skippedCount > 0) {
              this.toastService.warning(`${skippedCount} items were skipped as they are out of stock.`);
            } else {
              this.toastService.success('Items added to cart!');
            }
            this.router.navigate(['/cart']);
          },
          error: (err) => {
            this.toastService.error('Failed to add some items to cart.');
            this.isReordering = false;
          }
        });
      },
      error: (err) => {
        this.toastService.error('Failed to retrieve reorder data.');
        this.isReordering = false;
      }
    });
  }

  getImageUrl(item: any): string {
    if (item.image) {
      if (Array.isArray(item.image) && item.image.length > 0) return item.image[0];
      if (typeof item.image === 'string') {
        const parts = item.image.split(',');
        return parts[0].trim();
      }
    }
    if (item.product?.Thumbnail_Images) {
      return item.product.Thumbnail_Images;
    }
    return 'data:image/svg+xml;charset=UTF-8,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'100\' height=\'100\'%3E%3Crect width=\'100\' height=\'100\' fill=\'%23f3f4f6\'/%3E%3Cpath d=\'M50 35a15 15 0 1 0 0 30 15 15 0 0 0 0-30zm0 25a10 10 0 1 1 0-20 10 10 0 0 1 0 20z\' fill=\'%23d1d5db\'/%3E%3C/svg%3E';
  }

  getStatusLabel(status: string): string {
    return this.statusLabels[status] || status || 'N/A';
  }

  goToReview() {
    const firstItem = this.order?.items?.find((item: any) => {
      const productId = typeof item.product === 'object' ? item.product?._id : item.product;
      return !!productId;
    });
    const slug = firstItem?.product?.slug;
    if (!slug) {
      this.router.navigate(['/account/orders']);
      return;
    }
    this.router.navigate(['/products', slug], {
      queryParams: { writeReview: this.hasReviewedAllItems ? 0 : 1, orderId: this.order?._id },
      fragment: 'reviews-section'
    });
  }

  getShippingStatusText(status: string): string {
    switch (status) {
      case 'Delivered': return 'Delivered successfully';
      case 'Shipped': return 'On its way to you';
      case 'Processing': return 'Preparing your order';
      default: return 'Awaiting dispatch...';
    }
  }
}
