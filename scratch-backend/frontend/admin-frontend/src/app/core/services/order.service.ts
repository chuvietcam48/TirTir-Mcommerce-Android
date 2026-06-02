import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * OrderService — maps to backend:
 *   GET  /api/v1/admin/orders      (admin list, requires auth+admin)
 *   GET  /api/v1/orders/:id        (detail, requires auth)
 *   PUT  /api/v1/orders/update-status  (requires auth+admin/cs/inventory)
 */

export interface OrderItem {
    Product: {
        _id: string;
        Product_Name: string;
        Product_ID: string;
        Thumbnail_Images: string | string[];
    };
    Quantity: number;
    Price: number;
}

export interface Order {
    _id: string;
    user: {
        _id: string;
        name: string;
        email: string;
    };
    Order_Items?: OrderItem[];
    items?: OrderItem[];
    totalAmount?: number;
    Total_Price?: number;
    shippingCost?: number;
    status: string;
    paymentMethod?: string;
    paymentStatus?: string;
    ghnOrderCode?: string;
    carrier?: string;
    trackingNumber?: string;
    isPacked?: boolean;
    shippingAddress: {
        fullName: string;
        phone: string;
        address: string;
        city: string;
        district?: string;
        province?: string;
    };
    status_history?: StatusHistory[];
    statusHistory?: StatusHistory[];   // backend returns camelCase
    createdAt: string;
    updatedAt: string;
}

export interface StatusHistory {
    status: string;
    timestamp: string;
    updated_by?: string;
    note?: string;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
    private adminOrdersUrl = `${environment.apiUrl}/admin/orders`;
    private ordersUrl = `${environment.apiUrl}/orders`;

    constructor(private http: HttpClient) { }

    /**
     * GET /api/v1/admin/orders
     * Response: { orders: [...], page, pages, total }
     */
    getAllOrders(page = 1, status?: string): Observable<any> {
        let params = new HttpParams().set('page', page.toString());
        if (status) params = params.set('status', status);
        return this.http.get<any>(this.adminOrdersUrl, { params });
    }

    /**
     * GET /api/v1/orders/:id
     * Response: Order object
     */
    getOrderById(id: string): Observable<any> {
        return this.http.get<any>(`${this.ordersUrl}/${id}`);
    }

    /**
     * PUT /api/v1/orders/update-status
     * Body: { orderId, status }
     * Status enum: Pending | Processing | Shipped | Delivered | Cancelled
     */
    updateOrderStatus(orderId: string, status: string, note?: string): Observable<any> {
        return this.http.put<any>(`${environment.apiUrl}/orders/update-status`, { orderId, status, note });
    }

    /** GET /api/v1/orders/:id/tracking */
    getOrderTracking(id: string): Observable<any> {
        return this.http.get<any>(`${this.ordersUrl}/${id}/tracking`);
    }

    /** GET /api/v1/admin/orders/stats */
    getOrderStats(): Observable<any> {
        return this.http.get<any>(`${this.adminOrdersUrl}/stats`);
    }

    cancelOrder(orderId: string): Observable<any> {
        return this.http.post(`${environment.apiUrl}/orders/${orderId}/cancel`, {});
    }

    updateFulfillment(orderId: string, data: { carrier?: string; trackingNumber?: string; isPacked?: boolean }): Observable<any> {
        return this.http.put<any>(`${this.ordersUrl}/${orderId}/fulfillment`, data);
    }
}
