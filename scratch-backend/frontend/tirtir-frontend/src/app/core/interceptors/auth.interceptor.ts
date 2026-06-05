import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
    // FIX: Circular Dependency. Do not inject AuthService here.
    // Access token directly from storage.
    const token = localStorage.getItem('tirtir_auth_token');

    // Only add token for requests to our own API
    const isApiRequest = req.url.startsWith(environment.apiUrl);

    // Skip adding token for auth endpoints (login, register)
    const isAuthEndpoint = req.url.includes('/api/auth/login') ||
        req.url.includes('/api/auth/register') ||
        req.url.includes('/api/auth/forgot-password') ||
        req.url.includes('/api/auth/reset-password');

    // If token exists and it's an API request but not an auth endpoint, add it
    if (token && isApiRequest && !isAuthEndpoint) {
        req = req.clone({
            setHeaders: {
                Authorization: `Bearer ${token}`,
            },
        });
    }

    return next(req);
};
