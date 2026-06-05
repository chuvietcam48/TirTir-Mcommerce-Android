import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ReviewProduct {
    _id: string;
    Name?: string;
    Thumbnail_Images?: string;
    Product_Slug?: string;
    slug?: string;
}

export interface Review {
    _id: string;
    user: { name?: string; email?: string };
    product?: ReviewProduct;
    product_id?: ReviewProduct;
    rating: number;
    comment: string;
    title?: string;
    createdAt: string;
    status?: string;
}

@Injectable({
    providedIn: 'root'
})
export class ReviewService {
    private apiUrl = `${environment.apiUrl}/reviews`;

    constructor(private http: HttpClient) { }

    getAllReviews(): Observable<any> {
        return this.http.get<any>(this.apiUrl);
    }

    getReviewById(id: string): Observable<any> {
        return this.http.get<any>(`${this.apiUrl}/${id}`);
    }

    deleteReview(id: string): Observable<any> {
        return this.http.delete(`${environment.apiUrl}/admin/reviews/${id}`);
    }
}
