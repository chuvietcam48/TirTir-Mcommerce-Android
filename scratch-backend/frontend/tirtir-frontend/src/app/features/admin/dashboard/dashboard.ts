import { Component, OnInit, OnDestroy, inject, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Chart, registerables } from 'chart.js';
import {
    AdminDashboardService,
    DashboardStats,
    RevenueChartData,
    RecentOrder,
    InventoryAlerts,
    OrderStatsMap,
} from '../services/admin-dashboard.service';

Chart.register(...registerables);

@Component({
    selector: 'app-admin-dashboard',
    standalone: true,
    imports: [CommonModule, RouterLink],
    templateUrl: './dashboard.html',
    styleUrl: './dashboard.css',
})
export class AdminDashboardComponent implements OnInit, AfterViewInit, OnDestroy {
    @ViewChild('revenueCanvas') revenueCanvas!: ElementRef<HTMLCanvasElement>;
    @ViewChild('ordersCanvas') ordersCanvas!: ElementRef<HTMLCanvasElement>;

    private dashboardService = inject(AdminDashboardService);
    private revenueChart: Chart | null = null;
    private ordersChart: Chart | null = null;

    // Data
    stats: DashboardStats | null = null;
    recentOrders: RecentOrder[] = [];
    lowStockItems: InventoryAlerts['lowStock']['items'] = [];
    orderStats: OrderStatsMap = {};

    // UI state
    loading = true;
    error: string | null = null;

    ngOnInit(): void {
        this.loadDashboardData();
    }

    ngAfterViewInit(): void {
        // Charts are created after data loads in loadDashboardData
    }

    ngOnDestroy(): void {
        this.revenueChart?.destroy();
        this.ordersChart?.destroy();
    }

    loadDashboardData(): void {
        this.loading = true;
        this.error = null;

        // Load KPI stats
        this.dashboardService.getStats().subscribe({
            next: (data) => (this.stats = data),
            error: (err) => console.error('Stats error:', err),
        });

        // Load revenue chart
        this.dashboardService.getRevenueChart().subscribe({
            next: (data) => this.buildRevenueChart(data),
            error: (err) => console.error('Revenue chart error:', err),
        });

        // Load order stats for donut chart
        this.dashboardService.getOrderStats().subscribe({
            next: (data) => {
                this.orderStats = data;
                this.buildOrdersChart(data);
            },
            error: (err) => console.error('Order stats error:', err),
        });

        // Load recent orders
        this.dashboardService.getRecentOrders(5).subscribe({
            next: (data) => (this.recentOrders = data),
            error: (err) => console.error('Recent orders error:', err),
        });

        // Load inventory alerts
        this.dashboardService.getInventoryAlerts().subscribe({
            next: (data) => {
                this.lowStockItems = data.lowStock.items;
                this.loading = false;
            },
            error: (err) => {
                console.error('Inventory alerts error:', err);
                this.loading = false;
            },
        });
    }

    /* ── Chart Builders ──────────────────────────────────────── */

    private buildRevenueChart(data: RevenueChartData): void {
        // Wait for ViewChild to be available
        setTimeout(() => {
            if (!this.revenueCanvas?.nativeElement) return;
            this.revenueChart?.destroy();

            const ctx = this.revenueCanvas.nativeElement.getContext('2d');
            if (!ctx) return;

            this.revenueChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: data.labels,
                    datasets: [
                        {
                            label: 'Revenue (₫)',
                            data: data.data,
                            borderColor: '#C8102E',
                            backgroundColor: 'rgba(200, 16, 46, 0.08)',
                            fill: true,
                            tension: 0.3,
                            pointRadius: 3,
                            pointBackgroundColor: '#C8102E',
                        },
                    ],
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: false },
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            ticks: {
                                callback: (value: any) => '₫' + Number(value).toLocaleString(),
                                font: { size: 11 },
                            },
                            grid: { color: 'rgba(0,0,0,0.04)' },
                        },
                        x: {
                            ticks: { font: { size: 11 } },
                            grid: { display: false },
                        },
                    },
                },
            });
        }, 100);
    }

    private buildOrdersChart(data: OrderStatsMap): void {
        setTimeout(() => {
            if (!this.ordersCanvas?.nativeElement) return;
            this.ordersChart?.destroy();

            const ctx = this.ordersCanvas.nativeElement.getContext('2d');
            if (!ctx) return;

            const statusColors: Record<string, string> = {
                Pending: '#F57C00',
                Processing: '#1565C0',
                Shipped: '#7B1FA2',
                Delivered: '#2E7D32',
                Cancelled: '#D32F2F',
            };

            const labels = Object.keys(data);
            const values = Object.values(data);
            const colors = labels.map((l) => statusColors[l] || '#999');

            this.ordersChart = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels,
                    datasets: [{ data: values, backgroundColor: colors, borderWidth: 0 }],
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: '65%',
                    plugins: {
                        legend: {
                            position: 'bottom',
                            labels: { font: { size: 12 }, padding: 12, usePointStyle: true },
                        },
                    },
                },
            });
        }, 100);
    }

    /* ── Helpers ─────────────────────────────────────────────── */

    formatCurrency(value: number): string {
        return '₫' + value.toLocaleString('vi-VN');
    }

    getStatusClass(status: string): string {
        const map: Record<string, string> = {
            Pending: 'status-pending',
            Processing: 'status-processing',
            Shipped: 'status-shipped',
            Delivered: 'status-delivered',
            Cancelled: 'status-cancelled',
        };
        return map[status] || '';
    }

    get totalOrderCount(): number {
        return Object.values(this.orderStats).reduce((sum, v) => sum + v, 0);
    }
}
