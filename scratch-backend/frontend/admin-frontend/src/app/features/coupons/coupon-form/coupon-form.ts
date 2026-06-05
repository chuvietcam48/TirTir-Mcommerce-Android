import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, ValidatorFn, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { CouponService, Coupon } from '../../../core/services/coupon.service';

// ── Standalone cross-field validator (avoids 'this' binding issues in constructor) ──
function dateRangeValidator(): ValidatorFn {
    return (group: AbstractControl): ValidationErrors | null => {
        const from = group.get('validFrom')?.value;
        const to   = group.get('validTo')?.value;
        if (from && to && new Date(from) >= new Date(to)) {
            return { dateRange: true };
        }
        return null;
    };
}

@Component({
    selector: 'app-coupon-form',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, RouterModule],
    templateUrl: './coupon-form.html',
    styleUrls: ['./coupon-form.css']
})
export class CouponFormComponent implements OnInit {
    couponForm: FormGroup;
    isEditMode = false;
    couponId: string | null = null;
    loading = false;
    error: string | null = null;
    submitting = false;

    constructor(
        private fb: FormBuilder,
        private couponService: CouponService,
        private router: Router,
        private route: ActivatedRoute
    ) {
        this.couponForm = this.fb.group({
            code:           ['', [Validators.required, Validators.pattern('^[A-Za-z0-9_-]+$')]],
            discountType:   ['percentage', Validators.required],
            discountValue:  [null, [Validators.required, Validators.min(0.01)]],
            validFrom:      ['', Validators.required],
            validTo:        ['', Validators.required],
            minOrderValue:  [0, [Validators.min(0)]],
            maxDiscount:    [null],
            usageLimit:     [null, [Validators.min(1)]],
            active:         [true, Validators.required]
        }, { validators: dateRangeValidator() });

        // Apply/remove max validator when discountType changes
        this.couponForm.get('discountType')!.valueChanges.subscribe((type: string) => {
            const valueCtrl = this.couponForm.get('discountValue')!;
            if (type === 'percentage') {
                valueCtrl.setValidators([Validators.required, Validators.min(0.01), Validators.max(100)]);
            } else {
                valueCtrl.setValidators([Validators.required, Validators.min(0.01)]);
            }
            valueCtrl.updateValueAndValidity();
        });
    }

    ngOnInit(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
            this.isEditMode = true;
            this.couponId = id;
            this.loadCoupon(id);
        }
    }

    loadCoupon(id: string): void {
        this.loading = true;
        this.couponService.getCouponById(id).subscribe({
            next: (coupon) => {
                // Format dates for input[type="date"]
                const validFrom = new Date(coupon.validFrom).toISOString().split('T')[0];
                const validTo = new Date(coupon.validTo).toISOString().split('T')[0];

                this.couponForm.patchValue({
                    ...coupon,
                    validFrom,
                    validTo
                });
                this.loading = false;
            },
            error: (err) => {
                this.error = 'Failed to load coupon details';
                this.loading = false;
                console.error('Load error:', err);
            }
        });
    }

    onSubmit(): void {
        if (this.couponForm.invalid) {
            this.markFormGroupTouched(this.couponForm);
            return;
        }

        this.submitting = true;
        this.error = null;

        const formValue = this.couponForm.value;
        // Ensure numeric values are numbers
        const couponData: Coupon = {
            ...formValue,
            discountValue: Number(formValue.discountValue),
            minOrderValue: formValue.minOrderValue ? Number(formValue.minOrderValue) : 0,
            maxDiscount: formValue.maxDiscount ? Number(formValue.maxDiscount) : undefined,
            usageLimit: formValue.usageLimit ? Number(formValue.usageLimit) : undefined,
            usedCount: this.isEditMode ? formValue.usedCount : 0,
        };

        if (this.isEditMode && this.couponId) {
            this.couponService.updateCoupon(this.couponId, couponData).subscribe({
                next: () => {
                    this.router.navigate(['/coupons']);
                },
                error: (err) => {
                    this.error = err.error?.message || 'Failed to update coupon';
                    this.submitting = false;
                    console.error('Update error:', err);
                }
            });
        } else {
            this.couponService.createCoupon(couponData).subscribe({
                next: () => {
                    this.router.navigate(['/coupons']);
                },
                error: (err) => {
                    this.error = err.error?.message || 'Failed to create coupon';
                    this.submitting = false;
                    console.error('Create error:', err);
                }
            });
        }
    }

    /** Shorthand to access form controls in template */
    get f() { return this.couponForm.controls; }

    /** Auto-uppercase the coupon code as user types */
    toUpperCase(event: Event): void {
        const input = event.target as HTMLInputElement;
        const start = input.selectionStart ?? 0;
        const end   = input.selectionEnd   ?? 0;
        input.value = input.value.toUpperCase();
        input.setSelectionRange(start, end);
        this.couponForm.get('code')!.setValue(input.value, { emitEvent: false });
    }


    private markFormGroupTouched(formGroup: FormGroup) {
        Object.values(formGroup.controls).forEach(control => {
            control.markAsTouched();
            if (control instanceof FormGroup) this.markFormGroupTouched(control);
        });
    }
}
