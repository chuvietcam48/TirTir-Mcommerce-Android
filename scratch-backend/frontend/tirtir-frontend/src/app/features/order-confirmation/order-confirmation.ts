import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { OrderService } from '../../core/services/order.service';
import { Order } from '../../core/models';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner';

@Component({
    selector: 'app-order-confirmation',
    standalone: true,
    imports: [CommonModule, RouterModule, LoadingSpinnerComponent],
    template: `
    <div class="confirmation-container">
      <div *ngIf="loading" class="loading"><app-loading-spinner></app-loading-spinner></div>
      <div *ngIf="!loading && order" class="confirmation-card">
        <div class="success-icon">✓</div>
        <h1>Order Confirmed!</h1>
        <p class="order-id">Order #{{ orderId }}</p>
        <p class="message">Thank you for your purchase. We'll send you a confirmation email shortly.</p>
        <div class="actions">
          <a routerLink="/account/orders" class="btn-primary">View Orders</a>
          <a routerLink="/shop" class="btn-secondary">Continue Shopping</a>
        </div>
      </div>
    </div>
  `,
    styles: [`
    .confirmation-container{min-height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#fafafa 0%,#f5f5f5 100%);padding:2rem}
    .confirmation-card{background:#fff;border-radius:8px;box-shadow:0 2px 24px rgba(0,0,0,0.08);padding:3rem 2.5rem;max-width:500px;text-align:center}
    .success-icon{width:80px;height:80px;line-height:80px;border-radius:50%;background:#10b981;color:#fff;font-size:3rem;margin:0 auto 1.5rem}
    h1{font-size:1.875rem;font-weight:600;color:#1a1a1a;margin:0 0 0.5rem}
    .order-id{font-size:1rem;color:#666;margin-bottom:1rem}
    .message{color:#666;margin-bottom:2rem}
    .actions{display:flex;gap:1rem;flex-direction:column}
    .btn-primary,.btn-secondary{padding:0.875rem 2rem;font-size:1rem;font-weight:600;text-decoration:none;border-radius:6px;transition:all 0.2s}
    .btn-primary{color:#fff;background:#1a1a1a}
    .btn-primary:hover{background:#333;transform:translateY(-1px)}
    .btn-secondary{color:#1a1a1a;border:1.5px solid #e5e5e5}
    .btn-secondary:hover{border-color:#1a1a1a}
    .loading{padding:4rem}
  `],
})
export class OrderConfirmationComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private orderService = inject(OrderService);

    orderId = '';
    order: Order | null = null;
    loading = true;

    ngOnInit(): void {
        this.orderId = this.route.snapshot.paramMap.get('id') || '';
        if (this.orderId) {
            this.orderService.getOrderById(this.orderId).subscribe({
                next: (order) => {
                    this.order = order;
                    this.loading = false;
                },
                error: () => {
                    this.loading = false;
                },
            });
        }
    }
}
