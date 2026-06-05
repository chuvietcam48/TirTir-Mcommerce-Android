import { Component, OnInit, Input, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface Review {
    id: number;
    author: string;
    date: string;
    rating: number;
    title: string;
    content: string;
    verified: boolean;
    helpful: number;
    shade?: string;
    likedByUser?: boolean;
}

@Component({
    selector: 'app-customer-reviews',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './customer-reviews.component.html',
    styleUrl: './customer-reviews.component.css',
})
export class CustomerReviewsComponent {
    @Input() productName: string = '';

    averageRating = 4.8;
    totalReviews = 2847;
    isWritingReview = false;

    newReview = {
        rating: 5,
        title: '',
        content: ''
    };

    ratingBreakdown = [
        { stars: 5, percentage: 85 },
        { stars: 4, percentage: 10 },
        { stars: 3, percentage: 3 },
        { stars: 2, percentage: 1 },
        { stars: 1, percentage: 1 },
    ];

    reviews: Review[] = [
        {
            id: 1,
            author: 'Sarah M.',
            date: 'January 15, 2026',
            rating: 5,
            title: 'Best cushion I\'ve ever used!',
            content: 'I\'ve tried so many cushion foundations and this is by far the best. The coverage is amazing, it lasts all day, and my skin looks so natural and dewy. The 40 shade range made it easy to find my perfect match.',
            verified: true,
            helpful: 128,
            shade: '21N Ivory',
            likedByUser: false
        },
        {
            id: 2,
            author: 'Emily K.',
            date: 'January 12, 2026',
            rating: 5,
            title: 'Perfect match for my skin tone',
            content: 'Finally found a foundation that matches my skin perfectly! The texture is so smooth and it blends like a dream. Definitely repurchasing.',
            verified: true,
            helpful: 89,
            shade: '17C Porcelain',
            likedByUser: false
        },
        {
            id: 3,
            author: 'Jessica L.',
            date: 'January 10, 2026',
            rating: 4,
            title: 'Great coverage, slightly drying',
            content: 'Love the coverage and longevity of this cushion. It looks flawless for hours. Only giving 4 stars because it can be slightly drying on my skin in winter, but using a good moisturizer underneath fixes that.',
            verified: true,
            helpful: 56,
            shade: '23N Sand',
            likedByUser: true
        }
    ];

    getStars(rating: number): number[] {
        return Array(5).fill(0).map((_, i) => i < rating ? 1 : 0);
    }

    getStarClass(filled: number): string {
        return filled ? 'star filled' : 'star empty';
    }

    toggleLike(review: Review) {
        review.likedByUser = !review.likedByUser;
        review.helpful += review.likedByUser ? 1 : -1;
    }

    postReview() {
        if (!this.newReview.title || !this.newReview.content) return;

        const review: Review = {
            id: Date.now(),
            author: 'You (Demo User)',
            date: new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }),
            rating: this.newReview.rating,
            title: this.newReview.title,
            content: this.newReview.content,
            verified: true,
            helpful: 0,
            likedByUser: false
        };

        this.reviews.unshift(review);
        this.isWritingReview = false;
        this.newReview = { rating: 5, title: '', content: '' };
        this.totalReviews++;
    }

    deleteReview(reviewId: number) {
        this.reviews = this.reviews.filter(r => r.id !== reviewId);
        this.totalReviews--;
    }
}
