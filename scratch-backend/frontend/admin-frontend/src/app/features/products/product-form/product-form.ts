import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Observable, Subject, of } from 'rxjs';
import { tap, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { ProductService } from '../../../core/services/product.service';
import { environment } from '../../../../environments/environment';

@Component({
    selector: 'app-product-form',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, RouterModule, FormsModule],
    templateUrl: './product-form.html',
    styleUrls: ['./product-form.css']
})
export class ProductFormComponent implements OnInit {
    productForm!: FormGroup;
    isEditMode = false;
    productId: string | null = null;
    loading = false;
    uploadingImage = false;
    uploadingVariantImageIndex: number | null = null;
    processingColorIndex: number | null = null;
    error: string | null = null;
    imagePreview: string | null = null;
    
    expandedPanels: boolean[] = [];

    // Smart Suggester Subject
    productNameSubject = new Subject<string>();
    parentSuggestions: any[] = [];

    categories = ['Makeup', 'Skincare', 'Gift Card', 'Cushion', 'Tint', 'Serum', 'Lotion', 'Toner', 'Cleanser', 'Sunscreen', 'Mask'];
    statuses = ['Active', 'Inactive', 'Archived'];

    // Chips Options
    skinTones = ['Fair', 'Light', 'Medium', 'Tan', 'Deep', 'All Skin Tone'];
    skinTypes = ['Oily', 'Dry', 'Combination', 'Oily/Combination', 'Normal', 'All Types'];
    undertones = ['Warm', 'Cool', 'Neutral', 'Olive'];
    finishTypes = ['Matte', 'Dewy', 'Natural', 'Satin', 'Gloss'];
    coverages = ['Sheer', 'Light', 'Medium', 'Full'];
    coverageProfiles = ['Color Correcting', 'Buildable', 'Transfer-proof', 'Long-wear'];
    oxidationLevels = ['None', 'Low', 'Medium', 'High'];
    hydrations = ['1','2','3','4','5'];
    
    isGeneratingId = false;

    // File Upload State
    thumbnailFile: File | null = null;
    thumbnailPreview: string | null = null;
    
    galleryFiles: { file: File, preview: string }[] = [];
    descriptionFiles: { file: File, preview: string }[] = [];

    constructor(
        private fb: FormBuilder,
        private productService: ProductService,
        private router: Router,
        private route: ActivatedRoute
    ) { }

    ngOnInit(): void {
        this.initForm();
        this.productId = this.route.snapshot.paramMap.get('id');
        if (this.productId) {
            this.isEditMode = true;
            this.productForm.get('Thumbnail_Images')?.clearValidators();
            this.productForm.get('Thumbnail_Images')?.updateValueAndValidity();
            this.loadProduct(this.productId);
        }
    }

    initForm(): void {
        this.productForm = this.fb.group({
            Category: ['Makeup', [Validators.required]],
            Category_Slug: [{ value: 'makeup', disabled: true }],
            Product_Name: ['', [Validators.required, Validators.minLength(3)]],
            Product_Slug: [''],
            Parent_ID: [''],
            Product_ID: ['', [Validators.required, Validators.pattern(/^[A-Z0-9\-_]+$/)]], // SKU
            Is_Auto_Generated_ID: [false],
            
            Price: [0, [Validators.required, Validators.min(0.01)]],
            Sale_Price: [0, [Validators.min(0)]],
            Volume_Size: [''],
            Is_Skincare: [false],
            Skin_Type_Target: [''],
            Main_Concern: [''],
            Key_Ingredients: [''],
            Description_Short: [''],
            How_To_Use: [''],
            Status: ['Active', [Validators.required]],
            Stock_Quantity: [0, [Validators.required, Validators.min(0)]],
            Stock_Reserved: [0, [Validators.min(0)]],
            Full_Description: [''],
            Thumbnail_Images: ['', [Validators.required]],
            Gallery_Images: this.fb.array([]),

            Has_Variants: [false],
            shades: this.fb.array([])
        });

        // 1. Auto-generate slug from name
        this.productForm.get('Product_Name')?.valueChanges.subscribe(name => {
            if (name) {
                if (!this.isEditMode) {
                    const slug = this.generateSlug(name);
                    this.productForm.get('Product_Slug')?.setValue(slug, { emitEvent: false });
                }
                this.productNameSubject.next(name);
            }
        });

        // Smart Debounce for Parent_ID mapping
        this.productNameSubject.pipe(
            debounceTime(300),
            distinctUntilChanged(),
            switchMap(name => {
                const cat = this.productForm.get('Category')?.value || '';
                if(name.length > 2) return this.productService.suggestParent(name, cat);
                return of([]);
            })
        ).subscribe(suggestions => {
            this.parentSuggestions = suggestions;
        });

        this.productForm.get('Parent_ID')?.valueChanges.subscribe(() => {
            this.productForm.patchValue({ Is_Auto_Generated_ID: false }, {emitEvent: false}); 
        });

        // 2. Auto-fill category slug
        this.productForm.get('Category')?.valueChanges.subscribe(cat => {
            if (cat) {
                const slug = cat.toLowerCase().replace(/\s+/g, '-');
                this.productForm.get('Category_Slug')?.setValue(slug); 
            }
        });
        
        // 3. Cascade logic for Shades identifiers
        const cascadeShades = () => {
            const parentProdId = this.productForm.get('Product_ID')?.value || '';
            const parentName = this.productForm.get('Product_Name')?.value || '';
            const parentId = this.productForm.get('Parent_ID')?.value || '';
            
            this.shades.controls.forEach(shadeCtrl => {
                const code = shadeCtrl.get('Shade_Code')?.value || '';
                const idBase = parentId || parentProdId; // Prefer Series ID/Parent_ID for consistency
                shadeCtrl.patchValue({
                    Product_ID: parentProdId,
                    Shade_Category_Name: parentName,
                    Parent_ID: parentId,
                    Shade_ID: idBase && code ? `${idBase}-${code}` : ''
                }, { emitEvent: false });
            });
        };

        this.productForm.get('Product_ID')?.valueChanges.subscribe(cascadeShades);
        this.productForm.get('Product_Name')?.valueChanges.subscribe(cascadeShades);
        this.productForm.get('Parent_ID')?.valueChanges.subscribe(cascadeShades);

        this.productForm.get('Has_Variants')?.valueChanges.subscribe(hasVars => {
            if (hasVars && this.shades.length === 0) {
                this.addEmptyShade();
            }
        });
    }

    generateSlug(text: string): string {
        return text.toString().toLowerCase()
            .normalize('NFD').replace(/[\u0300-\u036f]/g, "")
            .trim()
            .replace(/\s+/g, '-')
            .replace(/[^\w\-]+/g, '')
            .replace(/\-\-+/g, '-');
    }

    selectParentSuggestion(suggest: any): void {
        this.productForm.patchValue({ Parent_ID: suggest.Parent_ID });
        this.parentSuggestions = [];
    }

    // Smart ID generator asking backend
    generateSmartSKU(): void {
        const title = this.productForm.get('Product_Name')?.value;
        const cat = this.productForm.get('Category')?.value;
        const parentId = this.productForm.get('Parent_ID')?.value;
        
        if (!title || !cat) return;

        this.isGeneratingId = true;
        this.productService.generateSmartIds(title, cat, parentId).subscribe({
            next: (res) => {
                this.productForm.patchValue({ 
                    Product_ID: res.suggestedProductID,
                    Is_Auto_Generated_ID: true
                });
                
                // For Parent ID, only patch if empty to not override explicit user choice
                if (!this.productForm.get('Parent_ID')?.value && res.suggestedShadeID) {
                    this.productForm.patchValue({ Parent_ID: res.suggestedShadeID.split('-')[0] + '-' + res.suggestedShadeID.split('-')[1] });
                }
                this.isGeneratingId = false;
            },
            error: (err) => {
                console.error("Smart ID Error:", err);
                this.isGeneratingId = false;
            }
        });
    }

    // ─── SHADES / VARIANTS LOGIC ────────────────────────────────
    get hasVariants(): boolean { return this.productForm.get('Has_Variants')?.value; }
    get shades(): FormArray { return this.productForm.get('shades') as FormArray; }

    addEmptyShade(): void {
        const parentProdId = this.productForm.get('Product_ID')?.value || '';
        const parentName = this.productForm.get('Product_Name')?.value || '';
        const parentId = this.productForm.get('Parent_ID')?.value || '';
        
        const shadeGroup = this.fb.group({
            _id: [''],
            Shade_Code: ['', Validators.required],
            Shade_Category_Name: [{value: parentName, disabled: true}],
            Parent_ID: [{value: parentId, disabled: true}],
            Shade_ID: [{value: '', disabled: true}], 
            Product_ID: [{value: parentProdId, disabled: true}],
            
            Shade_Name: ['', Validators.required],
            Skin_Tone: [''],
            Skin_Type: [''],
            Undertone: [''],
            
            Finish_Type: [''],
            Hydration: [''],
            Coverage: [''],
            Coverage_Profile: [''],
            Oxidation_Level: [''],
            Oxidation_Risk_Level: [''],
            
            Shade_Image: [''],
            
            Hex_Code: [''],
            Stock_Quantity: [0, [Validators.min(0)]],
            R: [{value: null, disabled: true}],
            G: [{value: null, disabled: true}],
            B: [{value: null, disabled: true}],
            L: [{value: null, disabled: true}],
            a: [{value: null, disabled: true}],
            b: [{value: null, disabled: true}]
        });

        // Auto ID listeners
        shadeGroup.get('Shade_Code')?.valueChanges.subscribe(code => {
            const currentParentProdId = this.productForm.get('Product_ID')?.value || '';
            const currentParentId = this.productForm.get('Parent_ID')?.value || ''; // Series ID
            const idBase = currentParentId || currentParentProdId;
            
            if (idBase && code) {
                shadeGroup.patchValue({ Shade_ID: `${idBase}-${code}` }, { emitEvent: false });
            }
        });
        
        // Hex Color listener for backend smart extraction
        shadeGroup.get('Hex_Code')?.valueChanges.pipe(
            debounceTime(500),
            distinctUntilChanged()
        ).subscribe(hex => {
            if (hex && hex.startsWith('#') && (hex.length === 4 || hex.length === 7)) {
                this.productService.extractColorFromHex(hex).subscribe(res => {
                     shadeGroup.patchValue({ R: res.r, G: res.g, B: res.b, L: res.L, a: res.a, b: res.b });
                });
            } else if (!hex) {
                shadeGroup.patchValue({ R: null, G: null, B: null, L: null, a: null, b: null });
            }
        });

        this.shades.push(shadeGroup);
        this.expandedPanels.push(true); 
    }

    removeShade(index: number): void {
        if(confirm('Are you sure you want to remove this shade?')) {
            this.shades.removeAt(index);
            this.expandedPanels.splice(index, 1);
        }
    }
    
    togglePanel(index: number): void {
        this.expandedPanels[index] = !this.expandedPanels[index];
    }

    getVariantImagePreview(index: number): string {
        const control = this.shades.at(index).get('Shade_Image');
        return control ? this.resolveImageUrl(control.value) : '';
    }

    onVariantImageChange(event: any, index: number): void {
        const file = event.target.files[0];
        if (file) {
            this.uploadingVariantImageIndex = index;
            // 1. Upload to assets
            this.productService.uploadImage(file).subscribe({
                next: (response: any) => {
                    this.uploadingVariantImageIndex = null;
                    if (response.success) {
                        this.shades.at(index).patchValue({ Shade_Image: response.data.url });
                    }
                },
                error: () => {
                    this.uploadingVariantImageIndex = null;
                    this.showToast('Failed to upload variant image');
                }
            });

            // 2. Extractor process (Parallel)
            this.processingColorIndex = index;
            this.productService.extractColorFromImage(file).subscribe({
                next: (res) => {
                    this.processingColorIndex = null;
                    this.shades.at(index).patchValue({ 
                        Hex_Code: res.hex, 
                        R: res.r, G: res.g, B: res.b, 
                        L: res.L, a: res.a, b: res.b 
                    });
                },
                error: () => this.processingColorIndex = null
            });
        }
    }

    setChip(groupIndex: number, field: string, value: string): void {
        const control = this.shades.at(groupIndex).get(field);
        if (control?.value === value) {
            control.setValue(''); // toggle off
        } else {
            control?.setValue(value);
        }
    }

    // ─── FILE UPLOAD HANDLERS ──────────────────────────────────────────────
    onThumbnailChange(event: any): void {
        const file = event.target.files[0];
        if (file) {
            this.thumbnailFile = file;
            this.thumbnailPreview = URL.createObjectURL(file);
        }
    }
    
    removeThumbnail(): void {
        this.thumbnailFile = null;
        this.thumbnailPreview = null;
    }
    
    onGalleryChange(event: any): void {
        const files = Array.from(event.target.files) as File[];
        files.forEach(file => {
            this.galleryFiles.push({ file, preview: URL.createObjectURL(file) });
        });
        event.target.value = '';
    }
    
    removeGalleryImage(index: number): void {
        this.galleryFiles.splice(index, 1);
    }
    
    onDescriptionChange(event: any): void {
        const files = Array.from(event.target.files) as File[];
        files.forEach(file => {
            this.descriptionFiles.push({ file, preview: URL.createObjectURL(file) });
        });
        event.target.value = '';
    }
    
    removeDescriptionImage(index: number): void {
        this.descriptionFiles.splice(index, 1);
    }

    get galleryImages(): FormArray { return this.productForm.get('Gallery_Images') as FormArray; }
    // ─── Loading & Submitting ─────────────────────────────────
    loadProduct(id: string): void {
        this.loading = true;
        this.productService.getProduct(id).subscribe({
            next: (product: any) => {
                // ── Reset all media state ──
                this.thumbnailFile = null;
                this.thumbnailPreview = null;
                this.galleryFiles = [];
                this.descriptionFiles = [];

                // ── Restore Thumbnail ──
                const thumbUrl = typeof product.Thumbnail_Images === 'string'
                    ? product.Thumbnail_Images
                    : (Array.isArray(product.Thumbnail_Images) ? product.Thumbnail_Images[0] : '');
                if (thumbUrl) {
                    this.thumbnailPreview = this.resolveImageUrl(thumbUrl);
                }

                // ── Restore Gallery Images ──
                const galleryUrls = product.Gallery_Images || [];
                if (Array.isArray(galleryUrls)) {
                    galleryUrls.forEach((url: string) => {
                        if (url) {
                            this.galleryFiles.push({ file: new File([], 'existing'), preview: this.resolveImageUrl(url) });
                        }
                    });
                }

                // ── Restore Description Images ──
                const descUrls = product.Description_Images || product.descriptionImages || [];
                if (Array.isArray(descUrls)) {
                    descUrls.forEach((url: string) => {
                        if (url) {
                            this.descriptionFiles.push({ file: new File([], 'existing'), preview: this.resolveImageUrl(url) });
                        }
                    });
                }

                // ── Restore Shades ──
                this.shades.clear();
                this.expandedPanels = [];
                if (product.shades && Array.isArray(product.shades) && product.shades.length > 0) {
                    product.shades.forEach((s: any) => {
                        this.addEmptyShade(); 
                        const idx = this.shades.length - 1;
                        this.expandedPanels[idx] = false; 
                        this.shades.at(idx).patchValue({
                            _id: s._id || '',
                            Shade_Code: s.Shade_Code || '',
                            Shade_Category_Name: s.Shade_Category_Name || product.Name || '',
                            Parent_ID: s.Parent_ID || product.Parent_ID || '',
                            Shade_ID: s.Shade_ID || '',
                            Product_ID: s.Product_ID || product.Product_ID,
                            Shade_Name: s.Shade_Name || '',
                            Skin_Tone: s.Skin_Tone || '',
                            Skin_Type: s.Skin_Type || '',
                            Undertone: s.Undertone || '',
                            Finish_Type: s.Finish_Type || '',
                            Hydration: s.Hydration?.toString() || '',
                            Coverage: s.Coverage || '',
                            Coverage_Profile: s.Coverage_Profile || '',
                            Oxidation_Level: s.Oxidation_Level || '',
                            Oxidation_Risk_Level: s.Oxidation_Risk_Level || '',
                            Shade_Image: s.Shade_Image || s.image || '',
                            Hex_Code: s.Hex_Code || s.color || '',
                            Stock_Quantity: s.Stock_Quantity || 0,
                            R: s.R, G: s.G, B: s.B, L: s.L, a: s.a, b: s.b
                        });
                    });
                }

                // ── Patch main form fields ──
                this.productForm.patchValue({
                    Product_ID: product.Product_ID || '',
                    Parent_ID: product.Parent_ID || '',
                    Category: product.Category || 'Makeup',
                    Category_Slug: product.Category_Slug || '',
                    Product_Name: product.Product_Name || product.Name || '',
                    Product_Slug: product.Product_Slug || product.slug || '',
                    Price: product.Price || 0,
                    Sale_Price: product.Sale_Price || 0,
                    Volume_Size: product.Volume_Size || '',
                    Is_Skincare: product.Is_Skincare || false,
                    Skin_Type_Target: product.Skin_Type_Target || '',
                    Main_Concern: product.Main_Concern || '',
                    Key_Ingredients: product.Key_Ingredients || '',
                    Description_Short: product.Description_Short || product.description || '',
                    How_To_Use: product.How_To_Use || '',
                    Status: product.Status || 'Active',
                    Stock_Quantity: product.Stock_Quantity || 0,
                    Stock_Reserved: product.Stock_Reserved || 0,
                    Full_Description: product.Full_Description || '',
                    Has_Variants: (product.shades && product.shades.length > 0)
                });
                
                this.loading = false;
            },
            error: (err: any) => {
                this.error = 'Failed to load product';
                this.loading = false;
            }
        });
    }

    onImageChange(event: any): void {
        const file = event.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = (e: any) => { this.imagePreview = e.target.result; };
            reader.readAsDataURL(file);
            this.uploadingImage = true;
            this.productService.uploadImage(file).subscribe({
                next: (response: any) => {
                    this.uploadingImage = false;
                    if (response.success) {
                        this.productForm.patchValue({ Thumbnail_Images: response.data.url });
                    }
                },
                error: () => {
                    this.uploadingImage = false;
                    this.error = 'Failed to upload image';
                }
            });
        }
    }

    resolveImageUrl(path: string): string {
        if (!path) return '';
        if (path.startsWith('data:') || path.startsWith('http')) return path;
        const backendBase = environment.apiUrl.replace('/api/v1', '');
        const clean = path.startsWith('/') ? path.slice(1) : path;
        return `${backendBase}/${clean}`;
    }

    showToast(message: string): void { alert(message); }

    onSubmit(): void {
        if (this.productForm.invalid || this.uploadingImage) {
            Object.keys(this.productForm.controls).forEach(key => {
                const control = this.productForm.get(key);
                control?.markAsTouched();
                if (key === 'Gallery_Images') (control as FormArray).controls.forEach(c => c.markAsTouched());
                if (key === 'shades') (control as FormArray).controls.forEach(c => c.markAllAsTouched());
            });
            if (this.uploadingImage) this.error = 'Please wait for image upload to complete';
            this.showToast('Please check the form for errors.');
            return;
        }

        this.loading = true;
        this.error = null;

        const formValues = this.productForm.getRawValue(); 

        const formData = new FormData();
        
        // Append primitive fields
        Object.keys(formValues).forEach(key => {
            if (key !== 'shades' && key !== 'Thumbnail_Images' && key !== 'Gallery_Images' && key !== 'Description_Images' && key !== 'variants' && key !== 'Is_Auto_Generated_ID') {
                if (formValues[key] !== null && formValues[key] !== undefined) {
                    // special handling
                    if (key === 'Is_Skincare') {
                        formData.append(key, formValues[key] === true || formValues[key] === 'true' ? 'true' : 'false');
                    } else if (key === 'Price' || key === 'Sale_Price' || key === 'Stock_Quantity' || key === 'Stock_Reserved') {
                        formData.append(key, Number(formValues[key]).toString());
                    } else if (key === 'Product_Name') {
                        formData.append('Name', formValues[key]);
                    } else if (key === 'Product_Slug') {
                        formData.append('slug', formValues[key]);
                    } else {
                        formData.append(key, formValues[key].toString());
                    }
                }
            }
        });
        
        if (formValues.Has_Variants && formValues.shades && formValues.shades.length > 0) {
            const processedShades = formValues.shades.map((s: any) => ({
                ...s,
                Product_ID: formValues.Product_ID
            }));
            formData.append('shades', JSON.stringify(processedShades));
        }

        // Append File Objects
        if (this.thumbnailFile) {
            formData.append('thumbnail', this.thumbnailFile);
        } else if (this.thumbnailPreview && this.thumbnailPreview.startsWith('http')) {
            formData.append('Thumbnail_Images', this.thumbnailPreview); // keep existing URL
        }

        this.galleryFiles.forEach(gf => {
            if (gf.preview.startsWith('http')) {
                formData.append('existingGallery', gf.preview);
            } else {
                formData.append('gallery', gf.file);
            }
        });

        this.descriptionFiles.forEach(df => {
            if (df.preview.startsWith('http')) {
                formData.append('existingDescription', df.preview);
            } else {
                formData.append('descriptionUrl', df.file);
            }
        });

        const save$ = this.isEditMode && this.productId
            ? this.productService.updateProduct(this.productId, formData as any)
            : this.productService.addProduct(formData as any);

        save$.subscribe({
            next: (res: any) => {
                this.showToast('Product saved successfully!');
                const finalId = res._id || res.id || this.productId;
                if (this.isEditMode && finalId) {
                    this.router.navigate(['/products/detail', finalId]);
                } else {
                    this.router.navigate(['/products']);
                }
            },
            error: (err: any) => {
                this.error = err.error?.message || (this.isEditMode ? 'Failed to update product' : 'Failed to create product');
                this.loading = false;
            }
        });
    }

    cancel(): void { this.router.navigate(['/products']); }

    getFieldError(field: string): string {
        const control = this.productForm.get(field);
        if (control?.hasError('required')) return `This field is required`;
        if (control?.hasError('minlength')) return `Minimum ${control.getError('minlength').requiredLength} characters required`;
        if (control?.hasError('min')) return `Value must be at least ${control.getError('min').min}`;
        if (control?.hasError('pattern')) return `Invalid format. Letters, numbers, hyphens, underscores only.`;
        return '';
    }
}
