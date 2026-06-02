import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Settings {
    _id?: string;
    bannerUrl: string;
    shippingFee: number;
    freeShippingThreshold: number;
    contactPhone: string;
    contactEmail: string;
    socialLinks: {
        facebook: string;
        instagram: string;
        tiktok: string;
    };
    bankInfo: {
        bankName: string;
        accountNumber: string;
        accountHolder: string;
    };
}

@Injectable({
    providedIn: 'root'
})
export class SettingService {
    private apiUrl = `${environment.apiUrl}/settings`;
    private uploadUrl = `${environment.apiUrl}/upload/banner`;

    constructor(private http: HttpClient) { }

    getSettings(): Observable<Settings> {
        return this.http.get<Settings>(this.apiUrl);
    }

    updateSettings(settings: Settings): Observable<any> {
        return this.http.put(this.apiUrl, settings);
    }

    uploadBanner(file: File): Observable<any> {
        const formData = new FormData();
        formData.append('banner', file);
        return this.http.post(this.uploadUrl, formData);
    }
}
