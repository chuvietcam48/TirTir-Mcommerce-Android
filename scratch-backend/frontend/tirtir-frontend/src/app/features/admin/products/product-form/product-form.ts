import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AdminProductService, ProductFormData } from '../../services/admin-product.service';

@Component({
    selector: 'app-product-form',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterLink],
    templateUrl: './product-form.html',
    styleUrl: './product-form.css',
})
export class ProductFormComponent implements OnInit {
    private productService = inject(AdminProductService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    isEditMode = false;
    productId: string | null = null;
    loading = false;
    saving = false;

    form: ProductFormData = {
        Name: '',
        Product_ID: '',
        Price: 0,
        Category: '',
        Category_Slug: '',
        Description_Short: '',
        Full_Description: '',
        How_To_Use: '',
        Status: 'Active',
        Stock_Quantity: 0,
        Is_Skincare: false,
        Is_Best_Seller: false,
        Volume_Size: '',
        Skin_Type_Target: '',
        Main_Concern: '',
    };

    categoryOptions = [
        'Cushion', 'Lip', 'Toner', 'Serum', 'Cleanser', 'Cream',
        'Sunscreen', 'Mask', 'Primer', 'Fixer', 'Ampoule', 'Eye Cream',
        'Facial Oil', 'Gift Set'
    ];

    ngOnInit(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
            this.isEditMode = true;
            this.productId = id;
            this.loadProduct(id);
        }
    }

    loadProduct(id: string): void {
        this.loading = true;
        this.productService.getProductDetail(id).subscribe({
            next: (product: any) => {
                this.form = {
                    Name: product.Name || '',
                    Product_ID: product.Product_ID || '',
                    Price: product.Price || 0,
                    Category: product.Category || '',
                    Category_Slug: product.Category_Slug || '',
                    Description_Short: product.Description_Short || '',
                    Full_Description: product.Full_Description || '',
                    How_To_Use: product.How_To_Use || '',
                    Status: product.Status || 'Active',
                    Stock_Quantity: product.Stock_Quantity || 0,
                    Is_Skincare: product.Is_Skincare || false,
                    Is_Best_Seller: product.Is_Best_Seller || false,
                    Volume_Size: product.Volume_Size || '',
                    Skin_Type_Target: product.Skin_Type_Target || '',
                    Main_Concern: product.Main_Concern || '',
                };
                this.loading = false;
            },
            error: (err) => {
                console.error('Failed to load product:', err);
                this.loading = false;
            },
        });
    }

    onCategoryChange(): void {
        this.form.Category_Slug = this.form.Category.toLowerCase().replace(/\s+/g, '-');
        this.form.Is_Skincare = !['Cushion', 'Lip', 'Primer', 'Fixer'].includes(this.form.Category);
    }

    onSubmit(): void {
        this.saving = true;
        const obs = this.isEditMode
            ? this.productService.updateProduct(this.productId!, this.form)
            : this.productService.createProduct(this.form);

        obs.subscribe({
            next: () => {
                this.saving = false;
                this.router.navigate(['/admin/products']);
            },
            error: (err) => {
                console.error('Save failed:', err);
                this.saving = false;
                alert('Failed to save product: ' + (err.error?.message || err.message));
            },
        });
    }
}
