import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/**
 * Global HTTP error interceptor for admin-frontend.
 *
 * 401 → token expired / not authenticated → redirect to /login
 * 403 → authenticated but not authorized → redirect to /dashboard
 * 500 → server error → log only, let component handle display
 *
 * Always re-throws the error so components can still react if needed.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
    const router = inject(Router);

    return next(req).pipe(
        catchError((error: HttpErrorResponse) => {
            switch (error.status) {
                case 401:
                    // Token missing or expired — clear session and go to login
                    localStorage.removeItem('admin_token');
                    localStorage.removeItem('admin_user');
                    router.navigate(['/login']);
                    break;

                case 403:
                    // Authenticated but role is not allowed for this resource
                    router.navigate(['/dashboard']);
                    break;

                case 500:
                    // Internal server error — log for debugging, do not redirect
                    console.error('[Admin] Server Error 500:', error.url, error.message);
                    break;

                default:
                    // Other errors (400, 404, etc.) — let component handle
                    break;
            }

            // Always re-throw so components / services can react
            return throwError(() => error);
        })
    );
};
