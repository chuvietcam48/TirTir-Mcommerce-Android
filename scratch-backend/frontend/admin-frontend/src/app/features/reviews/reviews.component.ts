import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ReviewService, Review } from '../../core/services/review.service';

@Component({
    selector: 'app-reviews',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule],
    template: `
<div class="reviews-container">
    <div class="page-header">
        <div>
            <h1>Customer Reviews</h1>
            <p class="subtitle">Moderate and manage product reviews</p>
        </div>
    </div>

    <!-- Stats Cards -->
    <div class="stats-row">
        <div class="stat-card">
            <div class="stat-value">{{ reviews.length }}</div>
            <div class="stat-label">Total Reviews</div>
        </div>
        <div class="stat-card highlight">
            <div class="stat-value">{{ getAvgRating() }}</div>
            <div class="stat-label">Avg Rating</div>
        </div>
        <div class="stat-card">
            <div class="stat-value">{{ getCountByRating(5) }}</div>
            <div class="stat-label">5 Stars</div>
        </div>
        <div class="stat-card">
            <div class="stat-value">{{ getCountByRating(1) + getCountByRating(2) }}</div>
            <div class="stat-label">Low Ratings (1-2★)</div>
        </div>
    </div>

    <!-- Action Error Banner -->
    <div *ngIf="actionError" class="error-banner">
        {{ actionError }}
        <button class="dismiss-btn" (click)="actionError = null">✕</button>
    </div>

    <!-- Filters -->
    <div class="filters-bar">
        <input type="text" [(ngModel)]="searchQuery" (input)="applyFilters()"
            placeholder="Search by product or customer..." class="search-input" />
        <select [(ngModel)]="selectedRating" (change)="applyFilters()" class="filter-select">
            <option value="">All Ratings</option>
            <option value="5">★★★★★ 5 Stars</option>
            <option value="4">★★★★☆ 4 Stars</option>
            <option value="3">★★★☆☆ 3 Stars</option>
            <option value="2">★★☆☆☆ 2 Stars</option>
            <option value="1">★☆☆☆☆ 1 Star</option>
        </select>
    </div>

    <!-- Loading -->
    <div *ngIf="loading" class="loading-state">
        <div class="spinner"></div>
        <p>Loading reviews...</p>
    </div>

    <!-- Error -->
    <div *ngIf="error && !loading" class="error-state">
        <p>{{ error }}</p>
        <button class="btn btn-primary" (click)="loadReviews()">Retry</button>
    </div>

    <!-- Table -->
    <div *ngIf="!loading && !error" class="table-container">
        <table class="data-table">
            <thead>
                <tr>
                    <th>Customer</th>
                    <th>Product</th>
                    <th>Rating</th>
                    <th>Comment</th>
                    <th>Date</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                <tr *ngFor="let review of paginatedReviews">
                    <td>
                        <div class="customer-name">{{ review.user.name || 'Anonymous' }}</div>
                        <div class="customer-email">{{ review.user.email || '' }}</div>
                    </td>
                    <td class="product-name">{{ getProductName(review) }}</td>
                    <td>
                        <span class="stars">{{ getStars(review.rating) }}</span>
                        <span class="rating-num">{{ review.rating }}/5</span>
                    </td>
                    <td class="comment-cell">{{ review.comment }}</td>
                    <td>{{ formatDate(review.createdAt) }}</td>
                    <td class="actions">
                        <a class="btn btn-sm btn-outline" [routerLink]="['/reviews', review._id]" title="View detail">
                            View
                        </a>
                        <ng-container *ngIf="pendingDeleteId !== review._id">
                            <button class="btn btn-sm btn-danger" (click)="requestDelete(review._id)" title="Delete">
                                Delete
                            </button>
                        </ng-container>
                        <ng-container *ngIf="pendingDeleteId === review._id">
                            <span class="confirm-text">Sure?</span>
                            <button class="btn btn-sm btn-danger" (click)="confirmDelete(review._id)">Yes</button>
                            <button class="btn btn-sm btn-secondary" (click)="cancelDelete()">No</button>
                        </ng-container>
                    </td>
                </tr>
            </tbody>
        </table>

        <div *ngIf="filteredReviews.length === 0" class="empty-state">
            <p>No reviews found</p>
        </div>

        <!-- Pagination -->
        <div *ngIf="totalPages > 1" class="pagination">
            <button class="page-btn" [disabled]="currentPage === 1" (click)="goToPage(currentPage - 1)">‹</button>
            <span class="page-info">Page {{ currentPage }} / {{ totalPages }} ({{ filteredReviews.length }} reviews)</span>
            <button class="page-btn" [disabled]="currentPage === totalPages" (click)="goToPage(currentPage + 1)">›</button>
        </div>
    </div>
</div>
    `,
    styles: [`
        .reviews-container { padding: 0; }
        .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
        .page-header h1 { font-size: 24px; font-weight: 700; margin: 0 0 4px; }
        .subtitle { color: #757575; font-size: 14px; margin: 0; }
        .stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
        .stat-card { background: #fff; border: 1px solid #e0e0e0; border-radius: 4px; padding: 20px; text-align: center; }
        .stat-card.highlight { border-color: #d32f2f; }
        .stat-value { font-size: 28px; font-weight: 700; color: #121212; }
        .stat-card.highlight .stat-value { color: #d32f2f; }
        .stat-label { font-size: 12px; color: #757575; margin-top: 4px; text-transform: uppercase; letter-spacing: 0.5px; }
        .error-banner { display: flex; align-items: center; justify-content: space-between; background: #fde8e8; border: 1px solid #f5c6c6; border-radius: 4px; padding: 10px 16px; margin-bottom: 16px; color: #b71c1c; font-size: 14px; }
        .dismiss-btn { background: none; border: none; cursor: pointer; color: #b71c1c; font-size: 16px; padding: 0 4px; }
        .filters-bar { display: flex; gap: 12px; margin-bottom: 16px; }
        .search-input { flex: 1; padding: 10px 12px; border: 1px solid #e0e0e0; border-radius: 4px; font-size: 14px; }
        .filter-select { padding: 10px 12px; border: 1px solid #e0e0e0; border-radius: 4px; font-size: 14px; background: #fff; }
        .customer-name { font-weight: 500; font-size: 14px; }
        .customer-email { font-size: 12px; color: #757575; }
        .product-name { font-weight: 500; max-width: 160px; }
        .stars { font-size: 14px; color: #f57c00; }
        .rating-num { font-size: 12px; color: #757575; margin-left: 4px; }
        .comment-cell { max-width: 240px; font-size: 13px; color: #424242; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .actions { white-space: nowrap; display: flex; gap: 4px; align-items: center; }
        .confirm-text { font-size: 12px; color: #b71c1c; font-weight: 600; }
        .btn-secondary { background: #e0e0e0; color: #333; }
        .btn-outline { background: #fff; border: 1px solid #d1d5db; color: #374151; }
        .btn-outline:hover { background: #f9fafb; }
        .pagination { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 16px 0; }
        .page-btn { padding: 6px 12px; border: 1px solid #e0e0e0; border-radius: 4px; background: #fff; cursor: pointer; font-size: 16px; }
        .page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
        .page-info { font-size: 13px; color: #757575; }
        .loading-state, .error-state, .empty-state { text-align: center; padding: 48px 24px; color: #757575; }
        .spinner { width: 40px; height: 40px; margin: 0 auto 16px; border: 3px solid #e0e0e0; border-top-color: #d32f2f; border-radius: 50%; animation: spin 0.8s linear infinite; }
        @keyframes spin { to { transform: rotate(360deg); } }
    `]
})
export class ReviewsComponent implements OnInit {
    reviews: Review[] = [];
    filteredReviews: Review[] = [];
    loading = true;
    error: string | null = null;
    actionError: string | null = null;
    searchQuery = '';
    selectedRating = '';

    // Pagination
    currentPage = 1;
    pageSize = 20;

    // Inline delete confirm
    pendingDeleteId: string | null = null;

    constructor(private reviewService: ReviewService) { }

    ngOnInit() { this.loadReviews(); }

    loadReviews() {
        this.loading = true;
        this.error = null;
        this.reviewService.getAllReviews().subscribe({
            next: (data) => {
                this.reviews = Array.isArray(data) ? data : (data.reviews || data.data || []);
                this.applyFilters();
                this.loading = false;
            },
            error: () => {
                this.error = 'Failed to load reviews. Please try again.';
                this.loading = false;
            }
        });
    }

    applyFilters() {
        let filtered = [...this.reviews];
        if (this.searchQuery.trim()) {
            const q = this.searchQuery.toLowerCase();
            filtered = filtered.filter(r =>
                this.getProductName(r).toLowerCase().includes(q) ||
                (r.user?.name || '').toLowerCase().includes(q)
            );
        }
        if (this.selectedRating) {
            filtered = filtered.filter(r => r.rating === +this.selectedRating);
        }
        this.filteredReviews = filtered;
        this.currentPage = 1;
    }

    get totalPages(): number {
        return Math.ceil(this.filteredReviews.length / this.pageSize);
    }

    get paginatedReviews(): Review[] {
        const start = (this.currentPage - 1) * this.pageSize;
        return this.filteredReviews.slice(start, start + this.pageSize);
    }

    goToPage(page: number) {
        if (page >= 1 && page <= this.totalPages) this.currentPage = page;
    }

    requestDelete(id: string) {
        this.pendingDeleteId = id;
        this.actionError = null;
    }

    cancelDelete() {
        this.pendingDeleteId = null;
    }

    confirmDelete(id: string) {
        this.reviewService.deleteReview(id).subscribe({
            next: () => {
                this.pendingDeleteId = null;
                this.loadReviews();
            },
            error: (err) => {
                this.actionError = err.error?.message || 'Failed to delete review';
                this.pendingDeleteId = null;
            }
        });
    }

    getStars(rating: number): string { return '★'.repeat(rating) + '☆'.repeat(5 - rating); }
    getCountByRating(r: number): number { return this.reviews.filter(x => x.rating === r).length; }
    getAvgRating(): string {
        if (!this.reviews.length) return '—';
        const avg = this.reviews.reduce((s, r) => s + r.rating, 0) / this.reviews.length;
        return avg.toFixed(1);
    }
    getProductName(review: Review): string {
        return review.product?.Name || review.product_id?.Name || 'N/A';
    }
    formatDate(d: string): string { return new Date(d).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }); }
}
