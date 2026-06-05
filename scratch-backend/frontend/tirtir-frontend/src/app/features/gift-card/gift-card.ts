import { Component, ChangeDetectionStrategy, inject, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CartService } from '../../core/services/cart.service';
import { ProductService } from '../../core/services/product.service';
import { ProductData } from '../../core/constants/products.data';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner';

@Component({
    selector: 'app-gift-card',
    standalone: true,
    imports: [CommonModule, LoadingSpinnerComponent],
    templateUrl: './gift-card.html',
    styleUrl: './gift-card.css',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class GiftCardComponent implements OnInit {
    private cartService = inject(CartService);
    private productService = inject(ProductService);
    private cdr = inject(ChangeDetectorRef);

    giftCardProduct: ProductData | null = null;
    selectedAmount = 10;
    amounts: number[] = [];
    giftCardIds: { [key: number]: string } = {};
    loading = true;

    ngOnInit() {
        this.productService.getProducts({ category: 'gift-card' }).subscribe({
            next: (res) => {
                this.loading = false;

                // Find all gift card variants
                const products = res.data || [];
                if (products.length > 0) {
                    // Set the base product
                    // The backend returns separate products for each amount (e.g. tirtir-gift-card-10, tirtir-gift-card-25)
                    // We can use the lowest price one as the base, or create a virtual one.
                    // The ProductService already remaps tirtir-gift-card-10 to slug 'tirtir-gift-card'.
                    const baseCard = products.find(p => p.slug === 'tirtir-gift-card') || products[0];
                    this.giftCardProduct = baseCard;

                    // Populate amounts and IDs
                    const amountsMap = new Map<number, string>();
                    products.forEach(p => {
                        amountsMap.set(p.price, p.id);
                    });

                    this.amounts = Array.from(amountsMap.keys()).sort((a, b) => a - b);
                    this.amounts.forEach(amt => {
                        this.giftCardIds[amt] = amountsMap.get(amt)!;
                    });

                    if (this.amounts.length > 0) {
                        this.selectedAmount = this.amounts[0];
                        this.giftCardProduct.price = this.selectedAmount;
                    }
                }
                this.cdr.detectChanges();
            },
            error: (err) => {
                this.loading = false;
                console.error('Error fetching gift cards', err);
                this.cdr.detectChanges();
            }
        });
    }

    selectAmount(amount: number) {
        this.selectedAmount = amount;
        if (this.giftCardProduct) {
            this.giftCardProduct.price = amount;
        }
    }

    addToCart() {
        if (!this.giftCardProduct) return;
        const productId = this.giftCardIds[this.selectedAmount];
        if (!productId) {
            alert('Invalid amount selected');
            return;
        }

        this.cartService.addToCart({ productId, quantity: 1 }).subscribe({
            next: () => alert(`Gift Card ($${this.selectedAmount}) added to cart!`),
            error: (err) => alert('Failed to add to cart: ' + err.message)
        });
    }
}
