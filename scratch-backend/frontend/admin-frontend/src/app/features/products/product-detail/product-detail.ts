import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { ProductService } from '../../../core/services/product.service';
import { environment } from '../../../../environments/environment';

@Component({
    selector: 'app-product-detail',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './product-detail.html',
    styleUrls: ['./product-detail.css']
})
export class ProductDetailComponent implements OnInit {
    product: any = null;
    loading = true;
    error: string | null = null;
    deleting = false;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private productService: ProductService
    ) {}

    ngOnInit(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
            this.loadProduct(id);
        } else {
            this.error = 'No product ID provided.';
            this.loading = false;
        }
    }

    loadProduct(id: string): void {
        this.loading = true;
        this.error = null;
        this.productService.getProductById(id).subscribe({
            next: (data) => {
                this.product = data;
                this.loading = false;
            },
            error: (err) => {
                this.error = 'Failed to load product details: ' + (err.error?.message || err.message);
                this.loading = false;
            }
        });
    }

    goToEdit(): void {
        if (this.product && (this.product._id || this.product.id)) {
            this.router.navigate(['/products/edit', this.product._id || this.product.id]);
        }
    }

    confirmDelete(): void {
        if (confirm(`Are you sure you want to delete ${this.product.Name}? This cannot be undone.`)) {
            const id = this.product._id || this.product.id;
            this.deleting = true;
            this.productService.deleteProduct(id).subscribe({
                next: () => {
                    alert('Product deleted successfully.');
                    this.router.navigate(['/products']);
                },
                error: (err) => {
                    this.deleting = false;
                    alert('Failed to delete: ' + (err.error?.message || err.message));
                }
            });
        }
    }

    resolveImageUrl(path: string): string {
        if (!path) return '/assets/placeholder-product.svg';
        if (path.startsWith('http') || path.startsWith('data:')) return path;
        const backendBase = environment.apiUrl.replace('/api/v1', '');
        const clean = path.startsWith('/') ? path.slice(1) : path;
        return `${backendBase}/${clean}`;
    }
}
