import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { InventoryService, InventoryStats, InventoryAlert } from '../../../core/services/inventory.service';
import { ProductService } from '../../../core/services/product.service';
import { environment } from '../../../../environments/environment';

/** Low-stock threshold must match backend `getInventoryStats` → Stock_Quantity < 10 */
// REFACTORED: 2026-03-20 — fixed alert mapping + added full inventory list
const LOW_STOCK_THRESHOLD = 10;

@Component({
    selector: 'app-inventory-dashboard',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule],
    templateUrl: './inventory-dashboard.html',
    styleUrls: ['./inventory-dashboard.css']
})
export class InventoryDashboardComponent implements OnInit {
    stats: InventoryStats | null = null;
    statsLoading = true;

    // Critical alerts (low stock + out of stock from /alerts)
    criticalAlerts: InventoryAlert[] = [];
    alertsLoading = true;

    // Full product inventory list
    allProducts: any[] = [];
    filteredProducts: any[] = [];
    productsLoading = true;

    error: string | null = null;

    // Filters
    searchQuery = '';
    selectedStatus = 'all';   // 'all' | 'in-stock' | 'low-stock' | 'out-of-stock'
    selectedCategory = 'All';
    categories: string[] = ['All'];

    // Filter banner from query param (e.g. ?filter=low-stock from General)
    activeFilter: string | null = null;

    // Quick Restock inline state
    restockingId: string | null = null;
    restockQty: number = 0;
    restockReason = 'Manual restock';
    restocking = false;

    // Pagination for full list
    currentPage = 1;
    readonly pageSize = 20;

    constructor(
        private inventoryService: InventoryService,
        private productService: ProductService,
        private route: ActivatedRoute,
        private router: Router
    ) { }

    ngOnInit(): void {
        this.route.queryParams.subscribe(params => {
            const filter = params['filter'] || null;
            this.activeFilter = filter;
            // Pre-set status filter from query param
            if (filter === 'low-stock') this.selectedStatus = 'low-stock';
            else if (filter === 'out-of-stock') this.selectedStatus = 'out-of-stock';
        });
        this.loadAll();
    }

    loadAll(): void {
        this.error = null;
        this.loadStats();
        this.loadAlerts();
        this.loadProducts();
    }

    // ── Stats ──────────────────────────────────────────────────────
    loadStats(): void {
        this.statsLoading = true;
        this.inventoryService.getInventoryStats().subscribe({
            next: (data) => { this.stats = data; this.statsLoading = false; },
            error: () => { this.statsLoading = false; this.error = 'Failed to load inventory stats'; }
        });
    }

    // ── Alerts (Critical Section) ─────────────────────────────────
    loadAlerts(): void {
        this.alertsLoading = true;
        this.inventoryService.getInventoryAlerts().subscribe({
            next: (data) => {
                // Backend returns { lowStock: { count, items[] }, deadStock: {...} }
                // Critical items = all items from lowStock (includes out-of-stock since 0 < 10)
                this.criticalAlerts = Array.isArray(data?.lowStock?.items)
                    ? data.lowStock.items
                    : [];
                this.alertsLoading = false;
            },
            error: () => {
                this.criticalAlerts = [];
                this.alertsLoading = false;
            }
        });
    }

    // ── Full Product List ─────────────────────────────────────────
    loadProducts(): void {
        this.productsLoading = true;
        this.productService.getAllProducts().subscribe({
            next: (data: any) => {
                this.allProducts = Array.isArray(data) ? data : (data.products || data.data || []);
                // Build category list from real data
                const catSet = new Set<string>(this.allProducts.map((p: any) => p.Category).filter(Boolean));
                this.categories = ['All', ...Array.from(catSet).sort()];
                this.applyFilters();
                this.productsLoading = false;
            },
            error: () => {
                this.productsLoading = false;
                this.error = 'Failed to load product inventory';
            }
        });
    }

    // ── Filtering ─────────────────────────────────────────────────
    applyFilters(): void {
        let result = [...this.allProducts];

        // Search
        if (this.searchQuery.trim()) {
            const q = this.searchQuery.toLowerCase();
            result = result.filter(p =>
                (p.Name || '').toLowerCase().includes(q) ||
                (p.Product_ID || '').toLowerCase().includes(q)
            );
        }

        // Category
        if (this.selectedCategory && this.selectedCategory !== 'All') {
            result = result.filter(p => p.Category === this.selectedCategory);
        }

        // Stock Status (threshold aligned with backend: < 10 = low stock)
        switch (this.selectedStatus) {
            case 'in-stock':
                result = result.filter(p => (p.Stock_Quantity ?? 0) >= LOW_STOCK_THRESHOLD);
                break;
            case 'low-stock':
                result = result.filter(p => (p.Stock_Quantity ?? 0) > 0 && (p.Stock_Quantity ?? 0) < LOW_STOCK_THRESHOLD);
                break;
            case 'out-of-stock':
                result = result.filter(p => (p.Stock_Quantity ?? 0) === 0);
                break;
        }

        this.filteredProducts = result;
        this.currentPage = 1;
    }

    onSearchChange(): void { this.applyFilters(); }
    onStatusChange(): void { this.applyFilters(); }
    onCategoryChange(): void { this.applyFilters(); }

    clearFilter(): void {
        this.activeFilter = null;
        this.selectedStatus = 'all';
        this.router.navigate([], { queryParams: {} });
        this.applyFilters();
    }

    // ── Pagination ────────────────────────────────────────────────
    get paginatedProducts(): any[] {
        const start = (this.currentPage - 1) * this.pageSize;
        return this.filteredProducts.slice(start, start + this.pageSize);
    }

    get totalPages(): number {
        return Math.ceil(this.filteredProducts.length / this.pageSize);
    }

    goToPage(page: number): void {
        if (page >= 1 && page <= this.totalPages) this.currentPage = page;
    }

    // ── Stock count getters (unaffected by status filter, for dropdown labels) ─
    get countInStock(): number {
        return this.allProducts.filter(p => (p.Stock_Quantity ?? 0) >= LOW_STOCK_THRESHOLD).length;
    }
    get countLowStock(): number {
        return this.allProducts.filter(p => (p.Stock_Quantity ?? 0) > 0 && (p.Stock_Quantity ?? 0) < LOW_STOCK_THRESHOLD).length;
    }
    get countOutOfStock(): number {
        return this.allProducts.filter(p => (p.Stock_Quantity ?? 0) === 0).length;
    }

    // ── Stock Status Helpers (threshold = backend's 10) ──────────
    getStockStatus(qty: number): 'out-of-stock' | 'low-stock' | 'in-stock' {
        if (qty === 0) return 'out-of-stock';
        if (qty < LOW_STOCK_THRESHOLD) return 'low-stock';
        return 'in-stock';
    }

    getStockLabel(qty: number): string {
        if (qty === 0) return 'Out of Stock';
        if (qty < LOW_STOCK_THRESHOLD) return 'Low Stock';
        return 'In Stock';
    }

    // ── Quick Restock (Critical Alerts section) ───────────────────
    openRestock(item: any): void {
        this.restockingId = item._id;
        this.restockQty = 0;
        this.restockReason = 'Manual restock';
    }

    cancelRestock(): void {
        this.restockingId = null;
    }

    confirmRestock(item: any): void {
        if (!this.restockQty || this.restockQty <= 0) return;
        this.restocking = true;

        // Backend PATCH /adjust expects { productId, newStock, reason }
        const newStock = (item.Stock_Quantity ?? 0) + this.restockQty;

        this.inventoryService.adjustStock({
            productId: item._id || item.Product_ID,
            newStock,
            reason: this.restockReason || 'Manual restock'
        }).subscribe({
            next: () => {
                this.restocking = false;
                this.restockingId = null;
                this.loadAll(); // Reload everything reactively
            },
            error: (err: any) => {
                console.error('Restock error:', err);
                this.restocking = false;
                this.error = err.error?.message || 'Failed to restock. Please try again.';
            }
        });
    }

    // ── Image resolution ──────────────────────────────────────────
    resolveImage(path: string): string {
        if (!path) return '/assets/placeholder-product.svg';
        if (path.startsWith('http') || path.startsWith('data:')) return path;
        const base = environment.apiUrl.replace('/api/v1', '');
        const clean = path.startsWith('/') ? path.slice(1) : path;
        return `${base}/${clean}`;
    }
}
