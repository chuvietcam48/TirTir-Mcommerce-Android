import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdminUserService } from '../../services/admin-user.service';

@Component({
    selector: 'app-customer-detail',
    standalone: true,
    imports: [CommonModule, RouterLink],
    template: `
    <div class="page" *ngIf="user">
      <div class="page-header">
        <h1>{{ user.name }}</h1>
        <a class="back" routerLink="/admin/customers">← Back</a>
      </div>
      <div class="grid">
        <div class="card">
          <h2>Profile</h2>
          <p><strong>Email:</strong> {{ user.email }}</p>
          <p><strong>Phone:</strong> {{ user.phone || '—' }}</p>
          <p><strong>Gender:</strong> {{ user.gender || '—' }}</p>
          <p><strong>Role:</strong> {{ user.role }}</p>
          <p><strong>Status:</strong> {{ user.isBlocked ? 'Blocked' : 'Active' }}</p>
          <p><strong>Joined:</strong> {{ user.createdAt | date:'dd/MM/yyyy' }}</p>
        </div>
        <div class="card">
          <h2>Order History</h2>
          @if (orders.length === 0) { <p class="muted">No orders found</p> }
          @for (o of orders; track o._id) {
            <div class="order-row">
              <span class="mono">{{ o._id | slice:0:8 }}...</span>
              <span>₫{{ o.totalAmount?.toLocaleString() }}</span>
              <span class="badge" [class]="'s-' + (o.status||'').toLowerCase()">{{ o.status }}</span>
            </div>
          }
        </div>
      </div>
    </div>
    <div *ngIf="loading" class="empty">Loading...</div>
  `,
    styleUrl: './customer-detail.css',
})
export class CustomerDetailComponent implements OnInit {
    private svc = inject(AdminUserService);
    private route = inject(ActivatedRoute);
    user: any = null; orders: any[] = []; loading = true;

    ngOnInit() {
        const id = this.route.snapshot.paramMap.get('id')!;
        this.svc.getUserDetail(id).subscribe({
            next: (u) => { this.user = u; this.loading = false; },
            error: () => this.loading = false,
        });
        this.svc.getUserOrders(id).subscribe({
            next: (o: any) => this.orders = Array.isArray(o) ? o : (o.orders || []),
        });
    }
}
