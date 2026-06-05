import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { InventoryService } from '../../../core/services/inventory.service';
import { ProductService } from '../../../core/services/product.service';

@Component({
    selector: 'app-stock-adjustment',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, RouterModule],
    templateUrl: './stock-adjustment.html',
    styleUrls: ['./stock-adjustment.css']
})
export class StockAdjustmentComponent implements OnInit {
    adjustmentForm: FormGroup;
    product: any = null;
    loading = true;
    submitting = false;
    error: string | null = null;
    projectedStock: number | null = null;

    constructor(
        private fb: FormBuilder,
        private inventoryService: InventoryService,
        private productService: ProductService,
        private router: Router,
        private route: ActivatedRoute
    ) {
        this.adjustmentForm = this.fb.group({
            action: ['add', Validators.required],
            quantity: [1, [Validators.required, Validators.min(1)]],
            reason: ['', Validators.required]
        });
    }

    ngOnInit(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
            this.loadProduct(id);
        } else {
            this.error = 'No product ID provided';
            this.loading = false;
        }

        // Recalculate projected stock on form changes
        this.adjustmentForm.valueChanges.subscribe(() => {
            this.calculateProjectedStock();
        });
    }

    loadProduct(id: string): void {
        this.loading = true;
        this.productService.getProductById(id).subscribe({
            next: (product) => {
                this.product = product;
                this.calculateProjectedStock();
                this.loading = false;
            },
            error: (err) => {
                console.error('Product load error:', err);
                this.error = 'Failed to load product details';
                this.loading = false;
            }
        });
    }

    calculateProjectedStock(): void {
        if (!this.product) return;

        const currentStock = this.product.stock || 0; // Assuming 'stock' or 'Stock_Quantity'
        // Let's try to handle different case conventions if needed, but 'stock' is standard
        const stock = this.product.Stock_Quantity ?? this.product.stock ?? 0;

        const { action, quantity } = this.adjustmentForm.value;
        const qty = Number(quantity) || 0;

        switch (action) {
            case 'add':
                this.projectedStock = stock + qty;
                break;
            case 'remove':
                this.projectedStock = Math.max(0, stock - qty);
                break;
            case 'set':
                this.projectedStock = Math.max(0, qty);
                break;
            default:
                this.projectedStock = stock;
        }
    }

    onSubmit(): void {
        if (this.adjustmentForm.invalid || !this.product) {
            this.markFormGroupTouched(this.adjustmentForm);
            return;
        }

        this.submitting = true;
        this.error = null;

        const adjustmentData = {
            productId: this.product._id,
            ...this.adjustmentForm.value,
            quantity: Number(this.adjustmentForm.value.quantity)
        };

        this.inventoryService.adjustStock(adjustmentData).subscribe({
            next: () => {
                this.router.navigate(['/inventory']);
            },
            error: (err) => {
                console.error('Adjustment error:', err);
                this.error = 'Failed to update stock';
                this.submitting = false;
            }
        });
    }

    private markFormGroupTouched(formGroup: FormGroup) {
        Object.values(formGroup.controls).forEach(control => {
            control.markAsTouched();
        });
    }
}
