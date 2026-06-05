import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ProductService } from '../../core/services/product.service';
import { ProductData } from '../../core/constants/products.data';

import { CartService } from '../../core/services/cart.service';

interface Deal {
    title: string;
    category: string;
    description: string;
    products: ProductData[];
    price: number;
    originalPrice: number;
    discount: number;
}

@Component({
    selector: 'app-deals',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './deals.html',
    styleUrl: './deals.css'
})
export class DealsComponent implements OnInit {
    private productService = inject(ProductService);
    private cartService = inject(CartService);
    private cdr = inject(ChangeDetectorRef);

    deals: Deal[] = [];
    isLoading = true;
    selectedProduct: ProductData | null = null;

    ngOnInit() {
        this.loadDeals();
    }

    loadDeals() {
        this.isLoading = true;
        this.productService.getProducts({ limit: 100 }).subscribe({
            next: (response) => {
                const products = response.data;
                if (products.length >= 4) {
                    this.createCategoryDeals(products);
                }
                this.isLoading = false;
                this.cdr.detectChanges();
            },
            error: (err) => {
                console.error('Failed to load products for deals', err);
                this.isLoading = false;
                this.cdr.detectChanges();
            }
        });
    }

    createCategoryDeals(products: ProductData[]) {
        const makeupCategories = ['makeup', 'cushion', 'lip', 'tint', 'primer', 'balm', 'fixer'];
        
        const uniqueMakeupNames = new Set<string>();
        const makeup = products.filter(p => {
            const cat = ((p as any).category || (p as any).Category || '').toLowerCase();
            const name = (p.name || '').toLowerCase();
            if (makeupCategories.includes(cat) && !name.includes('gift card') && p.price >= 15) {
                if (!uniqueMakeupNames.has(name)) {
                    uniqueMakeupNames.add(name);
                    return true;
                }
            }
            return false;
        }).slice(0, 3);
        
        const skincareCategories = ['skincare', 'cleanser', 'toner', 'cream', 'serum', 'mask', 'sunscreen', 'ampoule', 'facial-oil', 'eye-cream'];
        const skincare = products.filter(p => {
            const cat = ((p as any).category || (p as any).Category || '').toLowerCase();
            const name = (p.name || '').toLowerCase();
            return skincareCategories.includes(cat) && !name.includes('gift card');
        }).slice(0, 3);

        const sourceMakeup = makeup.length >= 2 ? makeup : products.slice(0, 2);
        const sourceSkincare = skincare.length >= 2 ? skincare : products.slice(2, 5);

        const makeupDeal: Deal = {
            title: 'MAKEUP ESSENTIALS COMBO',
            category: 'MAKEUP',
            description: 'Everything you need for a flawless daily look.',
            products: sourceMakeup,
            originalPrice: sourceMakeup.reduce((sum, p) => sum + p.price, 0),
            price: sourceMakeup.reduce((sum, p) => sum + p.price, 0) * 0.85,
            discount: 15
        };

        const skincareDeal: Deal = {
            title: 'GLOW SKINCARE ROUTINE',
            category: 'SKINCARE',
            description: 'The ultimate 3-step routine for glass skin.',
            products: sourceSkincare,
            originalPrice: sourceSkincare.reduce((sum, p) => sum + p.price, 0),
            price: sourceSkincare.reduce((sum, p) => sum + p.price, 0) * 0.85,
            discount: 15
        };

        this.deals = [makeupDeal, skincareDeal];
    }

    showPrice(product: ProductData, event: Event) {
        event.stopPropagation();
        this.selectedProduct = product;
    }

    closePrice() {
        this.selectedProduct = null;
    }

    buyCombo(deal: Deal) {
        // Logic to add all products in combo to cart
        deal.products.forEach(product => {
            this.cartService.addToCart({
                productId: (product as any)._id || product.id,
                quantity: 1,
                shade: (product as any).Shades?.[0] || 'Default'
            }).subscribe();
        });
        alert(`Successfully added "${deal.title}" to cart!`);
    }
}
