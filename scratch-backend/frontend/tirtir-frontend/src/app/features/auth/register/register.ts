import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner';

@Component({
    selector: 'app-register',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, RouterModule, LoadingSpinnerComponent],
    templateUrl: './register.html',
    styleUrls: ['./register.css'],
})
export class RegisterComponent {
    private fb = inject(FormBuilder);
    private authService = inject(AuthService);
    private router = inject(Router);

    registerForm: FormGroup;
    loading = false;
    errorMessage = '';
    successMessage = '';

    // Password visibility state
    showPassword = false;
    showConfirmPassword = false;

    constructor() {
        this.registerForm = this.fb.group({
            firstName: ['', [Validators.required, Validators.minLength(1)]],
            lastName:  ['', [Validators.required, Validators.minLength(1)]],
            email:     ['', [Validators.required, Validators.email]],
            password:  ['', [Validators.required, Validators.minLength(8)]],
            confirmPassword: ['', [Validators.required]],
        }, {
            validators: this.passwordMatchValidator,
            updateOn: 'blur'
        });
    }

    togglePassword(): void {
        this.showPassword = !this.showPassword;
    }

    toggleConfirmPassword(): void {
        this.showConfirmPassword = !this.showConfirmPassword;
    }

    passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
        const password = control.get('password');
        const confirmPassword = control.get('confirmPassword');
        if (!password || !confirmPassword) return null;
        return password.value === confirmPassword.value ? null : { passwordMismatch: true };
    }

    onSubmit(): void {
        // Force update — handles browser autofill that bypasses change detection
        Object.keys(this.registerForm.controls).forEach(key => {
            this.registerForm.get(key)?.updateValueAndValidity();
        });

        if (this.registerForm.invalid) {
            this.registerForm.markAllAsTouched();
            return;
        }

        this.loading = true;
        this.errorMessage = '';
        this.successMessage = '';

        const { firstName, lastName, email, password } = this.registerForm.value;

        this.authService.register({ firstName, lastName, email, password }).subscribe({
            next: (response) => {
                this.loading = false;

                if (response.token) {
                    // Backend issued tokens → session already saved by auth service → go home
                    this.router.navigate(['/']);
                } else {
                    // Production: email verification required
                    this.successMessage = response.message || 'Account created! Please check your email.';
                }
            },
            error: (error) => {
                this.loading = false;
                this.errorMessage = error.message || 'Registration failed. Please try again.';
            },
        });
    }

    getFieldError(fieldName: string): string {
        const field = this.registerForm.get(fieldName);
        if (field && field.invalid && field.touched) {
            if (field.errors?.['required'])  return 'This field is required';
            if (field.errors?.['email'])     return 'Please enter a valid email address';
            if (field.errors?.['minlength']) {
                const min = field.errors['minlength'].requiredLength;
                return `Minimum ${min} characters required`;
            }
        }
        if (fieldName === 'confirmPassword' &&
            this.registerForm.errors?.['passwordMismatch'] && field?.touched) {
            return 'Passwords do not match';
        }
        return '';
    }
}
