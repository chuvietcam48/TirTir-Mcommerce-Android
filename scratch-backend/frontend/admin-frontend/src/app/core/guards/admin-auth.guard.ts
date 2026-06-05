import { inject } from '@angular/core';
import { Router, CanActivateFn, CanActivateChildFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Checks authentication and role for both parent AND child routes.
 * Used as both canActivate (on the shell layout) and canActivateChild
 * (on every child route), so role data on children is also enforced.
 */
const checkAccess = (route: any, router: Router, authService: AuthService): boolean => {
    const requiredRoles = route.data?.['roles'] as string[] | undefined;

    if (!authService.isAuthenticated()) {
        router.navigate(['/login']);
        return false;
    }

    if (requiredRoles && requiredRoles.length > 0) {
        if (!authService.hasRole(requiredRoles)) {
            router.navigate(['/dashboard']);
            return false;
        }
    }

    return true;
};

export const adminAuthGuard: CanActivateFn = (route, _state) => {
    const authService = inject(AuthService);
    const router = inject(Router);
    return checkAccess(route, router, authService);
};

/** Apply to canActivateChild so child routes also enforce their own data.roles */
export const adminAuthChildGuard: CanActivateChildFn = (route, _state) => {
    const authService = inject(AuthService);
    const router = inject(Router);
    return checkAccess(route, router, authService);
};
