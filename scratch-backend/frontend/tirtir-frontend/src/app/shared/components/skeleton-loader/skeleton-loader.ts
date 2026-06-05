import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export type SkeletonType = 'product-card' | 'product-detail' | 'cart-item' | 'list-item' | 'filter-bar';

@Component({
  selector: 'app-skeleton-loader',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton-wrapper" [ngClass]="type">
      <div class="shimmer"></div>
      
      <!-- Product Card Skeleton -->
      <div *ngIf="type === 'product-card'" class="card-skeleton">
        <div class="img-skeleton"></div>
        <div class="text-skeleton title"></div>
        <div class="text-skeleton price"></div>
      </div>

      <!-- Product Detail Skeleton -->
      <div *ngIf="type === 'product-detail'" class="detail-skeleton">
        <div class="gallery-column">
          <div class="img-skeleton main"></div>
          <div class="thumb-row">
            <div class="img-skeleton thumb"></div>
            <div class="img-skeleton thumb"></div>
            <div class="img-skeleton thumb"></div>
          </div>
        </div>
        <div class="info-column">
          <div class="text-skeleton title-lg"></div>
          <div class="text-skeleton price-md"></div>
          <div class="text-skeleton desc"></div>
          <div class="text-skeleton desc"></div>
          <div class="btn-skeleton"></div>
        </div>
      </div>

      <!-- Filter Bar Skeleton -->
      <div *ngIf="type === 'filter-bar'" class="filter-bar-skeleton">
        <div class="text-skeleton toggle"></div>
        <div class="text-skeleton sort"></div>
      </div>

      <!-- Cart Item Skeleton -->
      <div *ngIf="type === 'cart-item'" class="cart-skeleton">
        <div class="img-skeleton small"></div>
        <div class="content-skeleton">
          <div class="text-skeleton title"></div>
          <div class="text-skeleton meta"></div>
        </div>
        <div class="price-skeleton"></div>
      </div>
    </div>
  `,
  styles: [`
    .skeleton-wrapper {
      position: relative;
      overflow: hidden;
      background: #f5f5f5;
      border-radius: 4px;
    }

    .shimmer {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: linear-gradient(
        90deg,
        rgba(255, 255, 255, 0) 0%,
        rgba(255, 255, 255, 0.4) 50%,
        rgba(255, 255, 255, 0) 100%
      );
      animation: shimmer 1.5s infinite;
    }

    @keyframes shimmer {
      0% { transform: translateX(-100%); }
      100% { transform: translateX(100%); }
    }

    .text-skeleton {
      background: #e0e0e0;
      border-radius: 2px;
      margin-bottom: 0.5rem;
    }

    .img-skeleton {
      background: #e0e0e0;
      width: 100%;
    }

    /* Product Card */
    .card-skeleton {
      padding: 0;
    }
    .card-skeleton .img-skeleton { aspect-ratio: 1/1.2; }
    .card-skeleton .title { height: 1rem; width: 80%; margin-top: 1rem; }
    .card-skeleton .price { height: 1rem; width: 40%; }

    /* Product Detail */
    .detail-skeleton {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 3rem;
    }
    .main { aspect-ratio: 1/1.2; }
    .thumb-row { display: flex; gap: 1rem; margin-top: 1rem; }
    .thumb { width: 80px; height: 80px; }
    .title-lg { height: 2.5rem; width: 90%; }
    .price-md { height: 1.5rem; width: 30%; margin: 1.5rem 0; }
    .desc { height: 1rem; width: 100%; }
    .btn-skeleton { height: 3.5rem; width: 100%; margin-top: 2rem; background: #e0e0e0; }

    /* Filter Bar */
    .filter-bar-skeleton {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 0;
      height: 60px;
      width: 100%;
    }
    .filter-bar-skeleton .toggle { width: 150px; height: 1.5rem; margin: 0; }
    .filter-bar-skeleton .sort { width: 200px; height: 2rem; margin: 0; }

    /* Cart Item */
    .cart-skeleton {
      display: flex;
      align-items: center;
      gap: 1.5rem;
      padding: 1rem 0;
    }
    .img-skeleton.small { width: 80px; height: 100px; }
    .content-skeleton { flex: 1; }
    .content-skeleton .title { height: 1.1rem; width: 60%; }
    .content-skeleton .meta { height: 0.8rem; width: 30%; }
    .price-skeleton { width: 60px; height: 1.1rem; background: #e0e0e0; }
  `]
})
export class SkeletonLoaderComponent {
  @Input() type: SkeletonType = 'product-card';
}
