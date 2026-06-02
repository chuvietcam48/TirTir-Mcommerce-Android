import { Component, Input, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProductCard } from '../product-card/product-card';

@Component({
  selector: 'app-product-carousel',
  standalone: true,
  imports: [CommonModule, ProductCard],
  templateUrl: './product-carousel.html',
  styleUrl: './product-carousel.css',
})
export class ProductCarousel implements AfterViewInit {
  @Input() products: any[] = [];
  @Input() title: string = '';
  @Input() subtitle: string = '';

  @ViewChild('carouselTrack') carouselTrack!: ElementRef;

  ngAfterViewInit() {
    // Setup infinite scroll listener
    if (this.carouselTrack) {
      const track = this.carouselTrack.nativeElement;
      track.addEventListener('scroll', () => this.checkScrollPosition());
    }
  }

  scrollLeft() {
    const track = this.carouselTrack.nativeElement;
    const scrollAmount = 300;

    // If at the start, jump to the end
    if (track.scrollLeft <= 0) {
      track.scrollLeft = track.scrollWidth - track.clientWidth;
    } else {
      track.scrollBy({ left: -scrollAmount, behavior: 'smooth' });
    }
  }

  scrollRight() {
    const track = this.carouselTrack.nativeElement;
    const scrollAmount = 300;

    // If at the end, jump to the start
    if (track.scrollLeft + track.clientWidth >= track.scrollWidth - 1) {
      track.scrollLeft = 0;
    } else {
      track.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  }

  private checkScrollPosition() {
    const track = this.carouselTrack.nativeElement;
    const maxScroll = track.scrollWidth - track.clientWidth;

    // Optional: Auto-loop when reaching the end (smooth transition)
    if (track.scrollLeft >= maxScroll - 1) {
      // Reached end - could auto-loop here if desired
    } else if (track.scrollLeft <= 0) {
      // Reached start
    }
  }
}
