import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AdminInventoryService {
    private http = inject(HttpClient);
    private url = `${environment.apiUrl}/inventory`;

    getStats(): Observable<any> {
        return this.http.get(`${this.url}/stats`);
    }

    getAlerts(threshold = 10): Observable<any> {
        return this.http.get(`${this.url}/alerts`, { params: new HttpParams().set('threshold', threshold) });
    }

    getLogs(params?: { page?: number; limit?: number }): Observable<any> {
        let p = new HttpParams();
        if (params) Object.entries(params).forEach(([k, v]) => { if (v) p = p.set(k, String(v)); });
        return this.http.get(`${this.url}/logs`, { params: p });
    }

    adjustStock(productId: string, quantity: number, reason: string): Observable<any> {
        return this.http.patch(`${this.url}/adjust`, { productId, quantity, reason });
    }

    cleanupPending(): Observable<any> {
        return this.http.post(`${this.url}/cleanup`, {});
    }
}
