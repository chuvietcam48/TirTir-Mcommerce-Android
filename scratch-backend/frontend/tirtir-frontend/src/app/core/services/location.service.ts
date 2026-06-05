import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, catchError, of } from 'rxjs';

export interface LocationItem {
    id: string;
    name: string;
    name_en: string;
    full_name: string;
    full_name_en: string;
    latitude?: string;
    longitude?: string;
}

export interface LocationResponse {
    error: number;
    error_text: string;
    data_name: string;
    data: LocationItem[];
}

@Injectable({
    providedIn: 'root'
})
export class LocationService {
    private http = inject(HttpClient);
    private baseUrl = 'https://esgoo.net/api-tinhthanh';

    /**
     * Get all Provinces / Cities
     */
    getProvinces(): Observable<LocationItem[]> {
        return this.http.get<LocationResponse>(`${this.baseUrl}/1/0.htm`).pipe(
            map(response => response.error === 0 ? response.data : []),
            catchError(err => {
                console.error('Failed to fetch provinces', err);
                return of([]);
            })
        );
    }

    /**
     * Get Districts by Province ID
     */
    getDistricts(provinceId: string): Observable<LocationItem[]> {
        if (!provinceId) return of([]);
        return this.http.get<LocationResponse>(`${this.baseUrl}/2/${provinceId}.htm`).pipe(
            map(response => response.error === 0 ? response.data : []),
            catchError(err => {
                console.error('Failed to fetch districts', err);
                return of([]);
            })
        );
    }

    /**
     * Get Wards by District ID
     */
    getWards(districtId: string): Observable<LocationItem[]> {
        if (!districtId) return of([]);
        return this.http.get<LocationResponse>(`${this.baseUrl}/3/${districtId}.htm`).pipe(
            map(response => response.error === 0 ? response.data : []),
            catchError(err => {
                console.error('Failed to fetch wards', err);
                return of([]);
            })
        );
    }
}
