import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NotificationService } from './notification.service';

export interface AuthResponse {
    success: boolean;
    token?: string;
    user?: {
        _id: string;
        email: string;
        name: string;
        role: string;
    };
    message?: string;
}

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private apiUrl = environment.apiUrl;
    private currentUserSubject = new BehaviorSubject<any>(null);
    public currentUser$ = this.currentUserSubject.asObservable();

    constructor(
        private http: HttpClient,
        private notificationService: NotificationService
    ) {
        // Check if user is already logged in
        const token = localStorage.getItem('admin_token');
        const user = localStorage.getItem('admin_user');
        if (token && user) {
            this.currentUserSubject.next(JSON.parse(user));
        }
    }

    login(email: string, password: string): Observable<AuthResponse> {
        return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, {
            email,
            password
        }).pipe(
            tap(response => {
                if (response.success && response.token && response.user) {
                    // Allow admin and staff users
                    const allowedRoles = ['admin', 'inventory_staff', 'customer_service'];
                    if (allowedRoles.includes(response.user.role)) {
                        localStorage.setItem('admin_token', response.token);
                        localStorage.setItem('admin_user', JSON.stringify(response.user));
                        this.currentUserSubject.next(response.user);
                    } else {
                        throw new Error('Access denied. Admin privileges required.');
                    }
                }
            })
        );
    }

    logout(): void {
        localStorage.removeItem('admin_token');
        localStorage.removeItem('admin_user');
        this.currentUserSubject.next(null);
        // Disconnect socket
        this.notificationService.disconnect();
    }

    getToken(): string | null {
        return localStorage.getItem('admin_token');
    }

    isAuthenticated(): boolean {
        const token = this.getToken();
        if (!token) return false;

        // Decode JWT payload and check expiry
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            const isExpired = payload.exp && (Date.now() / 1000) > payload.exp;
            if (isExpired) {
                // Auto-clear expired session
                this.logout();
                return false;
            }
        } catch {
            // Malformed token — treat as invalid
            this.logout();
            return false;
        }

        return true;
    }

    isAdmin(): boolean {
        return this.hasRole(['admin']);
    }

    hasRole(roles: string[]): boolean {
        const user = this.currentUserSubject.value;
        if (!user) return false;
        // If user is admin, they have access to everything (or should be explicit?)
        // Usually admin has access to everything, but role checks might be specific.
        // For now, simple inclusion check.
        // But wait, if I ask hasRole(['inventory_staff']) and I am admin?
        // Admin should probably return true?
        // User said: "Phân quyền (Roles): Ví dụ 'Nhân viên kho' chỉ được truy cập menu Inventory"
        // Implicitly Admin can access everything.
        if (user.role === 'admin') return true; 
        
        return roles.includes(user.role);
    }

    getCurrentUser(): any {
        return this.currentUserSubject.value;
    }
}
