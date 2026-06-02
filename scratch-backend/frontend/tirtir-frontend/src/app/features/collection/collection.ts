
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProductCard } from '../../shared/components/product-card/product-card';
import { ProductData } from '../../core/constants/products.data';
import { ProductService } from '../../core/services/product.service';

interface CategoryConfig {
    title: string;
    description: string;
    productCategories: string[];
    productSlugs?: string[];
}

@Component({
    selector: 'app-collection',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule, ProductCard],
    templateUrl: './collection.html',
    styleUrl: './collection.css',
})
export class CollectionComponent implements OnInit {
    collectionTitle = 'ALL PRODUCTS';
    collectionDescription = 'Discover all TIRTIR products.';
    sortBy = 'best-selling';
    currentPage = 1;
    productsPerPage = 12;
    products: ProductData[] = [];
    collectionSlug = '';
    paginatedProducts: ProductData[] = [];

    // Category mappings
    categoryConfigs: { [key: string]: CategoryConfig } = {
        // Main categories
        'skincare': {
            title: 'SKINCARE',
            description: 'Discover TIRTIR skincare essentials for healthy, radiant skin.',
            productCategories: ['cleanser', 'toner', 'serum', 'ampoule', 'cream', 'sunscreen', 'facial-oil', 'eye-cream', 'mask', 'gift-set', 'skincare'],
        },
        'makeup': {
            title: 'MAKEUP',
            description: 'Discover TIRTIR makeup essentials for a long-lasting, luminous finish.',
            productCategories: ['cushion', 'lip', 'makeup'],
        },
        // Sub-categories - Face
        'face': {
            title: 'FACE',
            description: 'Foundation, cushion, and base makeup products.',
            productCategories: ['cushion', 'makeup'],
        },
        'lip': {
            title: 'LIP',
            description: 'Lip tints, balms, and glosses for every mood.',
            productCategories: ['lip'],
        },
        'lips': {
            title: 'LIP',
            description: 'Lip tints, balms, and glosses for every mood.',
            productCategories: ['lip'],
        },
        'lip-makeup': {
            title: 'LIP MAKEUP',
            description: 'Lip tints, balms, and glosses for every mood.',
            productCategories: ['lip'],
        },
        'face-makeup': {
            title: 'FACE MAKEUP',
            description: 'Foundation, cushion, and base makeup products.',
            productCategories: ['cushion', 'makeup'],
        },
        // Sub-categories - Skincare
        'cleanse-toner': {
            title: 'CLEANSE & TONER',
            description: 'Gentle cleansers and hydrating toners for clean, balanced skin.',
            productCategories: ['cleanser', 'toner'],
        },
        'treatments': {
            title: 'TREATMENTS',
            description: 'Serums, ampoules, and targeted treatments for skin concerns.',
            productCategories: ['serum', 'ampoule', 'facial-oil', 'eye-cream', 'mask'],
        },
        'moisturize-sunscreen': {
            title: 'MOISTURIZE & SUNSCREEN',
            description: 'Hydrating creams and protective sunscreens for healthy skin.',
            productCategories: ['cream', 'sunscreen', 'gift-set'],
        },
    };

    constructor(
        private route: ActivatedRoute,
        private productService: ProductService
    ) { }

    ngOnInit(): void {
        this.route.params.subscribe(params => {
            this.collectionSlug = params['slug'] || 'all';
            this.loadProducts();
        });
    }

    loadProducts(): void {
        const config = this.categoryConfigs[this.collectionSlug];

        if (config) {
            this.collectionTitle = config.title;
            this.collectionDescription = config.description;
        }

        // Fetch FILTERED products from backend (Server-Side Filtering)
        // We pass 'categorySlug' which matches our route slug (e.g. 'cleanse-toner')
        // The backend now knows how to map 'cleanse-toner' -> ['cleanser', 'toner']
        const queryParams: any = {
            limit: 1000, // Fetch all matching items for client-side pagination (simplified)
            categorySlug: this.collectionSlug
        };

        // Special case: 'all' collection
        if (this.collectionSlug === 'all') {
            delete queryParams.categorySlug;
        }

        this.productService.getProducts(queryParams).subscribe(response => {
            // Wrap in setTimeout to avoid ExpressionChangedAfterItHasBeenCheckedError
            setTimeout(() => {
                this.products = response.data;

                // Reset pagination and update
                this.currentPage = 1;
                this.updatePagination();
            }, 0);
        });
    }

    updatePagination() {
        const startIndex = (this.currentPage - 1) * this.productsPerPage;
        this.paginatedProducts = this.products.slice(startIndex, startIndex + this.productsPerPage);
    }

    get totalPages(): number {
        return Math.ceil(this.products.length / this.productsPerPage);
    }

    get pages(): number[] {
        return Array.from({ length: this.totalPages }, (_, i) => i + 1);
    }

    goToPage(page: number): void {
        if (page >= 1 && page <= this.totalPages) {
            this.currentPage = page;
            this.updatePagination();
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    }
}
