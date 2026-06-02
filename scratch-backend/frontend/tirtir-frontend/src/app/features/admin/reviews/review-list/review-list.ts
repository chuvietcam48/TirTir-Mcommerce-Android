import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminReviewService, AdminReview } from '../../services/admin-review.service';

@Component({
    selector: 'app-review-list',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="page">
      <div class="page-header"><h1>Reviews</h1></div>
      <div class="table-card"><div class="table-wrapper">
        <table class="admin-table">
          <thead><tr><th>Customer</th><th>Product</th><th>Rating</th><th>Comment</th><th>Verified</th><th>Date</th><th>Action</th></tr></thead>
          <tbody>
            @if (loading) { <tr><td colspan="7" class="empty">Loading...</td></tr> }
            @if (!loading && reviews.length === 0) { <tr><td colspan="7" class="empty">No reviews found</td></tr> }
            @for (r of reviews; track r._id) {
              <tr>
                <td>{{ r.user?.name || 'Anonymous' }}</td>
                <td class="bold">{{ r.product?.Name || '—' }}</td>
                <td><span class="stars">{{ '★'.repeat(r.rating) }}{{ '☆'.repeat(5 - r.rating) }}</span></td>
                <td class="comment">{{ r.comment | slice:0:60 }}{{ r.comment.length > 60 ? '...' : '' }}</td>
                <td>{{ r.verifiedPurchase ? 'Yes' : 'No' }}</td>
                <td class="muted">{{ r.createdAt | date:'dd/MM/yy' }}</td>
                <td><button class="link-btn danger" (click)="remove(r)">Delete</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div></div>
    </div>
  `,
    styleUrl: './review-list.css',
})
export class ReviewListComponent implements OnInit {
    private svc = inject(AdminReviewService);
    reviews: AdminReview[] = []; loading = true;

    ngOnInit() { this.load(); }

    load() {
        this.loading = true;
        this.svc.getAllReviews().subscribe({
            next: (r: any) => { this.reviews = Array.isArray(r) ? r : (r.reviews || r.data || []); this.loading = false; },
            error: () => this.loading = false,
        });
    }

    remove(r: AdminReview) {
        if (!confirm(`Delete review by ${r.user?.name || 'Anonymous'}?`)) return;
        this.svc.deleteReview(r._id).subscribe({ next: () => this.load() });
    }
}
