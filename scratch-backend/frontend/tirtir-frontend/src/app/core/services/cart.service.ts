import { Injectable, inject, signal, computed, effect } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, forkJoin, tap, catchError, throwError } from 'rxjs';
import { Cart, AddToCartRequest } from '../models';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

@Injectable({
    providedIn: 'root',
})
export class CartService {
    private apiUrl = `${environment.apiUrl}/cart`;
    private http = inject(HttpClient);
    private authService = inject(AuthService);

    private readonly PENDING_CART_KEY = 'tirtir_pending_cart';

    // Cart state management
    private cartSubject = new BehaviorSubject<Cart | null>(null);
    public cart$ = this.cartSubject.asObservable();

    // Signal-based cart count for header badge
    private cartCountSignal = signal<number>(0);
    public cartCount = computed(() => this.cartCountSignal());

    constructor() {
        // Clear cart state when user logs out
        effect(() => {
            if (!this.authService.isAuthenticated()) {
                this.cartSubject.next(null);
                setTimeout(() => this.cartCountSignal.set(0), 0);
            }
        });
    }

    // ── Pending cart (guest → login flow) ──────────────────────────────────

    /** Save items so they survive the redirect to /login */
    savePendingItems(items: AddToCartRequest[]): void {
        localStorage.setItem(this.PENDING_CART_KEY, JSON.stringify(items));
    }

    getPendingItems(): AddToCartRequest[] {
        try {
            return JSON.parse(localStorage.getItem(this.PENDING_CART_KEY) || '[]');
        } catch {
            return [];
        }
    }

    clearPendingItems(): void {
        localStorage.removeItem(this.PENDING_CART_KEY);
    }

    /**
     * Called on login: add any pending guest items to the server cart, then load.
     * If there are no pending items, falls straight through to loadCart().
     */
    flushPendingItems(): void {
        const pending = this.getPendingItems();
        if (pending.length === 0) {
            this.loadCart();
            return;
        }
        this.clearPendingItems();
        const adds = pending.map(item =>
            this.http.post<Cart>(`${this.apiUrl}/add`, item)
        );
        forkJoin(adds).subscribe({
            next: (carts) => {
                const last = carts[carts.length - 1];
                this.cartSubject.next(last);
                this.updateCartCount(last);
            },
            error: () => this.loadCart()
        });
    }

    /**
     * Load cart from backend
     */
    loadCart(): void {
        this.getCart().subscribe({
            next: (cart) => {
                this.cartSubject.next(cart);
                this.updateCartCount(cart);
            },
            error: (error) => {
                // Cart might be empty or user not authenticated
                console.log('Cart load info:', error.message);
                this.cartSubject.next(null);
                this.cartCountSignal.set(0);
            },
        });
    }

    /**
     * Get cart from backend
     */
    getCart(): Observable<Cart> {
        return this.http.get<Cart>(this.apiUrl).pipe(catchError(this.handleError));
    }

    /**
     * Add item to cart
     */
    addToCart(item: AddToCartRequest): Observable<Cart> {
        return this.http.post<Cart>(`${this.apiUrl}/add`, item).pipe(
            tap((cart) => {
                this.cartSubject.next(cart);
                this.updateCartCount(cart);
            }),
            catchError(this.handleError)
        );
    }

    /**
     * Apply a coupon code
     */
    applyCoupon(code: string): Observable<Cart> {
        return this.http.post<Cart>(`${environment.apiUrl}/coupons/apply`, { code }).pipe(
            tap((cart) => {
                this.cartSubject.next(cart);
                this.updateCartCount(cart);
            }),
            catchError(this.handleError)
        );
    }

    /**
     * Update cart item quantity or shade (Quick Edit)
     */
    updateCartItem(productId: string, quantity: number, shade?: string, oldShade?: string, newShade?: string): Observable<Cart> {
        const payload: any = { productId, quantity, shade: shade ?? '' };
        if (oldShade !== undefined) payload.oldShade = oldShade;
        if (newShade !== undefined) payload.newShade = newShade;

        return this.http.put<Cart>(`${this.apiUrl}/update`, payload).pipe(
            tap((cart) => {
                this.cartSubject.next(cart);
                this.updateCartCount(cart);
            }),
            catchError(this.handleError)
        );
    }

    /**
     * Remove item from cart
     * Calls DELETE /cart/remove/:productId/:shade
     */
    removeCartItem(productId: string, shade?: string): Observable<Cart> {
        // Encode shade to handle empty/undefined — backend expects 'null' string for no shade
        const encodedShade = encodeURIComponent(shade || 'null');
        return this.http.delete<Cart>(`${this.apiUrl}/remove/${productId}/${encodedShade}`).pipe(
            tap((cart) => {
                this.cartSubject.next(cart);
                this.updateCartCount(cart);
            }),
            catchError(this.handleError)
        );
    }

    /**
     * Clear cart (called after successful order)
     */
    clearCart(): void {
        this.cartSubject.next(null);
        this.cartCountSignal.set(0);
    }

    /**
     * Get current cart value (synchronous)
     */
    get currentCartValue(): Cart | null {
        return this.cartSubject.value;
    }

    /**
     * Update cart count signal
     */
    private updateCartCount(cart: Cart | null): void {
        if (cart && cart.items) {
            const totalCount = cart.items.reduce((sum, item) => sum + item.quantity, 0);
            // Wrap in setTimeout to avoid ExpressionChangedAfterItHasBeenCheckedError
            setTimeout(() => this.cartCountSignal.set(totalCount), 0);
        } else {
            setTimeout(() => this.cartCountSignal.set(0), 0);
        }
    }

    /**
     * Error handler
     */
    private handleError(error: any): Observable<never> {
        let errorMessage = 'An error occurred';

        if (error.error?.message) {
            errorMessage = error.error.message;
        } else if (error.message) {
            errorMessage = error.message;
        }

        console.error('Cart Error:', error);
        return throwError(() => new Error(errorMessage));
    }
}
