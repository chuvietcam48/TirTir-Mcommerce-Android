import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminProductService, AdminProduct } from '../../services/admin-product.service';

@Component({
    selector: 'app-product-list',
    standalone: true,
    imports: [CommonModule, RouterLink, FormsModule],
    templateUrl: './product-list.html',
    styleUrl: './product-list.css',
})
export class ProductListComponent implements OnInit {
    private productService = inject(AdminProductService);

    // Data
    products: AdminProduct[] = [];
    totalProducts = 0;
    categories: string[] = [];

    // Filters
    searchQuery = '';
    categoryFilter = '';
    currentPage = 1;
    pageSize = 20;

    // UI state
    loading = true;
    selectedIds = new Set<string>();
    showDeleteConfirm = false;
    deleteTargetId: string | null = null;

    ngOnInit(): void {
        this.loadProducts();
    }

    loadProducts(): void {
        this.loading = true;
        this.selectedIds.clear();

        this.productService
            .getProducts({
                page: this.currentPage,
                limit: this.pageSize,
                search: this.searchQuery || undefined,
                category: this.categoryFilter || undefined,
            })
            .subscribe({
                next: (res) => {
                    this.products = res.data;
                    this.totalProducts = res.total;
                    this.categories = res.categories || [];
                    this.loading = false;
                },
                error: (err) => {
                    console.error('Failed to load products:', err);
                    this.loading = false;
                },
            });
    }

    /* ── Search & Filter ────────────────────────────────────── */

    onSearch(): void {
        this.currentPage = 1;
        this.loadProducts();
    }

    onCategoryChange(): void {
        this.currentPage = 1;
        this.loadProducts();
    }

    clearFilters(): void {
        this.searchQuery = '';
        this.categoryFilter = '';
        this.currentPage = 1;
        this.loadProducts();
    }

    /* ── Pagination ─────────────────────────────────────────── */

    get totalPages(): number {
        return Math.ceil(this.totalProducts / this.pageSize);
    }

    get pageNumbers(): number[] {
        const pages: number[] = [];
        const start = Math.max(1, this.currentPage - 2);
        const end = Math.min(this.totalPages, this.currentPage + 2);
        for (let i = start; i <= end; i++) pages.push(i);
        return pages;
    }

    goToPage(page: number): void {
        if (page >= 1 && page <= this.totalPages) {
            this.currentPage = page;
            this.loadProducts();
        }
    }

    /* ── Selection & Bulk ───────────────────────────────────── */

    toggleSelect(id: string): void {
        this.selectedIds.has(id) ? this.selectedIds.delete(id) : this.selectedIds.add(id);
    }

    toggleSelectAll(): void {
        if (this.selectedIds.size === this.products.length) {
            this.selectedIds.clear();
        } else {
            this.products.forEach((p) => this.selectedIds.add(p._id));
        }
    }

    get allSelected(): boolean {
        return this.products.length > 0 && this.selectedIds.size === this.products.length;
    }

    /* ── Delete ─────────────────────────────────────────────── */

    confirmDelete(id: string): void {
        this.deleteTargetId = id;
        this.showDeleteConfirm = true;
    }

    cancelDelete(): void {
        this.showDeleteConfirm = false;
        this.deleteTargetId = null;
    }

    executeDelete(): void {
        if (!this.deleteTargetId) return;
        this.productService.deleteProduct(this.deleteTargetId).subscribe({
            next: () => {
                this.showDeleteConfirm = false;
                this.deleteTargetId = null;
                this.loadProducts();
            },
            error: (err) => console.error('Delete failed:', err),
        });
    }

    bulkDelete(): void {
        // Sequentially delete selected (in real app, use a batch endpoint)
        const ids = Array.from(this.selectedIds);
        if (!confirm(`Delete ${ids.length} products?`)) return;
        let completed = 0;
        ids.forEach((id) => {
            this.productService.deleteProduct(id).subscribe({
                next: () => {
                    completed++;
                    if (completed === ids.length) this.loadProducts();
                },
            });
        });
    }

    /* ── Helpers ─────────────────────────────────────────────── */

    formatCurrency(value: number): string {
        return '₫' + value.toLocaleString('vi-VN');
    }

    getImageUrl(thumb: string): string {
        if (!thumb) return '';
        if (thumb.startsWith('http')) return thumb;
        const base = (globalThis as any).__env__?.apiUrl?.replace('/api/v1', '') || 'http://localhost:5001';
        return `${base}/${thumb.startsWith('/') ? thumb.substring(1) : thumb}`;
    }

    getStockClass(qty: number): string {
        if (qty === 0) return 'stock-out';
        if (qty < 10) return 'stock-low';
        return 'stock-ok';
    }
}
