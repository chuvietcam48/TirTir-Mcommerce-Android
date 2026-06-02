import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminOrderItem {
    product: any;
    name: string;
    price: number;
    quantity: number;
    shade?: string;
    image?: string;
}

export interface AdminOrder {
    _id: string;
    user: { _id: string; name: string; email: string } | null;
    items: AdminOrderItem[];
    shippingAddress: {
        fullName: string; phone: string; province: string;
        district: string; ward: string; address: string;
    };
    paymentMethod: string;
    status: string;
    totalAmount: number;
    ghnOrderCode?: string;
    createdAt: string;
    updatedAt: string;
}

export interface PaginatedOrders {
    orders: AdminOrder[];
    page: number;
    pages: number;
    total: number;
}

@Injectable({ providedIn: 'root' })
export class AdminOrderService {
    private http = inject(HttpClient);
    private adminUrl = `${environment.apiUrl}/admin`;
    private orderUrl = `${environment.apiUrl}/orders`;

    getOrders(params: { page?: number; limit?: number; status?: string; search?: string; startDate?: string; endDate?: string }): Observable<PaginatedOrders> {
        let p = new HttpParams();
        Object.entries(params).forEach(([k, v]) => { if (v) p = p.set(k, String(v)); });
        return this.http.get<PaginatedOrders>(`${this.adminUrl}/orders`, { params: p });
    }

    getOrderDetail(id: string): Observable<AdminOrder> {
        return this.http.get<AdminOrder>(`${this.orderUrl}/${id}`);
    }

    updateOrderStatus(orderId: string, status: string): Observable<any> {
        return this.http.put(`${this.orderUrl}/update-status`, { orderId, status });
    }
}
