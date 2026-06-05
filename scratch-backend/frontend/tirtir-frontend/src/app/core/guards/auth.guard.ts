import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Auth Guard to protect routes requiring authentication
 * Redirects to login page if user is not authenticated
 */
export const authGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    // CRITICAL: Check token in localStorage first
    // This handles page reload case where auth signal hasn't updated yet
    const token = authService.getToken();

    if (token) {
        // Token exists, allow access
        // The checkAuthStatus() in AuthService constructor will validate it
        return true;
    }

    // Also check signal state for in-app navigation
    if (authService.isAuthenticated()) {
        return true;
    }

    // Store the attempted URL for redirecting after login
    const returnUrl = state.url;

    // Redirect to login page with return URL
    router.navigate(['/login'], {
        queryParams: { returnUrl },
    });

    return false;
};
