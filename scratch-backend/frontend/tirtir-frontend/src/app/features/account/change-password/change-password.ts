import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, AbstractControl } from '@angular/forms';
import { UserService } from '../../../core/services/user.service';

@Component({
    selector: 'app-change-password',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './change-password.html',
    styleUrl: './change-password.css'
})
export class ChangePasswordComponent {
    private userService = inject(UserService);
    private fb = inject(FormBuilder);

    passwordForm: FormGroup;
    loading = false;
    successMessage = '';
    errorMessage = '';
    showOldPassword = false;
    showNewPassword = false;
    showConfirmPassword = false;

    constructor() {
        this.passwordForm = this.fb.group({
            currentPassword: ['', [Validators.required, Validators.minLength(6)]],
            newPassword: ['', [Validators.required, Validators.minLength(6)]],
            confirmPassword: ['', [Validators.required]]
        }, {
            validators: this.passwordMatchValidator
        });
    }

    // Custom validator to check if passwords match
    passwordMatchValidator(control: AbstractControl): { [key: string]: boolean } | null {
        const newPassword = control.get('newPassword');
        const confirmPassword = control.get('confirmPassword');

        if (!newPassword || !confirmPassword) {
            return null;
        }

        return newPassword.value === confirmPassword.value ? null : { passwordMismatch: true };
    }

    onSubmit(): void {
        if (this.passwordForm.invalid) {
            this.passwordForm.markAllAsTouched();
            return;
        }

        this.loading = true;
        this.successMessage = '';
        this.errorMessage = '';

        const { currentPassword, newPassword } = this.passwordForm.value;

        this.userService.changePassword({ currentPassword, newPassword }).subscribe({
            next: (response) => {
                this.successMessage = response.message || 'Password changed successfully!';
                this.loading = false;
                this.passwordForm.reset();
            },
            error: (error) => {
                this.errorMessage = error.message || 'Failed to change password';
                this.loading = false;
            }
        });
    }

    togglePasswordVisibility(field: 'old' | 'new' | 'confirm'): void {
        if (field === 'old') this.showOldPassword = !this.showOldPassword;
        if (field === 'new') this.showNewPassword = !this.showNewPassword;
        if (field === 'confirm') this.showConfirmPassword = !this.showConfirmPassword;
    }
}
