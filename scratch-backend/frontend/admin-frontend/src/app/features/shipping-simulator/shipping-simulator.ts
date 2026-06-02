import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface ShippedOrder {
    _id: string;
    ghnOrderCode: string | null;
    shippingAddress: { fullName: string; phone: string; address: string; city: string };
    totalAmount: number;
    createdAt: string;
    user?: { name: string; email: string };
    // Runtime state
    _simulating?: boolean;
    _result?: string;
    _resultType?: 'success' | 'error' | 'info';
}

@Component({
    selector: 'app-shipping-simulator',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './shipping-simulator.html',
    styleUrls: ['./shipping-simulator.css']
})
export class ShippingSimulatorComponent implements OnInit, OnDestroy {
    orders: ShippedOrder[] = [];
    loading = false;
    error: string | null = null;
    search = '';
    page = 1;
    total = 0;
    limit = 20;

    private searchTimeout: any;
    private pollTimers: { [orderId: string]: any } = {};

    // Fix #1: was `'token'` — auth service stores as `'admin_token'`
    private readonly apiUrl = `${environment.apiUrl}/shipping`;
    // Fix #2: removed manual HttpHeaders — authInterceptor handles this globally

    ghnStatusOptions = [
        { label: 'Delivered', value: 'delivered', cls: 'btn-delivered' },
        { label: 'Return', value: 'return', cls: 'btn-return' },
        { label: 'Cancel', value: 'cancel', cls: 'btn-cancel' },
    ];

    constructor(private http: HttpClient) { }

    ngOnInit(): void {
        this.loadOrders();
    }

    ngOnDestroy(): void {
        // Clear all polling timers on destroy
        Object.values(this.pollTimers).forEach(t => clearTimeout(t));
        if (this.searchTimeout) clearTimeout(this.searchTimeout);
    }

    loadOrders(): void {
        this.loading = true;
        this.error = null;

        // Fix #2: no custom headers — authInterceptor attaches Bearer token automatically
        this.http.get<any>(
            `${this.apiUrl}/shipped-orders?search=${encodeURIComponent(this.search)}&page=${this.page}&limit=${this.limit}`
        ).subscribe({
            next: (res) => {
                this.orders = res.data || [];
                this.total = res.total || 0;
                this.loading = false;
            },
            error: (err) => {
                this.error = err.error?.message || 'Failed to load shipped orders';
                this.loading = false;
            }
        });
    }

    onSearchChange(): void {
        clearTimeout(this.searchTimeout);
        this.searchTimeout = setTimeout(() => {
            this.page = 1;
            this.loadOrders();
        }, 400);
    }

    simulate(order: ShippedOrder, ghnStatus: string): void {
        if (order._simulating) return;

        order._simulating = true;
        order._result = `Simulating ${ghnStatus}...`;
        order._resultType = 'info';

        // Fix #2: no custom headers — interceptor handles auth
        this.http.post<any>(
            `${this.apiUrl}/simulate-delivery`,
            { orderId: order._id, ghnStatus }
        ).subscribe({
            next: (res) => {
                order._result = `✅ ${res.message}`;
                order._resultType = 'success';
                order._simulating = false;
                this.startPolling(order);
            },
            error: (err) => {
                order._result = `❌ ${err.error?.message || 'Simulation failed'}`;
                order._resultType = 'error';
                order._simulating = false;
            }
        });
    }

    /** Poll order status for up to 10s after simulate click, then refresh list */
    private startPolling(order: ShippedOrder): void {
        let attempts = 0;
        const maxAttempts = 5;

        const poll = () => {
            if (attempts >= maxAttempts) {
                this.loadOrders();
                return;
            }

            attempts++;
            this.pollTimers[order._id] = setTimeout(() => {
                // Fix #2: no custom headers
                this.http.get<any>(`${environment.apiUrl}/orders/${order._id}`)
                    .subscribe({
                        next: (updatedOrder) => {
                            if (updatedOrder.status !== 'Shipped') {
                                order._result = `✅ Confirmed: Order is now "${updatedOrder.status}"`;
                                setTimeout(() => this.loadOrders(), 2000);
                            } else {
                                poll();
                            }
                        },
                        error: () => poll()
                    });
            }, 2000);
        };

        poll();
    }

    trackByFn(_: number, order: ShippedOrder): string {
        return order._id;
    }

    get totalPages(): number {
        return Math.ceil(this.total / this.limit);
    }

    formatDate(iso: string): string {
        return new Date(iso).toLocaleString('en-US');
    }

    shortId(id: string): string {
        return '#' + id.slice(-6).toUpperCase();
    }
}
