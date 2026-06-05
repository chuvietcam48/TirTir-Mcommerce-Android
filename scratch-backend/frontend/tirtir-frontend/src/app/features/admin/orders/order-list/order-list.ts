import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminOrderService, AdminOrder, PaginatedOrders } from '../../services/admin-order.service';

@Component({
    selector: 'app-admin-order-list',
    standalone: true,
    imports: [CommonModule, RouterLink, FormsModule],
    template: `
    <div class="page">
      <div class="page-header"><h1>Orders</h1><p class="sub">{{ total }} orders total</p></div>
      <div class="filters-bar">
        <input type="text" class="filter-input" placeholder="Search by Order ID..." [(ngModel)]="search" (keyup.enter)="load()">
        <select class="filter-input" [(ngModel)]="statusFilter" (change)="load()">
          <option value="">All Status</option>
          <option value="Pending">Pending</option><option value="Processing">Processing</option>
          <option value="Shipped">Shipped</option><option value="Delivered">Delivered</option>
          <option value="Cancelled">Cancelled</option>
        </select>
        <input type="date" class="filter-input" [(ngModel)]="startDate" (change)="load()">
        <input type="date" class="filter-input" [(ngModel)]="endDate" (change)="load()">
      </div>
      <div class="table-card">
        <div class="table-wrapper">
          <table class="admin-table">
            <thead><tr><th>Order ID</th><th>Customer</th><th>Items</th><th>Total</th><th>Payment</th><th>Status</th><th>Date</th><th>Action</th></tr></thead>
            <tbody>
              @if (loading) { <tr><td colspan="8" class="empty">Loading...</td></tr> }
              @if (!loading && orders.length === 0) { <tr><td colspan="8" class="empty">No orders found</td></tr> }
              @for (o of orders; track o._id) {
                <tr>
                  <td class="mono">{{ o._id | slice:0:8 }}...</td>
                  <td>{{ o.user?.name || 'Guest' }}</td>
                  <td>{{ o.items.length }}</td>
                  <td class="bold">₫{{ o.totalAmount.toLocaleString() }}</td>
                  <td>{{ o.paymentMethod }}</td>
                  <td><span class="badge" [class]="'s-' + o.status.toLowerCase()">{{ o.status }}</span></td>
                  <td class="muted">{{ o.createdAt | date:'dd/MM/yy' }}</td>
                  <td><a class="link" [routerLink]="['/admin/orders', o._id]">View</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (totalPages > 1) {
          <div class="pag">
            <button (click)="page > 1 && goTo(page-1)" [disabled]="page===1">‹</button>
            <span>{{ page }} / {{ totalPages }}</span>
            <button (click)="page < totalPages && goTo(page+1)" [disabled]="page===totalPages">›</button>
          </div>
        }
      </div>
    </div>
  `,
    styleUrl: './order-list.css',
})
export class AdminOrderListComponent implements OnInit {
    private svc = inject(AdminOrderService);
    orders: AdminOrder[] = []; total = 0; page = 1; totalPages = 1; loading = true;
    search = ''; statusFilter = ''; startDate = ''; endDate = '';

    ngOnInit() { this.load(); }

    load() {
        this.loading = true;
        this.svc.getOrders({ page: this.page, limit: 15, status: this.statusFilter || undefined, search: this.search || undefined, startDate: this.startDate || undefined, endDate: this.endDate || undefined }).subscribe({
            next: (r: PaginatedOrders) => { this.orders = r.orders; this.total = r.total; this.totalPages = r.pages; this.loading = false; },
            error: () => this.loading = false,
        });
    }

    goTo(p: number) { this.page = p; this.load(); }
}
