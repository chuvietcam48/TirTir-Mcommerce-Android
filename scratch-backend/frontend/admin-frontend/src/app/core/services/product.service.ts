import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, of } from 'rxjs';
import { tap, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class ProductService {
    private apiUrl = `${environment.apiUrl}`;
    
    // Client-side cache for instant loading UI
    private productsCache = new BehaviorSubject<any[] | null>(null);

    constructor(private http: HttpClient) { }

    getAllProducts(forceRefresh = false): Observable<any> {
        if (!forceRefresh && this.productsCache.value) {
            return of(this.productsCache.value);
        }

        return this.http.get(`${this.apiUrl}/products?limit=1000&page=1`).pipe(
            tap((data: any) => {
                const items = Array.isArray(data) ? data : (data.products || data.data || []);
                this.productsCache.next(items);
            })
        );
    }

    getProductById(id: string): Observable<any> {
        return this.http.get(`${this.apiUrl}/products/${id}?_t=${Date.now()}`).pipe(
            map((res: any) => res.data || res.product || res)
        );
    }

    createProduct(productData: any): Observable<any> {
        return this.http.post(`${this.apiUrl}/admin/products`, productData).pipe(
            tap((res: any) => {
                const current = this.productsCache.value;
                if (current) {
                    const newProd = res.product || res.data || res;
                    this.productsCache.next([newProd, ...current]);
                }
            })
        );
    }

    updateProduct(id: string, productData: any): Observable<any> {
        return this.http.put(`${this.apiUrl}/admin/products/${id}`, productData).pipe(
            tap((res: any) => {
                const current = this.productsCache.value;
                if (current) {
                    const updatedProd = res.product || res.data || res;
                    const idx = current.findIndex(p => p._id === id || p.id === id);
                    if (idx !== -1) {
                        const newArr = [...current];
                        newArr[idx] = { ...newArr[idx], ...updatedProd };
                        this.productsCache.next(newArr);
                    }
                }
            })
        );
    }

    deleteProduct(id: string): Observable<any> {
        return this.http.delete(`${this.apiUrl}/admin/products/${id}`).pipe(
            tap(() => {
                const current = this.productsCache.value;
                if (current) {
                    this.productsCache.next(current.filter(p => p._id !== id && p.id !== id));
                }
            })
        );
    }

    updateStock(id: string, stockData: any): Observable<any> {
        return this.http.patch(`${this.apiUrl}/admin/products/${id}/stock`, stockData).pipe(
            tap((res: any) => {
                // Keep the stock in sync with the cache
                const current = this.productsCache.value;
                if (current) {
                    const updatedProd = res.product || res.data || res;
                    const idx = current.findIndex(p => p._id === id || p.id === id);
                    if (idx !== -1 && updatedProd) {
                        const newArr = [...current];
                        newArr[idx] = { ...newArr[idx], Stock_Quantity: updatedProd.Stock_Quantity };
                        this.productsCache.next(newArr);
                    }
                }
            })
        );
    }

    getLowStockProducts(): Observable<any> {
        return this.http.get(`${this.apiUrl}/products/low-stock`);
    }

    // Aliases for consistency
    getProduct(id: string): Observable<any> {
        return this.getProductById(id);
    }

    addProduct(productData: any): Observable<any> {
        return this.createProduct(productData);
    }

    getStockHistory(id: string): Observable<any> {
        return this.http.get(`${this.apiUrl}/products/${id}/stock-history`);
    }

    uploadImage(file: File): Observable<any> {
        const formData = new FormData();
        formData.append('image', file);
        return this.http.post(`${this.apiUrl}/upload/product`, formData);
    }

    // --- Smart Admin API ---
    suggestParent(productName: string, category: string): Observable<any> {
        return this.http.get(`${this.apiUrl}/admin/smart/suggest-parent?productName=${encodeURIComponent(productName)}&category=${encodeURIComponent(category)}`);
    }

    generateSmartIds(productName: string, category: string, parentID: string): Observable<any> {
        return this.http.post(`${this.apiUrl}/admin/smart/generate-ids`, { productName, category, parentID });
    }

    extractColorFromImage(file: File): Observable<any> {
        const formData = new FormData();
        formData.append('image', file);
        return this.http.post(`${this.apiUrl}/admin/smart/extract-color`, formData);
    }

    extractColorFromHex(hex: string): Observable<any> {
        return this.http.post(`${this.apiUrl}/admin/smart/extract-color/hex`, { hex });
    }
}
