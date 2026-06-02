import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminReview {
    _id: string;
    user: { _id: string; name: string; email: string } | null;
    product: { _id: string; Name: string } | null;
    rating: number;
    title: string;
    comment: string;
    images: string[];
    verifiedPurchase: boolean;
    createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AdminReviewService {
    private http = inject(HttpClient);
    private url = `${environment.apiUrl}/admin`;

    getAllReviews(): Observable<AdminReview[]> {
        return this.http.get<AdminReview[]>(`${this.url}/reviews`);
    }

    deleteReview(id: string): Observable<any> {
        return this.http.delete(`${this.url}/reviews/${id}`);
    }
}
