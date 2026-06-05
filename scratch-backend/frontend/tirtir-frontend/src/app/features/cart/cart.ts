import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, timer } from 'rxjs';
import { CartService } from '../../core/services/cart.service';
import { AuthService } from '../../core/services/auth.service';
import { Cart, CartItem } from '../../core/models';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner';
import { FreeShippingBarComponent } from '../../shared/components/free-shipping-bar/free-shipping-bar.component';

@Component({
    selector: 'app-cart',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule, LoadingSpinnerComponent, FreeShippingBarComponent],
    templateUrl: './cart.html',
    styleUrls: ['./cart.css'],
})
export class CartComponent implements OnInit {
    cartService = inject(CartService);
    authService = inject(AuthService);
    router = inject(Router);

    cart: Cart | null = null;
    loading = true;
    error = '';
    promoCode = '';
    promoMessage = '';
    promoError = '';

    ngOnInit(): void {
        this.loadCart();
    }

    loadCart(): void {
        this.loading = true;

        // Use forkJoin to ensure the spinner stays for at least 800ms
        // even if the API response is faster, providing a premium feel.
        forkJoin([
            this.cartService.getCart(),
            timer(800)
        ]).subscribe({
            next: ([cart]) => {
                this.cart = cart;
                this.loading = false;
            },
            error: (err) => {
                this.error = err.message;
                this.loading = false;
            },
        });
    }

    updateQuantity(item: CartItem, newQuantity: number): void {
        if (newQuantity < 1) {
            // Auto-Remove Logic
            this.removeItemWithoutConfirm(item);
            return;
        }

        this.cartService.updateCartItem(
            item.product._id,
            newQuantity,
            item.shade
        ).subscribe({
            next: (updatedCart) => {
                this.cart = updatedCart;
                this.error = '';
            },
            error: (err) => {
                this.error = err.message;
            },
        });
    }

    removeItemWithoutConfirm(item: CartItem): void {
        this.cartService.removeCartItem(item.product._id, item.shade).subscribe({
            next: (updatedCart) => {
                // Future enhancement: Use a Toast Service here
                alert('Item removed from cart');
                this.cart = updatedCart;
                this.error = '';
            },
            error: (err) => {
                this.error = err.message;
            },
        });
    }

    // --- QUICK EDIT LOGIC --- //
    editingItemId: string | null = null;
    editingItemShade: string | null = null;

    toggleEditShade(item: CartItem): void {
        const uniqueId = item.product._id + '-' + (item.shade || '');
        if (this.editingItemId === uniqueId) {
            // Cancel edit
            this.editingItemId = null;
            this.editingItemShade = null;
        } else {
            // Start edit
            this.editingItemId = uniqueId;
            this.editingItemShade = item.shade || '';
        }
    }

    isEditingShade(item: CartItem): boolean {
        const uniqueId = item.product._id + '-' + (item.shade || '');
        return this.editingItemId === uniqueId;
    }

    saveNewShade(item: CartItem, event: any): void {
        const newShade = event.target.value;
        if (newShade === item.shade) {
            this.toggleEditShade(item); // No change, just close
            return;
        }

        // Send the HTTP request using the extended updateCartItem method
        this.cartService.updateCartItem(
            item.product._id,
            item.quantity,
            undefined, // We don't need 'shade' because we are sending old/new
            item.shade, // oldShade
            newShade   // newShade
        ).subscribe({
            next: (updatedCart) => {
                this.editingItemId = null;
                this.cart = updatedCart;
                this.error = '';
            },
            error: (err) => {
                this.error = err.message;
                this.editingItemId = null;
            }
        });
    }
    // ------------------------- //

    getImageUrl(url: string): string {
        if (!url) return '';
        if (url.startsWith('http')) return url;
        const cleanUrl = url.startsWith('/') ? url.substring(1) : url;
        return `http://localhost:5001/${cleanUrl}`;
    }

    onImageError(event: any): void {
        event.target.src = 'assets/placeholder-product.png';
    }

    getSubtotal(): number {
        if (!this.cart || !this.cart.items) return 0;
        return this.cart.items.reduce((sum, item) => sum + ((item.product.Price || 0) * item.quantity), 0);
    }

    getShipping(): number {
        return 0; // Free shipping
    }

    getTotal(): number {
        return this.getSubtotal() + this.getShipping();
    }

    goToCheckout(): void {
        this.router.navigate(['/checkout']);
    }

    continueShoppping(): void {
        this.router.navigate(['/shop']);
    }

    applyPromoCode(): void {
        this.promoError = '';
        this.promoMessage = '';
        if (!this.promoCode.trim()) {
            this.promoError = 'Please enter a coupon code';
            return;
        }

        this.cartService.applyCoupon(this.promoCode).subscribe({
            next: (updatedCart) => {
                this.cart = updatedCart;
                this.promoMessage = 'Coupon applied successfully!';
            },
            error: (err) => {
                this.promoError = err.message || 'Invalid coupon or expired.';
            }
        });
    }

    removeItem(item: CartItem): void {
        if (!confirm('Remove this item from your cart?')) return;

        this.cartService.removeCartItem(item.product._id, item.shade).subscribe({
            next: (updatedCart) => {
                this.cart = updatedCart;
                this.error = '';
            },
            error: (err) => {
                this.error = err.message;
            },
        });
    }
}
