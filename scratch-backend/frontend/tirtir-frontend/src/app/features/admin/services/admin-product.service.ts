import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

/* ── Interfaces ───────────────────────────────────────────── */

export interface AdminProduct {
    _id: string;
    Product_ID: string;
    Name: string;
    Price: number;
    Category: string;
    Category_Slug: string;
    slug: string;
    Status: string;
    Stock_Quantity: number;
    Stock_Reserved: number;
    Thumbnail_Images: string;
    Is_Best_Seller: boolean;
    Rating_Average: number;
    Rating_Count: number;
    Sold_Quantity: number;
    createdAt: string;
    updatedAt: string;
}

export interface ProductListResponse {
    data: AdminProduct[];
    total: number;
    categories: string[];
}

export interface ProductFormData {
    Name: string;
    Product_ID: string;
    Price: number;
    Category: string;
    Category_Slug: string;
    Description_Short?: string;
    Full_Description?: string;
    How_To_Use?: string;
    Status?: string;
    Stock_Quantity?: number;
    Is_Skincare?: boolean;
    Is_Best_Seller?: boolean;
    Skin_Type_Target?: string;
    Main_Concern?: string;
    Volume_Size?: string;
}

/* ── Service ──────────────────────────────────────────────── */

@Injectable({ providedIn: 'root' })
export class AdminProductService {
    private http = inject(HttpClient);
    private publicUrl = `${environment.apiUrl}/products`;
    private adminUrl = `${environment.apiUrl}/admin/products`;

    /** List products (uses public endpoint with admin-relevant fields) */
    getProducts(params?: {
        page?: number;
        limit?: number;
        search?: string;
        category?: string;
        sort?: string;
    }): Observable<ProductListResponse> {
        let httpParams = new HttpParams();
        if (params) {
            Object.entries(params).forEach(([key, val]) => {
                if (val !== null && val !== undefined && val !== '') {
                    httpParams = httpParams.set(key, String(val));
                }
            });
        }
        return this.http.get<ProductListResponse>(this.publicUrl, { params: httpParams });
    }

    /** Create product */
    createProduct(data: ProductFormData): Observable<any> {
        return this.http.post(this.adminUrl, data);
    }

    /** Update product */
    updateProduct(id: string, data: Partial<ProductFormData>): Observable<any> {
        return this.http.put(`${this.adminUrl}/${id}`, data);
    }

    /** Delete product */
    deleteProduct(id: string): Observable<any> {
        return this.http.delete(`${this.adminUrl}/${id}`);
    }

    /** Update stock */
    updateStock(id: string, quantity: number): Observable<any> {
        return this.http.patch(`${this.adminUrl}/${id}/stock`, { quantity });
    }

    /** Bulk import CSV */
    bulkImport(file: File): Observable<any> {
        const formData = new FormData();
        formData.append('file', file);
        return this.http.post(`${this.adminUrl}/bulk-import`, formData);
    }

    /** Get single product detail */
    getProductDetail(idOrSlug: string): Observable<any> {
        return this.http.get(`${this.publicUrl}/${idOrSlug}`);
    }
}
