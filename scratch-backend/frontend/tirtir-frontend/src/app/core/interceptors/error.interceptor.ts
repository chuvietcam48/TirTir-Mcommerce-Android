import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

// Sentinel header to prevent infinite refresh loops
const SKIP_REFRESH_HEADER = 'X-Skip-Token-Refresh';

/**
 * HTTP Interceptor for global error handling.
 *
 * On a 401/403, the interceptor only attempts a token refresh when the user
 * is actually supposed to be authenticated (i.e. an access token exists in
 * storage).  This prevents guest-user 401s (e.g. the notification bell on a
 * public page) from triggering a redirect to /login and ruining the guest UX.
 *
 * Flow for an authenticated session:
 *  1. 401/403 received + access token present → try refresh-token endpoint.
 *  2. Refresh succeeds → persist new tokens, transparently retry original request.
 *  3. Refresh fails (token revoked/expired) → clear both tokens, redirect to /login.
 *
 * Flow for a guest session (no access token):
 *  → 401/403 is logged and re-thrown.  No redirect, no refresh attempt.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
    const router = inject(Router);
    const http   = inject(HttpClient);

    return next(req).pipe(
        catchError((error: HttpErrorResponse) => {
            const isAuthError      = error.status === 401 || error.status === 403;
            const isRefreshAttempt = req.headers.has(SKIP_REFRESH_HEADER);

            // Gate: only act when the user should be authenticated.
            // A guest 401 (e.g. notifications, cart) must never trigger a redirect.
            const hasAccessToken = typeof window !== 'undefined' &&
                !!localStorage.getItem('tirtir_auth_token');

            if (isAuthError && !isRefreshAttempt && hasAccessToken) {
                const refreshToken = localStorage.getItem('tirtir_refresh_token');

                if (refreshToken) {
                    // Attempt token refresh — sentinel header prevents this call
                    // from looping back into the same catchError branch.
                    return http.post<{ token: string; refreshToken: string }>(
                        `${environment.apiUrl}/auth/refresh-token`,
                        { refreshToken },
                        { headers: { [SKIP_REFRESH_HEADER]: 'true' } }
                    ).pipe(
                        switchMap((response) => {
                            localStorage.setItem('tirtir_auth_token',  response.token);
                            localStorage.setItem('tirtir_refresh_token', response.refreshToken);

                            // Retry the original request with the fresh access token
                            const retried = req.clone({
                                setHeaders: { Authorization: `Bearer ${response.token}` }
                            });
                            return next(retried);
                        }),
                        catchError((refreshError) => {
                            // Both tokens are invalid — force a clean logout
                            localStorage.removeItem('tirtir_auth_token');
                            localStorage.removeItem('tirtir_refresh_token');
                            router.navigate(['/login']);
                            return throwError(() => refreshError);
                        })
                    );
                }

                // Access token exists but refresh token is missing →
                // session is unrecoverable, clear and redirect.
                localStorage.removeItem('tirtir_auth_token');
                router.navigate(['/login']);
            }

            console.error('HTTP Error:', error);
            return throwError(() => error);
        })
    );
};
