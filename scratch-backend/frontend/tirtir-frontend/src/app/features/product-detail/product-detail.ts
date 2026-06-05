import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { forkJoin, timer } from 'rxjs';
import { BrandGalleryComponent } from '../../shared/components/brand-gallery/brand-gallery';
import { CustomerReviewsComponent } from '../../shared/components/customer-reviews/customer-reviews';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner';
import { getProductBySlug, ProductData, PRODUCTS } from '../../core/constants/products.data';
import { ProductService } from '../../core/services/product.service';
import { CartService } from '../../core/services/cart.service';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, CustomerReviewsComponent, LoadingSpinnerComponent],
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.css',
})
export class ProductDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private productService = inject(ProductService);
  private cartService = inject(CartService);

  private cdr = inject(ChangeDetectorRef); // Re-inject for robust fix

  product!: ProductData;
  suggestedProducts: any[] = [];
  selectedImage: string = '';
  selectedShade: string = '';
  quantity = 1;
  activeAccordion: string | null = 'description';
  addingToCart = false;
  loading = true;

  constructor() { }

  ngOnInit() {
    this.route.params.subscribe(params => {
      const slug = params['slug'];
      this.loading = true;

      // Ensure the loading spinner is visible for at least 800ms
      // for a smoother transition even on fast connections.
      forkJoin([
        this.productService.getProductDetail(slug),
        timer(800)
      ]).subscribe({
        next: ([data]) => {
          this.product = data;
          this.selectedImage = this.product.images[0];
          if (this.product.shades && this.product.shades.length > 1) {
            const midIndex = Math.floor(this.product.shades.length / 2);
            this.selectedShade = this.product.shades[midIndex].name;
          }
          this.fetchSuggestions();
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: (err: any) => {
          console.error('Product not found', err);
          this.loading = false;
        }
      });
    });
  }

  fetchSuggestions() {
    const category = this.product?.category || 'skincare';
    this.productService.getProducts({ category, limit: 5 }).subscribe(res => {
      this.suggestedProducts = res.data
        .filter((p: any) => p.slug !== this.product.slug)
        .slice(0, 4); // Suggest up to 4 related products
    });
  }

  scrollToReviews() {
    const el = document.getElementById('reviews-section');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  }

  selectImage(img: string) {
    this.selectedImage = img;
  }

  selectShade(shadeName: string) {
    this.selectedShade = shadeName;

    // Find the shade object to get its image
    const shadeObj = this.product.shades?.find(s => s.name === shadeName);
    if (shadeObj && shadeObj.image) {
      this.selectedImage = shadeObj.image;
    }
  }

  increaseQty() {
    this.quantity++;
  }

  decreaseQty() {
    if (this.quantity > 1) this.quantity--;
  }

  addToCart() {
    if (!this.product) return;

    const hasMultipleShades = Array.isArray(this.product.shades) && this.product.shades.length > 1;

    if (hasMultipleShades && !this.selectedShade) {
      alert('Please select a shade');
      return;
    }

    this.addingToCart = true;

    this.cartService.addToCart({
      productId: (this.product as any)._id || this.product.id,
      quantity: this.quantity,
      shade: hasMultipleShades ? (this.selectedShade || undefined) : undefined
    }).subscribe({
      next: (cart) => {
        setTimeout(() => {
          this.addingToCart = false;
          alert(`Added ${this.quantity} item(s) to cart!`);
          this.cdr.detectChanges();
        }, 0);
      },
      error: (err: any) => {
        setTimeout(() => {
          this.addingToCart = false;
          console.error('Add to cart failed', err);
          alert(err.message || 'Failed to add to cart');
          this.cdr.detectChanges();
        }, 0);
      }
    });
  }

  toggleAccordion(section: string) {
    this.activeAccordion = this.activeAccordion === section ? null : section;
  }

  getStars(rating: number): number[] {
    return Array(5).fill(0).map((_, i) => i < Math.round(rating) ? 1 : 0);
  }
}

