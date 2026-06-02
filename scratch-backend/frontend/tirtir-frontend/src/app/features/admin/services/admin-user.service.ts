import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminUser {
    _id: string;
    name: string;
    email: string;
    role: string;
    isBlocked: boolean;
    isEmailVerified: boolean;
    phone?: string;
    gender?: string;
    createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AdminUserService {
    private http = inject(HttpClient);
    private url = `${environment.apiUrl}/admin/users`;

    getUsers(params?: { page?: number; limit?: number; search?: string }): Observable<any> {
        let p = new HttpParams();
        if (params) Object.entries(params).forEach(([k, v]) => { if (v) p = p.set(k, String(v)); });
        return this.http.get(this.url, { params: p });
    }

    getUserDetail(id: string): Observable<any> {
        return this.http.get(`${this.url}/${id}`);
    }

    getUserOrders(id: string): Observable<any> {
        return this.http.get(`${this.url}/${id}/orders`);
    }

    updateUserStatus(id: string, isBlocked: boolean): Observable<any> {
        return this.http.put(`${this.url}/${id}/status`, { isBlocked });
    }

    updateUserRole(id: string, role: string): Observable<any> {
        return this.http.put(`${this.url}/${id}/role`, { role });
    }

    deleteUser(id: string): Observable<any> {
        return this.http.delete(`${this.url}/${id}`);
    }

    getAdmins(): Observable<AdminUser[]> {
        return this.http.get<AdminUser[]>(`${this.url}/admins`);
    }

    createAdmin(data: { name: string; email: string; password: string; role: string }): Observable<any> {
        return this.http.post(`${this.url}/admin`, data);
    }
}
