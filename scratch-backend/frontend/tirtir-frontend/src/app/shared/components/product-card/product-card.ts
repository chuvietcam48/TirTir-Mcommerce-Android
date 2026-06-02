import { Component, Input, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { CartService } from '../../../core/services/cart.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './product-card.html',
  styleUrl: './product-card.css',
})
export class ProductCard implements OnDestroy {
  @Input() product: any;
  @Input() id: string = '';
  @Input() name: string = '';
  @Input() subtitle: string = '';
  @Input() price: number = 0;
  @Input() originalPrice?: number;
  @Input() image: string = '';
  @Input() badges: string[] = [];
  @Input() productSlug: string = '';
  @Input() swatches: { color: string }[] = [];
  @Input() shadeCount: number = 0;

  private readonly backendUrl = environment.apiUrl.replace('/api/v1', '');
  private cartService = inject(CartService);
  private authService = inject(AuthService);
  private router = inject(Router);

  quickAddState: 'idle' | 'loading' | 'added' = 'idle';
  private resetTimer: any;

  get displayProduct() {
    if (this.product) {
      return { ...this.product, image: this.getImageUrl(this.product) };
    }
    return {
      id: this.id,
      name: this.name,
      subtitle: this.subtitle,
      price: this.price,
      originalPrice: this.originalPrice,
      image: this.image,
      badges: this.badges,
      slug: this.productSlug,
      swatches: this.swatches,
      shadeCount: this.shadeCount,
    };
  }

  get productLink(): string {
    if (this.product?.slug) return this.product.slug;
    if (this.productSlug) return this.productSlug;
    return this.product?.id || this.product?._id || this.id;
  }

  get productId(): string {
    return this.product?._id || this.product?.id || this.id;
  }

  /** Products with multiple shades need the user to pick a shade first */
  get hasShades(): boolean {
    const count = this.product?.shadeCount
      ?? this.product?.Shades?.length
      ?? this.shadeCount;
    return Number(count) > 1;
  }

  onQuickAdd(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();

    if (this.quickAddState !== 'idle') return;

    // Has shades → send to product page to pick shade
    if (this.hasShades) {
      this.router.navigate(['/products', this.productLink]);
      return;
    }

    // Not logged in → save pending + redirect to login
    if (!this.authService.isAuthenticated()) {
      this.cartService.savePendingItems([{ productId: this.productId, quantity: 1 }]);
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }

    this.quickAddState = 'loading';

    this.cartService.addToCart({ productId: this.productId, quantity: 1 }).subscribe({
      next: () => {
        this.quickAddState = 'added';
        clearTimeout(this.resetTimer);
        this.resetTimer = setTimeout(() => {
          this.quickAddState = 'idle';
        }, 2000);
      },
      error: () => {
        this.quickAddState = 'idle';
      }
    });
  }

  getImageUrl(product: any): string {
    const resolve = (path: string): string => {
      if (!path || typeof path !== 'string' || !path.trim()) return '';
      if (path.startsWith('http')) return path;
      return `${this.backendUrl}/${path.startsWith('/') ? path.slice(1) : path}`;
    };

    const thumb = resolve(product?.Thumbnail_Images);
    if (thumb) return thumb;

    if (Array.isArray(product?.images) && product.images.length > 0) {
      const img = resolve(product.images[0]);
      if (img) return img;
    }

    const legacy = resolve(product?.image);
    if (legacy) return legacy;

    return 'https://placehold.co/400x400/f5f5f5/999?text=No+Image';
  }

  ngOnDestroy(): void {
    clearTimeout(this.resetTimer);
  }
}
