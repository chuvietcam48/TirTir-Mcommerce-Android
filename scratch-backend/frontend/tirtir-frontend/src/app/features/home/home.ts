import { Component, inject, ChangeDetectorRef, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ProductCard } from '../../shared/components/product-card/product-card';
import { ProductCarousel } from '../../shared/components/product-carousel/product-carousel';
import { ProductService } from '../../core/services/product.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule, ProductCard, ProductCarousel],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class HomeComponent implements OnInit, OnDestroy {
  private productService = inject(ProductService);
  private cdr = inject(ChangeDetectorRef); // Injected CDR

  bestSellers: any[] = [];
  newArrivals: any[] = [];
  trending: any[] = [];

  // Carousel state
  currentSlide = 0;
  totalSlides = 3;
  private autoPlayInterval: any;

  allProducts: any[] = [];

  constructor() { }

  ngOnInit() {
    this.loadCatalogAndSetup();
    this.startAutoPlay();
  }

  ngOnDestroy() {
    this.stopAutoPlay();
  }

  loadCatalogAndSetup() {
    this.productService.getProducts({ limit: 1000 }).subscribe({
      next: (response) => {
        this.allProducts = response.data;
        this.setupCollections();
        this.cdr.detectChanges();
      },
      error: (e) => console.error('Failed to load catalog', e)
    });
  }

  setupCollections() {
    // 1. Best Sellers: 4 Fixed + 1 Random
    const bsSlugs = ['mask-fit-red-cushion', 'waterism-glow-tint', 'milk-skin-toner', 'sos-serum'];
    const bsFixed = this.getProductsBySlugs(bsSlugs);
    const bsRandom = this.pickRandom(1, bsSlugs);
    this.bestSellers = [...bsFixed, ...bsRandom];

    // 2. New Arrivals: 6 Fixed + 2 Random
    const naSlugs = [
      'matcha-calming-duo-set',
      'hydro-uv-shield-sunscreen',
      'organic-jojoba-oil',
      'collagen-core-glow-mask',
      'matcha-skin-toner',
      'water-mellow-lip-balm'
    ];
    const naFixed = this.getProductsBySlugs(naSlugs);
    // Exclude BS randoms? User didn't specify, but let's just exclude current section's fixed
    const naRandom = this.pickRandom(2, naSlugs);
    this.newArrivals = [...naFixed, ...naRandom];

    // 3. Trending: 4 Fixed + 1 Random
    const trSlugs = [
      'mask-fit-red-cushion',
      'waterism-glow-tint',
      'mask-fit-aura-cushion',
      'milk-creamy-foam-cleanser'
    ];
    const trFixed = this.getProductsBySlugs(trSlugs);
    // Exclude current section's fixed
    const trRandom = this.pickRandom(1, trSlugs);
    this.trending = [...trFixed, ...trRandom];
  }

  private getProductsBySlugs(slugs: string[]): any[] {
    // Sort to match slug order
    return slugs.map(slug =>
      this.allProducts.find(p => {
        const pSlug = p.slug?.toLowerCase() || p.name.toLowerCase().replace(/\s+/g, '-');
        return pSlug.includes(slug) || slug.includes(pSlug) || p.name.toLowerCase().replace(/\s+/g, '-').includes(slug);
      })
    ).filter(p => p !== undefined);
  }

  private pickRandom(count: number, excludeSlugs: string[]): any[] {
    // Pool: Items NOT in excludeSlugs
    const pool = this.allProducts.filter(p => {
      const pSlug = p.slug?.toLowerCase() || p.name.toLowerCase().replace(/\s+/g, '-');
      // Check if this product matches any excluded slug
      const isExcluded = excludeSlugs.some(ex =>
        pSlug.includes(ex) || ex.includes(pSlug) || p.name.toLowerCase().replace(/\s+/g, '-').includes(ex)
      );
      return !isExcluded;
    });

    // Shuffle and pick
    const shuffled = [...pool].sort(() => 0.5 - Math.random());
    return shuffled.slice(0, count);
  }

  // Carousel Methods
  nextSlide() {
    this.currentSlide = (this.currentSlide + 1) % this.totalSlides;
    this.resetAutoPlay();
  }

  prevSlide() {
    this.currentSlide = (this.currentSlide - 1 + this.totalSlides) % this.totalSlides;
    this.resetAutoPlay();
  }

  goToSlide(index: number) {
    this.currentSlide = index;
    this.resetAutoPlay();
  }

  startAutoPlay() {
    this.autoPlayInterval = setInterval(() => {
      setTimeout(() => {
        this.nextSlide();
        this.cdr.detectChanges();
      }, 0);
    }, 5000); // Change slide every 5 seconds
  }

  stopAutoPlay() {
    if (this.autoPlayInterval) {
      clearInterval(this.autoPlayInterval);
    }
  }

  resetAutoPlay() {
    this.stopAutoPlay();
    this.startAutoPlay();
  }
}
