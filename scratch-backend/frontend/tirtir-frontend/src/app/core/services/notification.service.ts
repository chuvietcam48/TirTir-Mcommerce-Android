import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, timer, Subscription, of } from 'rxjs';
import { switchMap, tap, retry, shareReplay, takeWhile, catchError } from 'rxjs/operators';
import { isPlatformBrowser } from '@angular/common';
import { environment } from '../../../environments/environment';
import { INotification } from '../models/notification.model';
import { AuthService } from './auth.service';

@Injectable({
    providedIn: 'root'
})
export class NotificationService {
    private http = inject(HttpClient);
    private platformId = inject(PLATFORM_ID);
    private authService = inject(AuthService);

    // Use explicit URL or environment variable
    private apiUrl = 'http://localhost:5001/api/v1/notifications';

    // State Management
    private notificationsSubject = new BehaviorSubject<INotification[]>([]);
    public notifications$ = this.notificationsSubject.asObservable();

    private unreadCountSubject = new BehaviorSubject<number>(0);
    public unreadCount$ = this.unreadCountSubject.asObservable();

    private pollingSubscription?: Subscription;
    private authSubscription?: Subscription;
    private isPolling = false;

    constructor() {
        // Only start polling in browser environment
        if (isPlatformBrowser(this.platformId)) {
            // Listen to auth state changes to start/stop polling
            this.authSubscription = this.authService.currentUser$.subscribe((user) => {
                if (user && this.authService.getToken()) {
                    // User logged in, start polling
                    this.startPolling();
                } else {
                    // User logged out, stop polling
                    this.stopPolling();
                    // Clear notifications when logged out
                    this.notificationsSubject.next([]);
                    this.unreadCountSubject.next(0);
                }
            });
        }
    }

    /**
     * Start polling every 60 seconds
     */
    public startPolling() {
        if (this.isPolling) return;
        this.isPolling = true;

        this.pollingSubscription = timer(0, 60000)
            .pipe(
                switchMap(() => this.fetchNotifications()),
                // Stop polling if we explicitly get an error that wasn't caught/handled
                takeWhile(() => this.isPolling)
            )
            .subscribe({
                error: (err) => {
                    // Stop polling strictly on 401 Unauthorized
                    if (err.status === 401) {
                        this.stopPolling();
                    }
                }
            });
    }

    /**
     * Stop the polling completely
     */
    public stopPolling() {
        this.isPolling = false;
        if (this.pollingSubscription) {
            this.pollingSubscription.unsubscribe();
            this.pollingSubscription = undefined;
        }
    }

    /**
     * Fetch notifications from API
     */
    fetchNotifications(): Observable<any> {
        return this.http.get<{ success: boolean; data: INotification[] }>(this.apiUrl)
            .pipe(
                tap(response => {
                    if (response.success) {
                        this.updateState(response.data);
                    }
                }),
                retry(1), // Retry once on failure
                catchError(err => {
                    if (err.status === 401) {
                        this.stopPolling(); // immediately stop on UNAUTHORIZED
                    }
                    return of({ success: false, data: [] }); // gracefully return empty
                })
            );
    }

    /**
     * Update local state (notifications and unread count)
     */
    private updateState(notifications: INotification[]) {
        this.notificationsSubject.next(notifications);

        // Calculate unread count
        const count = notifications.filter(n => !n.isRead).length;
        this.unreadCountSubject.next(count);
    }

    /**
     * Mark a single notification as read
     * Optimistic UI update
     */
    markAsRead(id: string): Observable<any> {
        // 1. Optimistic Update
        const currentNotifications = this.notificationsSubject.value;
        const updatedNotifications = currentNotifications.map(n =>
            n._id === id ? { ...n, isRead: true } : n
        );
        this.updateState(updatedNotifications);

        // 2. Call API
        return this.http.put(`${this.apiUrl}/${id}/read`, {}).pipe(
            tap({
                error: () => {
                    // Revert on error (optional, usually skipped for read status)
                    this.updateState(currentNotifications);
                }
            })
        );
    }

    /**
     * Mark all notifications as read
     * Optimistic UI update
     */
    markAllAsRead(): Observable<any> {
        // 1. Optimistic Update
        const currentNotifications = this.notificationsSubject.value;
        const updatedNotifications = currentNotifications.map(n => ({ ...n, isRead: true }));
        this.updateState(updatedNotifications);

        // 2. Call API
        return this.http.put(`${this.apiUrl}/read-all`, {});
    }

    /**
     * Get top N notifications for dropdown
     */
    getRecent(limit: number = 5): Observable<INotification[]> {
        return this.notifications$; // Logic to slice can be done in component or selector
    }
}
