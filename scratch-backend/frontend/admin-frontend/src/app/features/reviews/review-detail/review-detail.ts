import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Review, ReviewService } from '../../../core/services/review.service';
import { environment } from '../../../../environments/environment';

@Component({
    selector: 'app-review-detail',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './review-detail.html',
    styleUrls: ['./review-detail.css']
})
export class ReviewDetailComponent implements OnInit {
    review: Review | null = null;
    loading = true;
    error: string | null = null;

    constructor(
        private route: ActivatedRoute,
        private reviewService: ReviewService
    ) {}

    ngOnInit(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (!id) {
            this.error = 'Review ID is missing.';
            this.loading = false;
            return;
        }
        this.loadReview(id);
    }

    loadReview(id: string): void {
        this.loading = true;
        this.error = null;
        this.reviewService.getReviewById(id).subscribe({
            next: (res) => {
                this.review = (res?.data || res) as Review;
                this.loading = false;
            },
            error: (err) => {
                this.error = err?.error?.message || 'Failed to load review detail.';
                this.loading = false;
            }
        });
    }

    get reviewProduct() {
        return this.review?.product || this.review?.product_id || null;
    }

    getStars(rating: number): string {
        return '★'.repeat(rating || 0) + '☆'.repeat(Math.max(0, 5 - (rating || 0)));
    }

    formatDate(value?: string): string {
        if (!value) return 'N/A';
        return new Date(value).toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    resolveProductImage(path?: string): string {
        if (!path) return '';
        if (path.startsWith('http') || path.startsWith('data:')) return path;
        const base = environment.apiUrl.replace('/api/v1', '');
        return `${base}/${path.startsWith('/') ? path.slice(1) : path}`;
    }

    getProductUrl(): string {
        const slug = this.reviewProduct?.Product_Slug || this.reviewProduct?.slug;
        if (!slug) return '#';
        const storefrontOrigin = window.location.origin.replace(':4201', ':4200');
        return `${storefrontOrigin}/products/${slug}`;
    }
}
