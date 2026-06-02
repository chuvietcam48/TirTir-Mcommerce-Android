import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminCoupon {
    _id: string;
    code: string;
    discountType: string;
    discountValue: number;
    minOrderValue: number;
    maxDiscount: number;
    validFrom: string;
    validTo: string;
    usageLimit: number;
    usedCount: number;
    active: boolean;
    applicableProducts: any[];
    createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AdminCouponService {
    private http = inject(HttpClient);
    private url = `${environment.apiUrl}/coupons`;

    getCoupons(): Observable<AdminCoupon[]> {
        return this.http.get<AdminCoupon[]>(this.url);
    }

    getCoupon(id: string): Observable<AdminCoupon> {
        return this.http.get<AdminCoupon>(`${this.url}/${id}`);
    }

    createCoupon(data: Partial<AdminCoupon>): Observable<any> {
        return this.http.post(this.url, data);
    }

    updateCoupon(id: string, data: Partial<AdminCoupon>): Observable<any> {
        return this.http.put(`${this.url}/${id}`, data);
    }

    deleteCoupon(id: string): Observable<any> {
        return this.http.delete(`${this.url}/${id}`);
    }

    toggleStatus(id: string): Observable<any> {
        return this.http.patch(`${this.url}/${id}/status`, {});
    }

    getStats(): Observable<any> {
        return this.http.get(`${this.url}/stats`);
    }
}
