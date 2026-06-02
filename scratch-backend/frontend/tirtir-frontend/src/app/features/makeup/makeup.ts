import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProductCard } from '../../shared/components/product-card/product-card';
import { ProductService } from '../../core/services/product.service';

@Component({
    selector: 'app-makeup',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule, ProductCard],
    templateUrl: './makeup.html',
    styleUrl: './makeup.css',
})
export class MakeupComponent implements OnInit {
    collectionTitle = 'MAKEUP';
    collectionDescription = 'Discover TIRTIR makeup essentials for a long-lasting, luminous finish.';

    // Filter state
    showFilters = true;
    sortBy = 'featured';

    // Expandable filter groups
    expandedFilters: { [key: string]: boolean } = {
        type: true,
    };

    // Pagination
    currentPage = 1;
    totalPages = 0; // Will be set by API call
    pages: number[] = []; // Will be set by API call

    // Makeup-specific product types matching TIRTIR
    productTypes = [
        { label: 'Cushion', value: 'cushion', count: 12 },
        { label: 'Lip Balm', value: 'lip-balm', count: 3 },
        { label: 'Lip Oil', value: 'lip-oil', count: 2 },
        { label: 'Lip Plumper', value: 'lip-plumper', count: 1 },
        { label: 'Lip Tint', value: 'lip-tint', count: 4 },
        { label: 'Makeup Fixer', value: 'makeup-fixer', count: 2 },
    ];

    // Sub-category banners
    subCategories = [
        {
            label: 'FACE',
            routerLink: '/collections/face',
            image: 'https://placehold.co/800x400/f6e4de/333?text=FACE',
            description: 'Cushions, Concealers & More'
        },
        {
            label: 'LIPS',
            routerLink: '/collections/lips',
            image: 'https://placehold.co/800x400/e8d5d0/333?text=LIPS',
            description: 'Tints, Balms & Glosses'
        }
    ];

    // Products loaded from backend
    products: any[] = [];
    loading = false;

    constructor(
        private route: ActivatedRoute,
        private productService: ProductService
    ) { }

    ngOnInit(): void {
        this.loadProducts();
    }

    loadProducts(): void {
        this.loading = true;
        // Fetch only makeup products (Is_Skincare=false)
        this.productService.getProducts({ isSkincare: 'false' }).subscribe({
            next: (response) => {
                this.products = response.data;
                this.totalPages = Math.ceil(response.total / 12);
                this.pages = Array.from({ length: this.totalPages }, (_, i) => i + 1);
                this.loading = false;
            },
            error: (err) => {
                console.error('Error loading makeup products:', err);
                this.loading = false;
            }
        });
    }

    get visibleProducts() {
        const itemsPerPage = 12;
        const start = (this.currentPage - 1) * itemsPerPage;
        return this.products.slice(start, start + itemsPerPage);
    }

    get productCount() {
        return this.products.length;
    }

    toggleFilters() {
        this.showFilters = !this.showFilters;
    }

    toggleFilterGroup(group: string) {
        this.expandedFilters[group] = !this.expandedFilters[group];
    }

    goToPage(page: number) {
        if (page >= 1 && page <= this.totalPages) {
            this.currentPage = page;
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    }
}
