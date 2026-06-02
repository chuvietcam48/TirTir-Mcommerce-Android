import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AdminCouponService } from '../../services/admin-coupon.service';

@Component({
    selector: 'app-coupon-form',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterLink],
    template: `
    <div class="page">
      <div class="page-header"><h1>{{ isEdit ? 'Edit Coupon' : 'Create Coupon' }}</h1><a class="back" routerLink="/admin/promotions">← Back</a></div>
      <form class="form-card" (ngSubmit)="save()">
        <div class="form-grid">
          <div class="form-group"><label>Code *</label><input class="input" [(ngModel)]="form.code" name="code" required [readonly]="isEdit"></div>
          <div class="form-group"><label>Discount Type *</label>
            <select class="input" [(ngModel)]="form.discountType" name="type" required><option value="percentage">Percentage (%)</option><option value="fixed">Fixed (₫)</option></select>
          </div>
          <div class="form-group"><label>Discount Value *</label><input class="input" type="number" [(ngModel)]="form.discountValue" name="value" required min="0"></div>
          <div class="form-group"><label>Min Order Value</label><input class="input" type="number" [(ngModel)]="form.minOrderValue" name="min" min="0"></div>
          <div class="form-group"><label>Max Discount</label><input class="input" type="number" [(ngModel)]="form.maxDiscount" name="max" min="0"></div>
          <div class="form-group"><label>Usage Limit</label><input class="input" type="number" [(ngModel)]="form.usageLimit" name="limit" min="0"></div>
          <div class="form-group"><label>Valid From</label><input class="input" type="date" [(ngModel)]="form.validFrom" name="from"></div>
          <div class="form-group"><label>Valid To</label><input class="input" type="date" [(ngModel)]="form.validTo" name="to"></div>
        </div>
        <div class="form-footer">
          <a class="btn-sec" routerLink="/admin/promotions">Cancel</a>
          <button type="submit" class="btn-primary" [disabled]="saving">{{ saving ? 'Saving...' : (isEdit ? 'Update' : 'Create') }}</button>
        </div>
      </form>
    </div>
  `,
    styleUrl: './coupon-form.css',
})
export class CouponFormComponent implements OnInit {
    private svc = inject(AdminCouponService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    isEdit = false; saving = false;
    form: any = { code: '', discountType: 'percentage', discountValue: 0, minOrderValue: 0, maxDiscount: 0, usageLimit: 0, validFrom: '', validTo: '' };

    ngOnInit() {
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
            this.isEdit = true;
            this.svc.getCoupon(id).subscribe({ next: (c) => { this.form = { ...c, validFrom: c.validFrom?.split('T')[0], validTo: c.validTo?.split('T')[0] }; } });
        }
    }

    save() {
        this.saving = true;
        const obs = this.isEdit
            ? this.svc.updateCoupon(this.route.snapshot.paramMap.get('id')!, this.form)
            : this.svc.createCoupon(this.form);
        obs.subscribe({
            next: () => { this.saving = false; this.router.navigate(['/admin/promotions']); },
            error: (e) => { this.saving = false; alert('Error: ' + (e.error?.message || e.message)); },
        });
    }
}
