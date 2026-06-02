import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../../core/services/product.service';
import { environment } from '../../../../environments/environment';

interface Product {
    _id?: string;
    id?: string;
    Name: string;
    Product_ID: string;
    Category: string;
    Price: number;
    Thumbnail_Images?: string;
    Stock_Quantity?: number;
}

@Component({
    selector: 'app-product-list',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule],
    templateUrl: './product-list.html',
    styleUrls: ['./product-list.css']
})
export class ProductListComponent implements OnInit {
    products: Product[] = [];
    filteredProducts: Product[] = [];
    loading = true;
    error: string | null = null;

    // Pagination
    currentPage = 1;
    pageSize = 20;
    totalPages = 1;

    // Search & Filters
    searchQuery = '';
    selectedCategory = '';
    selectedStockStatus = '';

    // Categories derived from loaded products — populated in loadProducts()
    categories: string[] = ['All'];
    stockStatuses = [
        { value: '', label: 'All Stock Levels' },
        { value: 'in-stock', label: 'In Stock (≥ 10)' },
        { value: 'low-stock', label: 'Low Stock (1–9)' },
        { value: 'out-of-stock', label: 'Out of Stock (0)' }
    ];

    // ─── Row-Scoped Action State ────────────────────────────────
    // Tracks state per product ID: 'confirm-delete', 'deleting', etc.
    rowState: { [id: string]: { status: 'idle' | 'confirm-delete' | 'deleting', error?: string } } = {};

    constructor(
        private productService: ProductService,
        private route: ActivatedRoute,
        private router: Router
    ) { }

    ngOnInit(): void {
        // Optional deep-link filters from General tab / other pages
        this.route.queryParams.subscribe(params => {
            const q = params['q'];
            const stock = params['stock'];
            const filter = params['filter']; // Dashboard sometimes sends '?filter=low-stock'
            const category = params['category'];

            if (typeof q === 'string') this.searchQuery = q;
            if (typeof stock === 'string') this.selectedStockStatus = stock;
            if (typeof filter === 'string') {
                if (filter === 'low-stock' || filter === 'out-of-stock') {
                    this.selectedStockStatus = filter;
                }
            }
            if (typeof category === 'string') this.selectedCategory = category;

            if (this.products.length > 0) this.applyFilters();
        });

        this.loadProducts();
    }

    loadProducts(): void {
        this.loading = true;
        this.error = null;
        this.rowState = {}; // reset specific states

        this.productService.getAllProducts().subscribe({
            next: (data: any) => {
                this.products = Array.isArray(data) ? data : (data.products || data.data || []);
                // Derive categories dynamically from real DB data
                const catSet = new Set<string>(this.products.map((p: any) => p.Category).filter(Boolean));
                this.categories = ['All', ...Array.from(catSet).sort()];
                this.applyFilters();
                this.loading = false;
            },
            error: (err: any) => {
                this.error = 'Failed to load products';
                this.loading = false;
                console.error('Product load error:', err);
            }
        });
    }

    applyFilters(): void {
        let filtered = [...this.products];

        if (this.searchQuery.trim()) {
            const query = this.searchQuery.toLowerCase();
            filtered = filtered.filter(p =>
                (p.Name || '').toLowerCase().includes(query) ||
                (p.Product_ID || '').toLowerCase().includes(query)
            );
        }

        if (this.selectedCategory && this.selectedCategory !== 'All') {
            filtered = filtered.filter(p => p.Category === this.selectedCategory);
        }

        if (this.selectedStockStatus) {
            switch (this.selectedStockStatus) {
                case 'in-stock': filtered = filtered.filter(p => (p.Stock_Quantity || 0) >= 10); break;
                case 'low-stock': filtered = filtered.filter(p => (p.Stock_Quantity || 0) > 0 && (p.Stock_Quantity || 0) < 10); break;
                case 'out-of-stock': filtered = filtered.filter(p => (p.Stock_Quantity || 0) === 0); break;
            }
        }

        this.filteredProducts = filtered;
        this.totalPages = Math.ceil(filtered.length / this.pageSize);
        this.currentPage = 1;
    }

    getPaginatedProducts(): Product[] {
        const start = (this.currentPage - 1) * this.pageSize;
        return this.filteredProducts.slice(start, start + this.pageSize);
    }

    onSearchChange(): void { this.applyFilters(); }
    onCategoryChange(): void { this.applyFilters(); }
    onStockStatusChange(): void { this.applyFilters(); }

    goToPage(page: number): void {
        if (page >= 1 && page <= this.totalPages) this.currentPage = page;
    }

    goToDetail(product: Product): void {
        const id = product._id || product.id;
        if (id) {
            this.router.navigate(['/products/detail', id]);
        }
    }



    // ─── Delete ──────────────────────────────────────────────────
    getRowState(p: Product) {
        const id = p._id || p.id || '';
        if (!this.rowState[id]) {
            this.rowState[id] = { status: 'idle' };
        }
        return this.rowState[id];
    }

    requestDelete(p: Product): void {
        const id = p._id || p.id || '';
        if (id) {
            this.getRowState(p).status = 'confirm-delete';
            this.getRowState(p).error = undefined;
        }
    }

    cancelDelete(p: Product): void {
        const id = p._id || p.id || '';
        if (id) {
            this.getRowState(p).status = 'idle';
            this.getRowState(p).error = undefined;
        }
    }

    confirmDelete(product: Product): void {
        const id = product._id || product.id;
        if (!id) return;
        const state = this.getRowState(product);
        state.status = 'deleting';
        state.error = undefined;

        this.productService.deleteProduct(id).subscribe({
            next: () => {
                // Remove from local arrays
                this.products = this.products.filter(p => (p._id || p.id) !== id);
                this.applyFilters();
                delete this.rowState[id];
            },
            error: (err: any) => {
                state.status = 'confirm-delete'; // Back to confirm mode to show error
                state.error = err.error?.message || 'Failed to delete product';
            }
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────
    getStockStatus(stock: number): string {
        if (stock === 0) return 'out-of-stock';
        if (stock < 10) return 'low-stock';
        return 'in-stock';
    }

    getStockLabel(stock: number): string {
        if (stock === 0) return 'Out of Stock';
        if (stock < 10) return 'Low Stock';
        return 'In Stock';
    }

    getMainImage(product: Product): string {
        const raw = product.Thumbnail_Images || '';
        if (!raw) return '/assets/placeholder-product.svg';
        if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('data:')) return raw;
        
        const backendBase = environment.apiUrl.replace('/api/v1', '');
        const clean = raw.startsWith('/') ? raw.slice(1) : raw;
        return `${backendBase}/${clean}`;
    }
}
