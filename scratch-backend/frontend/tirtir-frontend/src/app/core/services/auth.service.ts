import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, BehaviorSubject, tap, catchError, throwError, map, of } from 'rxjs';
import {
    User,
    LoginRequest,
    RegisterRequest,
    AuthResponse, // Keep AuthResponse here for now, as the instruction defines it later.
    ForgotPasswordRequest,
    ResetPasswordRequest,
} from '../models';
import { environment } from '../../../environments/environment';

@Injectable({
    providedIn: 'root',
})
export class AuthService {
    private apiUrl = `${environment.apiUrl}/auth`;
    private http = inject(HttpClient);
    private router = inject(Router);

    // Auth state management
    private currentUserSubject = new BehaviorSubject<User | null>(null);
    public currentUser$ = this.currentUserSubject.asObservable();

    // Signal-based auth state for modern Angular
    private isAuthenticatedSignal = signal<boolean>(false);
    public isAuthenticated = computed(() => this.isAuthenticatedSignal());

    private readonly TOKEN_KEY = 'tirtir_auth_token';
    private readonly REFRESH_TOKEN_KEY = 'tirtir_refresh_token';

    constructor() {
        // Check for existing token on initialization
        this.checkAuthStatus();
    }

    /**
     * Check if user is authenticated by verifying token
     */
    private checkAuthStatus(): void {
        const token = this.getToken();
        if (token) {
            // Verify token by fetching current user
            this.getCurrentUser().subscribe({
                next: (user) => {
                    this.currentUserSubject.next(user);
                    this.isAuthenticatedSignal.set(true);
                },
                error: (err) => {
                    // Only clear token if it's explicitly an auth invalidation error (e.g. expired)
                    // and not just a temporary 401/network glitch or backend restarting.
                    if (err && err.status === 401) {
                        this.clearToken();
                        this.isAuthenticatedSignal.set(false);
                    } else if (err && err.status !== 500) {
                        this.isAuthenticatedSignal.set(false);
                    }
                },
            });
        }
    }

    /**
     * Login user with email and password
     */
    login(credentials: LoginRequest): Observable<AuthResponse> {
        return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials).pipe(
            tap((response) => {
                if (response.success && response.token) {
                    this.setToken(response.token);
                    if ((response as any).refreshToken) {
                        localStorage.setItem(this.REFRESH_TOKEN_KEY, (response as any).refreshToken);
                    }
                    this.currentUserSubject.next(response.user);
                    this.isAuthenticatedSignal.set(true);
                }
            }),
            catchError(this.handleError)
        );
    }

    /**
     * Register new user.
     * In development mode the backend returns tokens directly → auto-login.
     * In production the backend returns a success message (email verification required).
     */
    register(userData: RegisterRequest): Observable<AuthResponse & { message?: string }> {
        return this.http
            .post<AuthResponse & { message?: string }>(`${this.apiUrl}/register`, userData)
            .pipe(
                tap((response) => {
                    // Auto-login when the backend issues tokens on registration
                    if (response.success && response.token) {
                        this.setToken(response.token);
                        if ((response as any).refreshToken) {
                            localStorage.setItem(this.REFRESH_TOKEN_KEY, (response as any).refreshToken);
                        }
                        this.currentUserSubject.next(response.user);
                        this.isAuthenticatedSignal.set(true);
                    }
                }),
                catchError(this.handleError)
            );
    }

    /**
     * Logout current user.
     *
     * Strategy — clear local state immediately so the UI reacts at once,
     * then fire the backend POST /logout in the background to invalidate the
     * refresh token in the DB and expire the HTTP-only cookie.
     * If the network call fails the session is still fully cleaned up locally.
     */
    logout(): void {
        // ── 1. Snapshot tokens BEFORE clearing storage ────────────────────────
        // The auth interceptor reads tirtir_auth_token from localStorage at
        // request time. If we call clearToken() first, storage is empty and the
        // interceptor adds no Authorization header → protect middleware → 401.
        // Capture both values up-front, then clear storage immediately after.
        const accessToken  = this.getToken();
        const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);

        // ── 2. Wipe local state right away (instant UI feedback) ──────────────
        this.clearToken();
        this.currentUserSubject.next(null);
        this.isAuthenticatedSignal.set(false);

        // ── 3. Notify the backend (fire-and-forget) ───────────────────────────
        // Bypass the interceptor by injecting the Authorization header directly
        // from the snapshot — storage is already empty at this point.
        this.http
            .post(
                `${this.apiUrl}/logout`,
                { refreshToken },
                accessToken ? { headers: { Authorization: `Bearer ${accessToken}` } } : {}
            )
            .pipe(catchError(() => of(null)))
            .subscribe();

        // ── 4. Redirect to login ──────────────────────────────────────────────
        this.router.navigate(['/login']);
    }

    /**
     * Get current logged-in user from backend
     */
    getCurrentUser(): Observable<User> {
        return this.http.get<{ success: boolean; data: User }>(`${this.apiUrl}/me`).pipe(
            tap((response) => {
                if (response.success && response.data) {
                    this.currentUserSubject.next(response.data);
                    this.isAuthenticatedSignal.set(true);
                }
            }),
            map(response => response.data),
            catchError(this.handleError)
        );
    }

    /**
     * Send forgot password email
     */
    forgotPassword(email: ForgotPasswordRequest): Observable<{ success: boolean; message: string }> {
        return this.http
            .post<{ success: boolean; message: string }>(`${this.apiUrl}/forgot-password`, email)
            .pipe(catchError(this.handleError));
    }

    /**
     * Reset password with token
     */
    resetPassword(
        token: string,
        passwordData: ResetPasswordRequest
    ): Observable<AuthResponse> {
        return this.http
            .put<AuthResponse>(`${this.apiUrl}/reset-password/${token}`, passwordData)
            .pipe(
                tap((response) => {
                    if (response.success && response.token) {
                        // Auto-login after successful password reset
                        this.setToken(response.token);
                        if ((response as any).refreshToken) {
                            localStorage.setItem(this.REFRESH_TOKEN_KEY, (response as any).refreshToken);
                        }
                        this.currentUserSubject.next(response.user);
                        this.isAuthenticatedSignal.set(true);
                    }
                }),
                catchError(this.handleError)
            );
    }

    /**
     * Get JWT token from localStorage
     */
    getToken(): string | null {
        return localStorage.getItem(this.TOKEN_KEY);
    }

    /**
     * Save JWT token to localStorage
     */
    private setToken(token: string): void {
        localStorage.setItem(this.TOKEN_KEY, token);
    }

    /**
     * Remove JWT token from localStorage
     */
    private clearToken(): void {
        localStorage.removeItem(this.TOKEN_KEY);
        localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    }

    /**
     * Get current user value (synchronous)
     */
    get currentUserValue(): User | null {
        return this.currentUserSubject.value;
    }

    /**
     * Check if user is admin
     */
    isAdmin(): boolean {
        return this.currentUserValue?.role === 'admin';
    }

    /**
     * Error handler — defined as an arrow function so `this` is always bound
     * correctly when passed as a callback to catchError(this.handleError).
     */
    private handleError = (error: any): Observable<never> => {
        let errorMessage = 'An error occurred';

        if (error.error?.message) {
            errorMessage = error.error.message;
        } else if (error.message) {
            errorMessage = error.message;
        }

        console.error('Auth Error:', error);
        return throwError(() => new Error(errorMessage));
    };
}
