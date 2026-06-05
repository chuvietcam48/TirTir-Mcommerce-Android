import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { io, Socket } from 'socket.io-client';
import { environment } from '../../../environments/environment';

export interface AdminNotification {
    id: string;
    type: 'new_order' | 'low_stock' | 'cart_recovered';
    title: string;
    message: string;
    timestamp: string;
    read: boolean;
    orderId?: string;
    productId?: string;
    cartId?: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService implements OnDestroy {
    private socket: Socket | null = null;

    private _notifications = new BehaviorSubject<AdminNotification[]>([]);
    notifications$ = this._notifications.asObservable();

    private _unreadCount = new BehaviorSubject<number>(0);
    unreadCount$ = this._unreadCount.asObservable();

    private _toasts = new BehaviorSubject<AdminNotification[]>([]);
    toasts$ = this._toasts.asObservable();

    connect(): void {
        if (this.socket?.connected) return;

        const token = localStorage.getItem('admin_token');
        if (!token) return;

        this.socket = io(environment.socketUrl, {
            auth: { token },
            transports: ['websocket'],
            reconnectionAttempts: 5
        });

        this.socket.on('connect', () => {
            console.log('[Socket] Connected to admin notifications');
        });

        this.socket.on('connect_error', (err) => {
            console.warn('[Socket] Connection error:', err.message);
        });

        this.socket.on('new_order', (payload: any) => this.handleEvent(payload));
        this.socket.on('low_stock', (payload: any) => this.handleEvent(payload));
        this.socket.on('cart_recovered', (payload: any) => this.handleEvent(payload));
    }

    private handleEvent(payload: any): void {
        const notification: AdminNotification = {
            id: `${payload.type}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
            type: payload.type,
            title: payload.title,
            message: payload.message,
            timestamp: payload.timestamp || new Date().toISOString(),
            read: false,
            orderId: payload.orderId,
            productId: payload.productId,
            cartId: payload.cartId
        };

        // Prepend and cap at 50
        const current = this._notifications.value;
        const updated = [notification, ...current].slice(0, 50);
        this._notifications.next(updated);
        this._unreadCount.next(updated.filter(n => !n.read).length);

        // Toast queue — max 3 visible
        this.pushToast(notification);
    }

    private pushToast(notification: AdminNotification): void {
        const current = this._toasts.value;
        const updated = [notification, ...current].slice(0, 3);
        this._toasts.next(updated);

        // Auto-dismiss after 4 seconds
        setTimeout(() => this.dismissToast(notification.id), 4000);
    }

    dismissToast(id: string): void {
        this._toasts.next(this._toasts.value.filter(t => t.id !== id));
    }

    markAllRead(): void {
        const updated = this._notifications.value.map(n => ({ ...n, read: true }));
        this._notifications.next(updated);
        this._unreadCount.next(0);
    }

    markRead(id: string): void {
        const updated = this._notifications.value.map(n =>
            n.id === id ? { ...n, read: true } : n
        );
        this._notifications.next(updated);
        this._unreadCount.next(updated.filter(n => !n.read).length);
    }

    disconnect(): void {
        if (this.socket) {
            this.socket.disconnect();
            this.socket = null;
        }
    }

    ngOnDestroy(): void {
        this.disconnect();
    }
}
