import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { OrderService, Order, StatusHistory } from '../../../core/services/order.service';
import { environment } from '../../../../environments/environment';

@Component({
    selector: 'app-order-detail',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule],
    templateUrl: './order-detail.html',
    styleUrls: ['./order-detail.css']
})
export class OrderDetailComponent implements OnInit {
    order: Order | null = null;
    loading = true;
    error: string | null = null;
    updateError: string | null = null;
    updateSuccess = false;
    selectedStatus = '';
    statusNote = '';
    updating = false;

    // Fulfillment
    carrier = '';
    trackingNumber = '';
    isPacked = false;
    savingFulfillment = false;

    statusOptions = ['Pending', 'Processing', 'Shipped', 'Delivered', 'Cancelled'];

    // Timeline steps in order
    private readonly STEPS = ['Pending', 'Processing', 'Shipped', 'Delivered'];

    constructor(private route: ActivatedRoute, private orderService: OrderService) {}

    ngOnInit(): void {
        const orderId = this.route.snapshot.paramMap.get('id');
        if (orderId) this.loadOrder(orderId);
    }

    loadOrder(id: string): void {
        this.loading = true;
        this.error = null;
        this.orderService.getOrderById(id).subscribe({
            next: (data: any) => {
                this.order = data;
                this.selectedStatus = data.status;
                // Populate fulfillment fields if present
                if (data.carrier) this.carrier = data.carrier;
                if (data.trackingNumber) this.trackingNumber = data.trackingNumber;
                if (data.isPacked) this.isPacked = data.isPacked;
                this.loading = false;
            },
            error: (err: any) => {
                this.error = 'Failed to load order details';
                this.loading = false;
            }
        });
    }

    updateStatus(): void {
        if (!this.order || this.updating || this.selectedStatus === this.order.status) return;
        this.updating = true;
        this.updateError = null;
        this.updateSuccess = false;
        this.orderService.updateOrderStatus(this.order._id, this.selectedStatus, this.statusNote.trim() || undefined).subscribe({
            next: (updatedOrder: any) => {
                if (this.order) {
                    this.order.status = updatedOrder.order?.status || updatedOrder.status || this.selectedStatus;
                    // If the API returned the full updated statusHistory, use it; otherwise inject locally
                    if (updatedOrder.order?.statusHistory) {
                        this.order.status_history = updatedOrder.order.statusHistory;
                    } else if (updatedOrder.statusHistory) {
                        this.order.status_history = updatedOrder.statusHistory;
                    } else {
                        // Optimistic local update so Timeline refreshes immediately
                        if (!this.order.status_history) this.order.status_history = [];
                        this.order.status_history = [
                            ...this.order.status_history,
                            {
                                status: this.selectedStatus,
                                timestamp: new Date().toISOString(),
                                note: this.statusNote.trim()
                            }
                        ];
                    }
                }
                this.statusNote = '';
                this.updating = false;
                this.updateSuccess = true;
                setTimeout(() => this.updateSuccess = false, 3000);
            },
            error: (err: any) => {
                this.updateError = err.error?.message || 'Failed to update status.';
                this.updating = false;
            }
        });
    }

    cancelOrder(): void {
        if (!this.order || !confirm('Are you sure you want to cancel this order?')) return;
        this.selectedStatus = 'Cancelled';
        this.statusNote = 'Order cancelled by admin.';
        this.updateStatus();
    }

    saveFulfillment(): void {
        if (!this.order) return;
        this.savingFulfillment = true;
        this.orderService.updateFulfillment(this.order._id, {
            carrier: this.carrier,
            trackingNumber: this.trackingNumber,
            isPacked: this.isPacked
        }).subscribe({
            next: (res: any) => {
                if (this.order) {
                    this.order.carrier = res.order?.carrier ?? this.carrier;
                    this.order.trackingNumber = res.order?.trackingNumber ?? this.trackingNumber;
                    this.order.isPacked = res.order?.isPacked ?? this.isPacked;
                }
                this.savingFulfillment = false;
                this.updateSuccess = true;
                setTimeout(() => this.updateSuccess = false, 3000);
            },
            error: (err: any) => {
                this.updateError = err.error?.message || 'Failed to save fulfillment.';
                this.savingFulfillment = false;
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Generates a short human-friendly order ID from creation date + last 4 chars of _id */
    getShortId(): string {
        if (!this.order) return '';
        const date = new Date(this.order.createdAt);
        const ddMM = `${String(date.getDate()).padStart(2,'0')}${String(date.getMonth()+1).padStart(2,'0')}`;
        const tail = this.order._id.slice(-4).toUpperCase();
        return `ORD-${ddMM}-${tail}`;
    }

    copyId(): void {
        if (!this.order) return;
        navigator.clipboard.writeText(this.order._id).then(() => {
            // Quick feedback — could use a toast service
            const prev = document.querySelector('.short-id-chip') as HTMLElement;
            if (prev) { prev.style.background = '#dcfce7'; setTimeout(() => prev.style.background = '', 1000); }
        });
    }

    getUserInitials(): string {
        const name = (this.order?.user as any)?.name || '';
        return name.split(' ').map((w: string) => w[0]).join('').slice(0,2).toUpperCase() || '?';
    }

    /** Returns the real Product_ID SKU from item, not the MongoDB _id */
    getItemSku(item: any): string {
        // item.productId may be populated object with Product_ID, or item.sku, or raw _id
        if (item.sku) return item.sku;
        if (item.product && typeof item.product === 'object') return item.product.Product_ID || item.product.product_id || '—';
        if (item.productId && typeof item.productId === 'object') return item.productId.Product_ID || item.productId.product_id || '—';
        // If product is just a string ID that looks like a Product_ID (not 24-char hex)
        const raw = item.product || item.productId || '';
        if (typeof raw === 'string' && raw.startsWith('PRD')) return raw;
        return '—'; // Don't show raw ObjectId
    }

    getItemSubtotal(item: any): number {
        return (item.price || item.Price || 0) * (item.quantity || item.Quantity || 0);
    }

    get itemsSubtotal(): number {
        if (!this.order?.items) return 0;
        return this.order.items.reduce((sum: number, item: any) =>
            sum + (item.price || item.Price || 0) * (item.quantity || item.Quantity || 0), 0);
    }

    /** Build inferred steps if no status_history exists */
    getTimelineSteps(): { label: string; done: boolean; active: boolean }[] {
        const currentIdx = this.STEPS.indexOf(this.order?.status || '');
        return this.STEPS.map((label, i) => ({
            label,
            done: i < currentIdx,
            active: i === currentIdx
        }));
    }

    /** Merges camelCase (backend) and snake_case (legacy) statusHistory into one array */
    getHistoryArray(): StatusHistory[] {
        return (this.order?.statusHistory || this.order?.status_history || []) as StatusHistory[];
    }

    isHistoryDone(i: number): boolean { return i < this.getHistoryArray().length - 1; }
    isHistoryActive(i: number): boolean { return i === this.getHistoryArray().length - 1; }

    getStatusClass(status: string): string {
        const m: { [k: string]: string } = {
            Pending: 'status-pending', Processing: 'status-processing',
            Shipped: 'status-shipped', Delivered: 'status-delivered', Cancelled: 'status-cancelled'
        };
        return m[status] || 'status-default';
    }

    formatDate(date: string): string {
        if (!date) return '—';
        return new Date(date).toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    }

    formatPrice(price: number): string { return `$${(price || 0).toFixed(2)}`; }

    resolveImage(path: string): string {
        if (!path) return '';
        if (path.startsWith('http') || path.startsWith('data:')) return path;
        const base = environment.apiUrl.replace('/api/v1', '');
        return `${base}/${path.startsWith('/') ? path.slice(1) : path}`;
    }

    printInvoice(): void { window.print(); }
}
