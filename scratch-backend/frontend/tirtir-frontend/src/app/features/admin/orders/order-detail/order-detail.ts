import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminOrderService, AdminOrder } from '../../services/admin-order.service';

@Component({
    selector: 'app-admin-order-detail',
    standalone: true,
    imports: [CommonModule, RouterLink, FormsModule],
    template: `
    <div class="page" *ngIf="order">
      <div class="page-header">
        <div>
          <h1>Order #{{ order._id | slice:0:8 }}</h1>
          <span class="badge" [class]="'s-' + order.status.toLowerCase()">{{ order.status }}</span>
        </div>
        <a class="back" routerLink="/admin/orders">← Back to Orders</a>
      </div>
      <div class="grid">
        <div class="card">
          <h2>Customer</h2>
          <p><strong>{{ order.user?.name || 'Guest' }}</strong></p>
          <p class="muted">{{ order.user?.email || '—' }}</p>
        </div>
        <div class="card">
          <h2>Shipping</h2>
          <p>{{ order.shippingAddress?.fullName }}</p>
          <p class="muted">{{ order.shippingAddress?.address }}, {{ order.shippingAddress?.ward }}, {{ order.shippingAddress?.district }}, {{ order.shippingAddress?.province }}</p>
          <p class="muted">Phone: {{ order.shippingAddress?.phone }}</p>
        </div>
        <div class="card">
          <h2>Update Status</h2>
          <select class="select" [(ngModel)]="newStatus">
            <option value="Pending">Pending</option><option value="Processing">Processing</option>
            <option value="Shipped">Shipped</option><option value="Delivered">Delivered</option>
            <option value="Cancelled">Cancelled</option>
          </select>
          <button class="btn-primary" (click)="updateStatus()" [disabled]="saving">{{ saving ? 'Saving...' : 'Update' }}</button>
        </div>
      </div>
      <div class="card">
        <h2>Order Items</h2>
        <table class="items-table">
          <thead><tr><th>Product</th><th>Qty</th><th>Price</th><th>Subtotal</th></tr></thead>
          <tbody>
            @for (item of order.items; track item.name) {
              <tr>
                <td>{{ item.name }}{{ item.shade ? ' — ' + item.shade : '' }}</td>
                <td>{{ item.quantity }}</td>
                <td>₫{{ item.price.toLocaleString() }}</td>
                <td class="bold">₫{{ (item.price * item.quantity).toLocaleString() }}</td>
              </tr>
            }
          </tbody>
          <tfoot><tr><td colspan="3" class="total-label">Total</td><td class="bold">₫{{ order.totalAmount.toLocaleString() }}</td></tr></tfoot>
        </table>
      </div>
      <div class="meta">
        <span>Payment: {{ order.paymentMethod }}</span>
        <span>Created: {{ order.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
        @if (order.ghnOrderCode) { <span>GHN: {{ order.ghnOrderCode }}</span> }
      </div>
    </div>
    <div *ngIf="!order && !loading" class="empty">Order not found</div>
    <div *ngIf="loading" class="empty">Loading...</div>
  `,
    styleUrl: './order-detail.css',
})
export class AdminOrderDetailComponent implements OnInit {
    private svc = inject(AdminOrderService);
    private route = inject(ActivatedRoute);
    order: AdminOrder | null = null;
    loading = true; saving = false;
    newStatus = '';

    ngOnInit() {
        const id = this.route.snapshot.paramMap.get('id')!;
        this.svc.getOrderDetail(id).subscribe({
            next: (o) => { this.order = o; this.newStatus = o.status; this.loading = false; },
            error: () => this.loading = false,
        });
    }

    updateStatus() {
        if (!this.order) return;
        this.saving = true;
        this.svc.updateOrderStatus(this.order._id, this.newStatus).subscribe({
            next: () => { this.order!.status = this.newStatus; this.saving = false; },
            error: () => this.saving = false,
        });
    }
}
