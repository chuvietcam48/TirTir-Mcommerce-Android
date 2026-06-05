import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

/**
 * Guard that restricts routes to users with 'admin' role.
 * Redirects non-admin users to the homepage.
 */
export const adminGuard: CanActivateFn = () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const user = authService.currentUserValue;

    if (user && user.role === 'admin') {
        return true;
    }

    // Not authenticated or not admin → redirect
    router.navigate(['/']);
    return false;
};
