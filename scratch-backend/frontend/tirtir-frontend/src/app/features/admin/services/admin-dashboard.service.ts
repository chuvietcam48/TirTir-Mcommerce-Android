import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

/* ── Response Interfaces ──────────────────────────────────── */

export interface DashboardStats {
    totalOrders: number;
    totalRevenue: number;
    totalCustomers: number;
    lowStockProducts: number;
}

export interface RevenueChartData {
    labels: string[];
    data: number[];
}

export interface OrderStatsMap {
    [status: string]: number; // e.g. { Pending: 5, Processing: 2 }
}

export interface RecentOrder {
    _id: string;
    user: { _id: string; name: string; email: string } | null;
    items: { name: string; quantity: number; price: number; image?: string }[];
    totalAmount: number;
    status: string;
    paymentMethod: string;
    createdAt: string;
}

export interface LowStockItem {
    Product_ID: string;
    Name: string;
    Stock_Quantity: number;
    Stock_Reserved: number;
    Thumbnail_Images: string;
    Category: string;
}

export interface InventoryAlerts {
    lowStock: { count: number; items: LowStockItem[] };
    deadStock: { count: number; items: any[] };
}

export interface CustomerStats {
    totalCustomers: number;
    newCustomersLast30Days: number;
    customerGrowth: { _id: string; count: number }[];
}

/* ── Service ──────────────────────────────────────────────── */

@Injectable({ providedIn: 'root' })
export class AdminDashboardService {
    private http = inject(HttpClient);
    private adminUrl = `${environment.apiUrl}/admin`;
    private inventoryUrl = `${environment.apiUrl}/inventory`;

    /** KPI summary stats */
    getStats(): Observable<DashboardStats> {
        return this.http.get<DashboardStats>(`${this.adminUrl}/dashboard/stats`);
    }

    /** Revenue chart (line chart data) */
    getRevenueChart(startDate?: string, endDate?: string): Observable<RevenueChartData> {
        let params = new HttpParams();
        if (startDate) params = params.set('startDate', startDate);
        if (endDate) params = params.set('endDate', endDate);
        return this.http.get<RevenueChartData>(`${this.adminUrl}/dashboard/revenue`, { params });
    }

    /** Order counts grouped by status */
    getOrderStats(): Observable<OrderStatsMap> {
        return this.http.get<OrderStatsMap>(`${this.adminUrl}/orders/stats`);
    }

    /** Recent orders (no page param → returns simple array) */
    getRecentOrders(limit = 10): Observable<RecentOrder[]> {
        const params = new HttpParams().set('limit', limit.toString());
        return this.http.get<RecentOrder[]>(`${this.adminUrl}/orders`, { params });
    }

    /** Inventory alerts (low stock + dead stock) */
    getInventoryAlerts(threshold = 10): Observable<InventoryAlerts> {
        const params = new HttpParams().set('threshold', threshold.toString());
        return this.http.get<InventoryAlerts>(`${this.inventoryUrl}/alerts`, { params });
    }

    /** Customer analytics */
    getCustomerStats(): Observable<CustomerStats> {
        return this.http.get<CustomerStats>(`${this.adminUrl}/dashboard/customers`);
    }
}
