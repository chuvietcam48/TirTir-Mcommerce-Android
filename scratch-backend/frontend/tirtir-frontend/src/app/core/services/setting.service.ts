import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, map } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PublicSettings {
    bannerUrl?: string;
    shippingFee?: number;
    freeShippingThreshold?: number;
    contactPhone?: string;
    contactEmail?: string;
    socialLinks?: any;
}

@Injectable({
    providedIn: 'root'
})
export class SettingService {
    private apiUrl = `${environment.apiUrl}/settings`;
    private http = inject(HttpClient);

    getPublicSettings(): Observable<PublicSettings> {
        return this.http.get<PublicSettings>(`${this.apiUrl}/public`).pipe(
            catchError(error => {
                console.error('Failed to fetch public settings:', error);
                return of({});
            })
        );
    }

    getFreeShippingThreshold(): Observable<number> {
        return this.getPublicSettings().pipe(
            map(settings => settings.freeShippingThreshold || 400000) // Default to 400k if not set
        );
    }

    // Admin Setting update (Admin needs auth)
    updateSettings(updates: Partial<PublicSettings>): Observable<any> {
        return this.http.put(`${this.apiUrl}`, updates);
    }
}
