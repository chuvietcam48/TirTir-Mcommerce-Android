import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Matches the backend User sub-document schema */
export interface Address {
    _id?: string;
    fullName: string;
    phone: string;
    street: string;
    city: string;
    district: string;
    ward?: string;
    isDefault?: boolean;
}

/** Matches the backend User model returned by /admin/users */
export interface Customer {
    _id: string;
    name: string;
    email: string;
    phone?: string;
    role: string;
    createdAt: string;
    addresses?: Address[];
    isBlocked: boolean;
    isEmailVerified?: boolean;
    avatar?: string;
    gender?: string;
    birthDate?: string;
}

@Injectable({
    providedIn: 'root'
})
export class CustomerService {
    private apiUrl = `${environment.apiUrl}/admin/users`;

    constructor(private http: HttpClient) { }

    getAllCustomers(params?: any): Observable<any> {
        let httpParams = new HttpParams();
        if (params) {
            Object.keys(params).forEach(key => {
                if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
                    httpParams = httpParams.set(key, params[key]);
                }
            });
        }
        return this.http.get<any>(this.apiUrl, { params: httpParams });
    }

    getCustomerById(id: string): Observable<Customer> {
        return this.http.get<Customer>(`${this.apiUrl}/${id}`);
    }

    /** GET /api/v1/admin/users/:id/orders — returns real Order documents */
    getCustomerOrders(id: string): Observable<any[]> {
        return this.http.get<any[]>(`${this.apiUrl}/${id}/orders`);
    }

    /** PUT /api/v1/admin/users/:id/status — body: { isBlocked: boolean } */
    updateCustomerStatus(id: string, isBlocked: boolean): Observable<any> {
        return this.http.put<any>(`${this.apiUrl}/${id}/status`, { isBlocked });
    }
}
