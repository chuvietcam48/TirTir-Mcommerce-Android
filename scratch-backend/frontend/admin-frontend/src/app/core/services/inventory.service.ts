import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface InventoryStats {
    totalProducts: number;
    lowStockItems: number;
    outOfStockItems: number;
    totalValue: number;
}

export interface InventoryAlert {
    _id: string;
    Product_ID: string;
    Name: string;
    Stock_Quantity: number;
    Stock_Reserved?: number;
    Thumbnail_Images?: string;
    Category?: string;
}

export interface InventoryAlertGroup {
    count: number;
    items: InventoryAlert[];
}

export interface InventoryAlertsResponse {
    lowStock: InventoryAlertGroup;
    deadStock: InventoryAlertGroup;
}

export interface StockLog {
    _id: string;
    product: {
        _id: string;
        Name?: string;
        Product_ID?: string;
        // legacy aliases from old StockLog TS interface (used in stock-logs.html)
        name?: string;
        sku?: string;
    };
    action: 'Import' | 'Export' | 'Refund' | 'Adjust' | 'Sale' | 'Reserve' | 'Release' | string;
    change_type: 'Increase' | 'Decrease';
    source_id?: string;
    balance_before: number;
    balance_after: number;
    changeAmount: number;
    // Legacy aliases for stock-logs.html compatibility
    quantity?: number;     // maps to changeAmount
    newStock?: number;     // maps to balance_after
    previousStock?: number; // maps to balance_before
    user?: { name?: string; email?: string; };  // maps to performedBy
    reason: string;
    performedBy?: {
        _id: string;
        name: string;
        email: string;
    };
    createdAt: string;
}

export interface StockAdjustment {
    productId: string;
    // New format (used by inventory-dashboard)
    newStock?: number;
    reason?: string;
    // Legacy format (used by low-stock-alerts)
    action?: 'add' | 'remove' | 'set';
    quantity?: number;
}

@Injectable({
    providedIn: 'root'
})
export class InventoryService {
    private apiUrl = `${environment.apiUrl}/inventory`;

    constructor(private http: HttpClient) { }

    getInventoryStats(): Observable<InventoryStats> {
        return this.http.get<InventoryStats>(`${this.apiUrl}/stats`);
    }

    /** Returns the full alerts object from backend: { lowStock: { count, items[] }, deadStock: {...} } */
    getInventoryAlerts(): Observable<InventoryAlertsResponse> {
        return this.http.get<InventoryAlertsResponse>(`${this.apiUrl}/alerts`);
    }

    getStockLogs(filters?: any): Observable<StockLog[]> {
        let params = new HttpParams();
        if (filters) {
            Object.keys(filters).forEach(key => {
                if (filters[key]) {
                    params = params.set(key, filters[key]);
                }
            });
        }
        return this.http.get<StockLog[]>(`${this.apiUrl}/logs`, { params });
    }

    /** Adjust stock by setting newStock directly (matches backend PATCH /adjust) */
    adjustStock(data: StockAdjustment): Observable<any> {
        return this.http.patch<any>(`${this.apiUrl}/adjust`, data);
    }
}
