import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProductCard } from '../../shared/components/product-card/product-card';
import { ProductService } from '../../core/services/product.service';

@Component({
    selector: 'app-cushion',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule, ProductCard],
    templateUrl: './cushion.html',
    styleUrl: './cushion.css',
})
export class CushionComponent implements OnInit {
    collectionTitle = 'CUSHION';
    collectionDescription = 'Discover TIRTIR\'s iconic cushion foundations for long-lasting, flawless coverage.';

    // Filter state
    showFilters = true;
    sortBy = 'featured';

    // Expandable filter groups
    expandedFilters: { [key: string]: boolean } = {
        type: true,
    };

    // Pagination (single page for cushions)
    currentPage = 1;
    totalPages = 1;
    pages = [1];

    // Cushion-specific product types
    productTypes = [
        { label: 'Full Size', value: 'full-size', count: 4 },
        { label: 'Mini', value: 'mini', count: 4 },
    ];

    // Sub-category banners for related collections
    subCategories = [
        {
            label: 'BASE',
            routerLink: '/collections/base',
            image: 'https://placehold.co/400x200/f6e4de/333?text=BASE',
            description: 'Primers & Foundations'
        },
        {
            label: 'FIXERS',
            routerLink: '/collections/fixers',
            image: 'https://placehold.co/400x200/e8d8d0/333?text=FIXERS',
            description: 'Setting Sprays'
        },
        {
            label: 'LIPS',
            routerLink: '/collections/lips',
            image: 'https://placehold.co/400x200/f0d8d8/333?text=LIPS',
            description: 'Tints & Balms'
        },
        {
            label: 'FACE',
            routerLink: '/collections/face',
            image: 'https://placehold.co/400x200/e8e0d8/333?text=FACE',
            description: 'Concealers & More'
        }
    ];

    // Fetched cushion products from the database
    products: any[] = [];

    constructor(private productService: ProductService) { }

    ngOnInit(): void {
        // 1. Fetch Real Products by Category
        this.productService.getProductsByCategory('cushion', 20).subscribe({
            next: (response) => this.products = response.data,
            error: (err) => console.error('Error loading cushions:', err)
        });

        // 2. Fetch Dynamic Counts (Cushion Types)
        this.productService.getProductFilters().subscribe({
            next: (filters) => {
                if (filters.cushionTypes) {
                    this.productTypes.forEach(type => {
                        const match = filters.cushionTypes.find((c: any) => c.name === type.value);
                        if (match) {
                            type.count = match.count;
                        } else {
                            type.count = 0;
                        }
                    });
                }
            },
            error: (err) => console.error('Error loading filters:', err)
        });
    }

    get visibleProducts() {
        return this.products;
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
