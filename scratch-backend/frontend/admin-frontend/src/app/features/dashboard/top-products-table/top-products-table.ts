import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardService, TopProduct } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-top-products-table',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './top-products-table.html',
  styleUrls: ['./top-products-table.css']
})
export class TopProductsTableComponent implements OnInit {
  topProducts: TopProduct[] = [];
  loading = true;
  error: string | null = null;

  constructor(private dashboardService: DashboardService) { }

  ngOnInit(): void {
    this.loadTopProducts();
  }

  private loadTopProducts(): void {
    this.dashboardService.getTopProducts().subscribe({
      next: (data) => {
        this.topProducts = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load top products';
        this.loading = false;
        console.error('Top products error:', err);
      }
    });
  }

  getProductImage(product: any): string {
    if (product && product.Thumbnail_Images && product.Thumbnail_Images.length > 0) {
      // Check if it's already a full URL (http) or needs base URL
      const imagePath = product.Thumbnail_Images[0];
      if (imagePath.startsWith('http')) {
        return imagePath;
      }
      // Assuming relative path from backend uploads
      // We need environment.apiUrl without /api/v1 if images are served from root/uploads
      // But usually images are static assets.
      // Let's try prepending the base URL (e.g. http://localhost:5001/)
      // environment.apiUrl is likely http://localhost:5001/api/v1
      const baseUrl = 'http://localhost:5001';
      if (imagePath.startsWith('/')) {
        return `${baseUrl}${imagePath}`;
      }
      return `${baseUrl}/${imagePath}`;
    }
    return 'assets/placeholder-product.png';
  }
}
