import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { DashboardService, GeneralOverviewResponse, OverviewRevenuePoint, OverviewTrafficPoint, TopProduct } from '../../core/services/dashboard.service';
import { environment } from '../../../environments/environment';

@Component({
    selector: 'app-dashboard',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
    overview: GeneralOverviewResponse | null = null;
    overviewLoading = true;
    overviewError: string | null = null;

    // Derived view models for charts/tables
    topProducts: TopProduct[] = [];
    recentOrders: any[] = [];
    lowStockItems: any[] = [];

    constructor(
        private dashboardService: DashboardService,
        private router: Router
    ) { }

    get showCartRecovery(): boolean {
        return false; // Removed from General per requirements
    }

    ngOnInit(): void {
        this.loadOverview();
    }

    loadOverview(): void {
        this.overviewLoading = true;
        this.overviewError = null;

        // Default range for General overview: last 30 days
        this.dashboardService.getOverview('30d').subscribe({
            next: (data) => {
                this.overview = data;
                this.topProducts = Array.isArray(data?.topProducts) ? data.topProducts.slice(0, 10) : [];
                this.recentOrders = Array.isArray(data?.recentOrders) ? data.recentOrders.slice(0, 5) : [];
                this.lowStockItems = Array.isArray(data?.lowStockAlerts) ? data.lowStockAlerts.slice(0, 5) : [];
                this.overviewLoading = false;
            },
            error: (err: any) => {
                console.error('General overview load error:', err);
                this.overviewError = 'Failed to load overview';
                this.overviewLoading = false;
            }
        });
    }

    // ── Helpers ─────────────────────────────────────────────────

    formatDate(date: string): string {
        return new Date(date).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
    }

    getStatusClass(status: string): string {
        const map: { [k: string]: string } = {
            Pending: 'badge-pending',
            Processing: 'badge-processing',
            Shipped: 'badge-shipped',
            Delivered: 'badge-delivered',
            Cancelled: 'badge-cancelled'
        };
        return map[status] ?? 'badge-default';
    }

    /** Trả về URL ảnh đầu tiên, dù Thumbnail_Images là string hay string[] */
    getThumb(img: string | string[] | undefined): string {
        if (!img) return '';
        if (Array.isArray(img)) return img[0] ?? '';
        return img;
    }

    /** Fix relative backend asset URLs (e.g. "assets/images/...") for admin app origin. */
    fixAssetUrl(url: string | undefined | null): string {
        if (!url) return '';
        if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) return url;
        const backendBase = environment.apiUrl.replace('/api/v1', '');
        const clean = url.startsWith('/') ? url.slice(1) : url;
        return `${backendBase}/${clean}`;
    }

    // ── Click-through navigation ────────────────────────────────

    goToProducts(): void {
        this.router.navigate(['/products']);
    }

    goToOrders(): void {
        this.router.navigate(['/orders']);
    }

    goToInventoryLowStock(): void {
        this.router.navigate(['/inventory'], { queryParams: { filter: 'low-stock' } });
    }
}
