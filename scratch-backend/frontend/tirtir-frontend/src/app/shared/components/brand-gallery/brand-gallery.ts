import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-brand-gallery',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './brand-gallery.html',
    styleUrl: './brand-gallery.css',
})
export class BrandGalleryComponent {
    // Placeholder images - replace with actual TIRTIR brand images
    galleryImages = [
        {
            src: 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=600&h=400&fit=crop',
            alt: 'TIRTIR Brand Lifestyle 1',
        },
        {
            src: 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=600&h=400&fit=crop',
            alt: 'TIRTIR Brand Lifestyle 2',
        },
        {
            src: 'https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?w=600&h=400&fit=crop',
            alt: 'TIRTIR Brand Lifestyle 3',
        },
        {
            src: 'https://images.unsplash.com/photo-1512496015851-a90fb38ba796?w=600&h=400&fit=crop',
            alt: 'TIRTIR Brand Lifestyle 4',
        },
        {
            src: 'https://images.unsplash.com/photo-1516975080664-ed2fc6a32937?w=600&h=400&fit=crop',
            alt: 'TIRTIR Brand Lifestyle 5',
        },
        {
            src: 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=600&h=400&fit=crop',
            alt: 'TIRTIR Brand Lifestyle 6',
        },
    ];
}
