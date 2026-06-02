import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdminCouponService, AdminCoupon } from '../../services/admin-coupon.service';

@Component({
    selector: 'app-coupon-list',
    standalone: true,
    imports: [CommonModule, RouterLink],
    template: `
    <div class="page">
      <div class="page-header"><h1>Promotions</h1><a class="btn-primary" routerLink="/admin/promotions/new">+ Create Coupon</a></div>
      <div class="table-card"><div class="table-wrapper">
        <table class="admin-table">
          <thead><tr><th>Code</th><th>Type</th><th>Value</th><th>Used / Limit</th><th>Valid Until</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>
            @if (loading) { <tr><td colspan="7" class="empty">Loading...</td></tr> }
            @if (!loading && coupons.length === 0) { <tr><td colspan="7" class="empty">No coupons found</td></tr> }
            @for (c of coupons; track c._id) {
              <tr>
                <td class="mono bold">{{ c.code }}</td>
                <td>{{ c.discountType === 'percentage' ? '%' : 'Fixed' }}</td>
                <td>{{ c.discountType === 'percentage' ? c.discountValue + '%' : '₫' + c.discountValue.toLocaleString() }}</td>
                <td>{{ c.usedCount }} / {{ c.usageLimit || '∞' }}</td>
                <td class="muted">{{ c.validTo | date:'dd/MM/yy' }}</td>
                <td>
                  <span class="badge" [class]="c.active ? 'active' : 'inactive'">{{ c.active ? 'Active' : 'Inactive' }}</span>
                </td>
                <td>
                  <a class="link" [routerLink]="['/admin/promotions', c._id, 'edit']">Edit</a>
                  <button class="link-btn" (click)="toggle(c)">{{ c.active ? 'Deactivate' : 'Activate' }}</button>
                  <button class="link-btn danger" (click)="remove(c)">Delete</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div></div>
    </div>
  `,
    styleUrl: './coupon-list.css',
})
export class CouponListComponent implements OnInit {
    private svc = inject(AdminCouponService);
    coupons: AdminCoupon[] = []; loading = true;

    ngOnInit() { this.load(); }

    load() {
        this.loading = true;
        this.svc.getCoupons().subscribe({
            next: (r: any) => { this.coupons = Array.isArray(r) ? r : (r.coupons || r.data || []); this.loading = false; },
            error: () => this.loading = false,
        });
    }

    toggle(c: AdminCoupon) {
        this.svc.toggleStatus(c._id).subscribe({ next: () => { c.active = !c.active; } });
    }

    remove(c: AdminCoupon) {
        if (!confirm(`Delete coupon ${c.code}?`)) return;
        this.svc.deleteCoupon(c._id).subscribe({ next: () => this.load() });
    }
}
