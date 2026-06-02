import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CustomerService, Customer } from '../../../core/services/customer.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Component({
    selector: 'app-customer-detail',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule],
    templateUrl: './customer-detail.html',
    styleUrls: ['./customer-detail.css']
})
export class CustomerDetailComponent implements OnInit {
    customer: Customer | null = null;
    orders: any[] = [];
    loading = true;
    error: string | null = null;
    updating = false;
    updateMessage: string | null = null;
    updateError: string | null = null;

    // Computed stats (derived from orders array)
    allOrdersCount = 0;
    successfulOrdersCount = 0;
    totalSpent = 0;
    lastOrderDate: string | null = null;
    avgOrderValue = 0;

    constructor(
        private route: ActivatedRoute,
        private customerService: CustomerService
    ) { }

    ngOnInit(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
            this.loadCustomerData(id);
        }
    }

    loadCustomerData(id: string): void {
        this.loading = true;
        this.error = null;

        forkJoin({
            customer: this.customerService.getCustomerById(id),
            orders: this.customerService.getCustomerOrders(id).pipe(
                catchError(err => {
                    console.error('Customer orders error:', err);
                    return of([]);
                })
            )
        }).subscribe({
            next: (result) => {
                this.customer = result.customer;
                this.orders = Array.isArray(result.orders) ? result.orders : [];
                this.computeStats();
                this.loading = false;
            },
            error: (err) => {
                this.error = 'Failed to load customer details';
                this.loading = false;
                console.error('Customer details error:', err);
            }
        });
    }

    /** Compute stats from the real orders array loaded via /admin/users/:id/orders */
    private computeStats(): void {
        this.allOrdersCount = this.orders.length;
        
        // Successful orders are those with 'Delivered' status
        const successfulOrders = this.orders.filter(o => o.status === 'Delivered');
        this.successfulOrdersCount = successfulOrders.length;
        
        // Total Spent is sum of totalAmount from successful orders only
        this.totalSpent = successfulOrders.reduce((sum, o) => sum + (o.totalAmount || 0), 0);
        
        // Last order date from any order (newest-first)
        this.lastOrderDate = this.orders.length > 0 ? this.orders[0].createdAt : null;
        
        // Average value based on successful orders
        this.avgOrderValue = this.successfulOrdersCount > 0 ? this.totalSpent / this.successfulOrdersCount : 0;
    }

    getStatusClass(status: string): string {
        const map: Record<string, string> = {
            'pending': 'status-pending',
            'processing': 'status-processing',
            'shipped': 'status-shipped',
            'delivered': 'status-delivered',
            'cancelled': 'status-cancelled'
        };
        return map[status?.toLowerCase()] || 'status-pending';
    }

    /**
     * Toggle customer block status using `isBlocked` (backend real field).
     * Backend PUT /admin/users/:id/status expects { isBlocked: boolean }
     */
    toggleBlock(): void {
        if (!this.customer || this.updating) return;

        const nextBlocked = !this.customer.isBlocked;
        const action = nextBlocked ? 'block' : 'unblock';

        if (!confirm(`Are you sure you want to ${action} this customer?`)) return;

        this.updating = true;
        this.updateMessage = null;
        this.updateError = null;

        this.customerService.updateCustomerStatus(this.customer._id, nextBlocked).subscribe({
            next: (res: any) => {
                if (this.customer) {
                    // Response shape: { success, message, user }
                    this.customer.isBlocked = res.user?.isBlocked ?? nextBlocked;
                }
                this.updateMessage = `Customer ${nextBlocked ? 'blocked' : 'unblocked'} successfully`;
                setTimeout(() => this.updateMessage = null, 3000);
                this.updating = false;
            },
            error: (err) => {
                console.error('Status update error:', err);
                this.updateError = 'Failed to update status. Please try again.';
                setTimeout(() => this.updateError = null, 4000);
                this.updating = false;
            }
        });
    }

    formatDate(date: string): string {
        if (!date) return '—';
        return new Date(date).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    }

    formatPrice(value: number): string {
        return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value || 0);
    }
}
