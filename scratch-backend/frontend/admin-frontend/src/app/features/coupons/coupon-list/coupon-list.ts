import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CouponService, Coupon } from '../../../core/services/coupon.service';

@Component({
    selector: 'app-coupon-list',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule],
    templateUrl: './coupon-list.html',
    styleUrls: ['./coupon-list.css']
})
export class CouponListComponent implements OnInit {
    coupons: Coupon[] = [];
    filteredCoupons: Coupon[] = [];
    loading = true;
    error: string | null = null;
    actionError: string | null = null;

    // Coupon Stats
    stats: any = null;
    statsLoading = true;

    // Pagination
    currentPage = 1;
    pageSize = 10;
    totalPages = 1;

    // Filters
    searchQuery = '';
    selectedStatus = '';

    // Inline delete confirm
    pendingDeleteId: string | null = null;

    constructor(private couponService: CouponService) { }

    ngOnInit(): void {
        this.loadCoupons();
        this.loadStats();
    }

    loadStats(): void {
        this.couponService.getCouponStats().subscribe({
            next: (data) => { this.stats = data; this.statsLoading = false; },
            error: () => { this.statsLoading = false; }
        });
    }

    loadCoupons(): void {
        this.loading = true;
        this.error = null;

        this.couponService.getAllCoupons().subscribe({
            next: (data: any) => {
                // Handle array or wrapped response
                this.coupons = Array.isArray(data) ? data : (data.coupons || data.data || []);
                this.applyFilters();
                this.loading = false;
            },
            error: (err: any) => {
                this.error = 'Failed to load coupons';
                this.loading = false;
                console.error('Coupon load error:', err);
            }
        });
    }

    applyFilters(): void {
        let filtered = [...this.coupons];

        // Search filter
        if (this.searchQuery.trim()) {
            const query = this.searchQuery.toLowerCase();
            filtered = filtered.filter(c =>
                c.code.toLowerCase().includes(query)
            );
        }

        // Status filter
        const now = new Date();
        if (this.selectedStatus === 'active') {
            filtered = filtered.filter(c => c.active && new Date(c.validTo) >= now);
        } else if (this.selectedStatus === 'expired') {
            filtered = filtered.filter(c => new Date(c.validTo) < now);
        } else if (this.selectedStatus === 'inactive') {
            filtered = filtered.filter(c => !c.active);
        } else if (this.selectedStatus === 'exhausted') {
            filtered = filtered.filter(c => c.usageLimit && c.usedCount >= c.usageLimit);
        }

        this.filteredCoupons = filtered;
        this.totalPages = Math.max(1, Math.ceil(filtered.length / this.pageSize));
        this.currentPage = 1;
    }

    onSearch(): void {
        this.applyFilters();
    }

    onStatusChange(): void {
        this.applyFilters();
    }

    get paginatedCoupons(): Coupon[] {
        const startIndex = (this.currentPage - 1) * this.pageSize;
        return this.filteredCoupons.slice(startIndex, startIndex + this.pageSize);
    }

    changePage(page: number): void {
        if (page >= 1 && page <= this.totalPages) {
            this.currentPage = page;
        }
    }

    requestDelete(id: string): void {
        this.pendingDeleteId = id;
        this.actionError = null;
    }

    cancelDelete(): void {
        this.pendingDeleteId = null;
    }

    confirmDelete(id: string): void {
        this.couponService.deleteCoupon(id).subscribe({
            next: () => {
                this.pendingDeleteId = null;
                this.loadCoupons();
                this.loadStats();
            },
            error: (err) => {
                this.actionError = err.error?.message || 'Failed to delete coupon';
                this.pendingDeleteId = null;
            }
        });
    }

    toggleStatus(coupon: Coupon): void {
        const nextStatus = coupon.active ? 'inactive' : 'active';
        this.couponService.toggleCouponStatus(coupon._id!, nextStatus).subscribe({
            next: (updatedCoupon) => {
                coupon.active = updatedCoupon.active;
                this.loadStats();
            },
            error: (err) => {
                this.actionError = err.error?.message || 'Failed to update coupon status';
            }
        });
    }

    formatDate(date: string): string {
        if (!date) return 'N/A';
        return new Date(date).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    }

    formatDiscount(type: string, value: number): string {
        return type === 'percentage' ? `${value}%` : `$${value.toFixed(2)}`;
    }

    getCouponStatus(coupon: Coupon): string {
        const now = new Date();
        const validTo = new Date(coupon.validTo);
        const validFrom = new Date(coupon.validFrom);

        if (!coupon.active) return 'inactive';
        if (now < validFrom) return 'scheduled';
        if (now > validTo) return 'expired';
        if (coupon.usageLimit && coupon.usedCount >= coupon.usageLimit) return 'exhausted';
        return 'active';
    }
}
