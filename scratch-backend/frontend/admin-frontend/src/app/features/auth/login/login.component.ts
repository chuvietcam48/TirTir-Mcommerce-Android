import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService, AuthResponse } from '../../../core/services/auth.service';

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.css']
})
export class LoginComponent {
    email = '';
    password = '';
    errorMessage = '';
    isLoading = false;

    constructor(
        private authService: AuthService,
        private router: Router
    ) { }

    onSubmit() {
        this.errorMessage = '';
        this.isLoading = true;

        this.authService.login(this.email, this.password).subscribe({
            next: (response: AuthResponse) => {
                this.isLoading = false;
                if (response.success) {
                    this.router.navigate(['/dashboard']);
                }
            },
            error: (error: any) => {
                this.isLoading = false;
                this.errorMessage = error.error?.message || 'Login failed. Please check your credentials.';
            }
        });
    }
}
