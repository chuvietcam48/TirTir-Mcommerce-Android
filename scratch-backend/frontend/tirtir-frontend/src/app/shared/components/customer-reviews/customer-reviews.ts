import { Component, OnInit, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

export interface Review {
    id: string;
    author: string;
    date: string;
    rating: number;
    title: string;
    content: string;
    verified: boolean;
    helpful: number;
    shade?: string;
    likedByUser?: boolean;
    images?: string[];
}

@Component({
    selector: 'app-customer-reviews',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './customer-reviews.html',
    styleUrl: './customer-reviews.css',
})
export class CustomerReviewsComponent implements OnInit {
    @Input() productName: string = '';
    @Input() productId: string = '';  // Required to fetch reviews from API

    private http = inject(HttpClient);
    private apiUrl = environment.apiUrl;

    averageRating = 0;
    totalReviews = 0;
    isWritingReview = false;
    loading = false;
    lightboxPhoto: string | null = null;

    newReview = { rating: 5, title: '', content: '' };

    ratingBreakdown = [
        { stars: 5, percentage: 0 },
        { stars: 4, percentage: 0 },
        { stars: 3, percentage: 0 },
        { stars: 2, percentage: 0 },
        { stars: 1, percentage: 0 },
    ];

    reviews: Review[] = [];

    ngOnInit(): void {
        if (this.productId) {
            this.loadReviews();
        }
    }

    loadReviews(page = 1): void {
        this.loading = true;
        this.http.get<any>(`${this.apiUrl}/products/${this.productId}/reviews?page=${page}&limit=10`)
            .subscribe({
                next: (res) => {
                    this.reviews = (res.data || []).map((r: any) => ({
                        id: r._id,
                        author: r.user?.name || 'Anonymous',
                        date: new Date(r.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' }),
                        rating: r.rating,
                        title: r.title || '',
                        content: r.comment || '',
                        verified: r.verifiedPurchase,
                        helpful: r.helpful?.length || 0,
                        shade: r.shade,
                        likedByUser: false,
                        images: r.images || []
                    }));
                    this.totalReviews = res.total || this.reviews.length;
                    this._recalcStats();
                    this.loading = false;
                },
                error: () => { this.loading = false; }
            });
    }

    private _recalcStats(): void {
        if (!this.reviews.length) return;
        const sum = this.reviews.reduce((acc, r) => acc + r.rating, 0);
        this.averageRating = Math.round((sum / this.reviews.length) * 10) / 10;

        const counts: Record<number, number> = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
        this.reviews.forEach(r => counts[r.rating] = (counts[r.rating] || 0) + 1);
        this.ratingBreakdown = [5, 4, 3, 2, 1].map(stars => ({
            stars,
            percentage: Math.round((counts[stars] / this.reviews.length) * 100)
        }));
    }

    getStars(rating: number): number[] {
        return Array(5).fill(0).map((_, i) => i < rating ? 1 : 0);
    }

    getStarClass(filled: number): string {
        return filled ? 'star filled' : 'star empty';
    }

    toggleLike(review: Review) {
        review.likedByUser = !review.likedByUser;
        review.helpful += review.likedByUser ? 1 : -1;
        this.http.post(`${this.apiUrl}/reviews/${review.id}/helpful`, {}).subscribe();
    }

    postReview() {
        if (!this.newReview.title || !this.newReview.content || !this.productId) return;

        this.http.post<any>(`${this.apiUrl}/products/${this.productId}/reviews`, {
            rating: this.newReview.rating,
            title: this.newReview.title,
            comment: this.newReview.content
        }).subscribe({
            next: () => {
                this.isWritingReview = false;
                this.newReview = { rating: 5, title: '', content: '' };
                this.loadReviews();
            },
            error: (err) => {
                alert(err.error?.message || 'Failed to post review. (Verified purchase required)');
            }
        });
    }

    deleteReview(reviewId: string) {
        this.http.delete(`${this.apiUrl}/reviews/${reviewId}`).subscribe({
            next: () => { this.reviews = this.reviews.filter(r => r.id !== reviewId); this.totalReviews--; },
            error: () => { }
        });
    }

    get reviewPhotos(): string[] {
        return this.reviews
            .filter(r => r.images && r.images.length > 0)
            .flatMap(r => r.images!);
    }

    openLightbox(photo: string): void { this.lightboxPhoto = photo; }
    closeLightbox(): void { this.lightboxPhoto = null; }
}
