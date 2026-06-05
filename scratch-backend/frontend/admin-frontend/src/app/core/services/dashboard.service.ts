import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * BE response từ GET /admin/dashboard/stats:
 * {
 *   totalRevenue: number,
 *   ordersByStatus: { Pending: n, Processing: n, Shipped: n, Delivered: n, Cancelled: n },
 *   newCustomersCount: number,
 *   topSellingProducts: [...],
 *   salesByCategory: [...]
 * }
 */
export interface DashboardStats {
    totalRevenue: number;
    ordersByStatus: {
        Pending?: number;
        Processing?: number;
        Shipped?: number;
        Delivered?: number;
        Cancelled?: number;
        [key: string]: number | undefined;
    };
    newCustomersCount: number;
    topSellingProducts?: any[];
    salesByCategory?: any[];
}

/**
 * BE response từ GET /admin/dashboard/revenue:
 * Array: [{ _id: "2024-02-10", revenue: 500000, count: 2 }, ...]
 */
export interface RevenuePoint {
    _id: string;
    revenue: number;
    count: number;
}

export interface TopProduct {
    product?: {
        _id?: string;
        name?: string;
        sku?: string;
        mainImage?: string;
    };
    salesCount?: number;
    revenue?: number;
    totalRevenue?: number;
    // Flat fallback fields (for backward compat)
    _id?: string;
    Name?: string;
    Product_Name?: string;
    Thumbnail_Images?: string | string[];
    totalSold?: number;
}

export interface OverviewRange {
    range: string;
    from: string;
    to: string;
}

export interface OverviewRevenuePoint {
    date: string; // YYYY-MM-DD
    revenue: number;
    orderCount: number;
}

export interface OverviewTrafficPoint {
    date: string; // YYYY-MM-DD
    views: number;
}

export interface GeneralOverviewResponse {
    range: OverviewRange;
    summary: {
        totalRevenue: number;
        totalOrders: number;
        deliveredOrders: number;
        newCustomers: number;
        averageOrderValue: number;
        lowStockCount: number;
        websiteViews: number;
    };
    orderStatusBreakdown: Record<string, number>;
    revenueSeries: OverviewRevenuePoint[];
    traffic: {
        viewsTotal: number;
        viewsSeries: OverviewTrafficPoint[];
    };
    customerGrowthSeries: { month: string; count: number }[];
    topProducts: TopProduct[];
    recentOrders: any[];
    lowStockAlerts: any[];
    cartRecovery?: {
        totalAbandoned: number;
        recoveredCount: number;
        recoveredRevenue: number;
        conversionRate: number;
    };
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
    private adminUrl = `${environment.apiUrl}/admin`;

    constructor(private http: HttpClient) { }

    /** GET /api/v1/admin/dashboard/stats */
    getStats(): Observable<DashboardStats> {
        return this.http.get<DashboardStats>(`${this.adminUrl}/dashboard/stats`);
    }

    /** GET /api/v1/admin/dashboard/overview?range=30d */
    getOverview(range: 'today' | '7d' | '30d' | '90d' | 'custom' = '30d', from?: string, to?: string): Observable<GeneralOverviewResponse> {
        let params = new HttpParams().set('range', range);
        if (from) params = params.set('from', from);
        if (to) params = params.set('to', to);
        return this.http.get<GeneralOverviewResponse>(`${this.adminUrl}/dashboard/overview`, { params });
    }

    /** GET /api/v1/admin/dashboard/revenue?startDate=&endDate= */
    getRevenueChart(startDate?: string, endDate?: string): Observable<RevenuePoint[]> {
        let params = new HttpParams();
        if (startDate) params = params.set('startDate', startDate);
        if (endDate) params = params.set('endDate', endDate);
        return this.http.get<RevenuePoint[]>(`${this.adminUrl}/dashboard/revenue`, { params });
    }

    /** GET /api/v1/admin/dashboard/top-products */
    getTopProducts(): Observable<TopProduct[]> {
        return this.http.get<TopProduct[]>(`${this.adminUrl}/dashboard/top-products`);
    }

    /** GET /api/v1/admin/orders — list for dashboard widget */
    getAllOrders(page = 1, status?: string): Observable<any> {
        let params = new HttpParams().set('page', page.toString());
        if (status) params = params.set('status', status);
        return this.http.get<any>(`${this.adminUrl}/orders`, { params });
    }

    /** GET /api/v1/inventory/alerts */
    getLowStockAlerts(threshold = 10): Observable<any> {
        const params = new HttpParams().set('threshold', threshold.toString());
        return this.http.get<any>(`${environment.apiUrl}/inventory/alerts`, { params });
    }

    /** GET /api/v1/admin/cart-recovery */
    getCartRecoveryStats(): Observable<any> {
        return this.http.get<any>(`${this.adminUrl}/stats/cart-recovery`);
    }
}
