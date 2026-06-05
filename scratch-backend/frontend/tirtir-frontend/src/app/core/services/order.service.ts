import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, throwError, tap } from 'rxjs';
import { Order, CreateOrderRequest, CreateOrderResponse } from '../models';
import { CartService } from './cart.service';
import { environment } from '../../../environments/environment';

@Injectable({
    providedIn: 'root',
})
export class OrderService {
    private apiUrl = `${environment.apiUrl}/orders`;
    private http = inject(HttpClient);
    private cartService = inject(CartService);

    /**
     * Create new order from cart
     */
    createOrder(orderData: CreateOrderRequest): Observable<CreateOrderResponse> {
        return this.http.post<CreateOrderResponse>(`${this.apiUrl}/create`, orderData).pipe(
            tap(() => {
                // Clear cart after successful order
                this.cartService.clearCart();
            }),
            catchError(this.handleError)
        );
    }

    /**
     * Get user's order history
     */
    getMyOrders(): Observable<Order[]> {
        return this.http.get<Order[]>(`${this.apiUrl}/my-orders`).pipe(catchError(this.handleError));
    }

    /**
     * Get specific order by ID
     */
    getOrderById(orderId: string): Observable<Order> {
        return this.http.get<Order>(`${this.apiUrl}/${orderId}`).pipe(catchError(this.handleError));
    }

    /**
     * Get validated data for reordering
     */
    getReorderData(orderId: string): Observable<any> {
        return this.http.get<any>(`${this.apiUrl}/${orderId}/reorder-data`).pipe(catchError(this.handleError));
    }

    /**
     * Error handler
     */
    private handleError(error: any): Observable<never> {
        let errorMessage = 'An error occurred';

        if (error.error?.message) {
            errorMessage = error.error.message;
            if (error.error.errors && Array.isArray(error.error.errors)) {
                errorMessage += ': ' + error.error.errors.map((e: any) => e.msg).join(', ');
            }
        } else if (error.message) {
            errorMessage = error.message;
        }

        console.error('Order Error:', error);
        return throwError(() => new Error(errorMessage));
    }
}
