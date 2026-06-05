import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { DashboardService } from '../../core/services/dashboard.service';

interface StatCard {
    label: string;
    value: number;
    sub?: string;
    kind?: 'money' | 'number';
}

@Component({
    selector: 'app-analytics',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './analytics.component.html',
    styleUrls: ['./analytics.component.css']
})
export class AnalyticsComponent implements OnInit {
    loading = true;
    error: string | null = null;

    // Dashboard stats (từ GET /admin/dashboard/stats)
    stats: any = null;
    statCards: StatCard[] = [];

    // Revenue data (từ GET /admin/dashboard/revenue)
    revenueData: Array<{ _id: string; revenue: number; count: number }> = [];
    revenueLoading = false;
    maxRevenue = 0; // để render relative bar chart

    // Top products (từ GET /admin/dashboard/top-products)
    topProducts: any[] = [];

    constructor(private dashboardService: DashboardService) { }

    ngOnInit(): void {
        this.loadStats();
        this.loadRevenue();
        this.loadTopProducts();
    }

    loadStats(): void {
        this.loading = true;
        this.dashboardService.getStats().subscribe({
            next: (data: any) => {
                this.stats = data;
                this.buildStatCards(data);
                this.loading = false;
            },
            error: () => {
                this.error = 'Failed to load analytics data';
                this.loading = false;
            }
        });
    }

    buildStatCards(data: any): void {
        this.statCards = [
            {
                label: 'Total Revenue',
                value: Number(data.totalRevenue ?? 0),
                sub: 'All time',
                kind: 'money'
            },
            {
                label: 'Total Orders',
                value: Number(Object.values(data.ordersByStatus ?? {}).reduce((a: any, b: any) => a + b, 0)),
                sub: `Pending: ${data.ordersByStatus?.Pending ?? 0}`,
                kind: 'number'
            },
            {
                label: 'New Customers',
                value: Number(data.newCustomersCount ?? 0),
                sub: 'Recent period',
                kind: 'number'
            },
            {
                label: 'Delivered',
                value: Number(data.ordersByStatus?.Delivered ?? 0),
                sub: `Cancelled: ${data.ordersByStatus?.Cancelled ?? 0}`,
                kind: 'number'
            }
        ];
    }

    loadRevenue(): void {
        this.revenueLoading = true;
        this.dashboardService.getRevenueChart().subscribe({
            next: (data: any) => {
                this.revenueData = Array.isArray(data) ? data : [];
                this.maxRevenue = Math.max(...this.revenueData.map(d => d.revenue), 1);
                this.revenueLoading = false;
            },
            error: () => { this.revenueLoading = false; }
        });
    }

    loadTopProducts(): void {
        this.dashboardService.getTopProducts().subscribe({
            next: (data: any) => {
                this.topProducts = Array.isArray(data) ? data : (data?.topSellingProducts ?? []);
            },
            error: () => { }
        });
    }

    getBarWidth(revenue: number): string {
        return `${Math.round((revenue / this.maxRevenue) * 100)}%`;
    }

    // Order status breakdown cho pie-style display
    getOrderStatusKeys(): string[] {
        return Object.keys(this.stats?.ordersByStatus ?? {});
    }

    getOrderStatus(key: string): number {
        return this.stats?.ordersByStatus?.[key] ?? 0;
    }

    getTotalOrders(): number {
        return Object.values(this.stats?.ordersByStatus ?? {}).reduce((a: any, b: any) => a + b, 0) as number;
    }

    getStatusPercent(key: string): string {
        const total = this.getTotalOrders();
        if (!total) return '0%';
        return `${Math.round((this.getOrderStatus(key) / total) * 100)}%`;
    }

    statusColor: { [key: string]: string } = {
        Pending: '#f59e0b',
        Processing: '#3b82f6',
        Shipped: '#8b5cf6',
        Delivered: '#10b981',
        Cancelled: '#ef4444'
    };
}
