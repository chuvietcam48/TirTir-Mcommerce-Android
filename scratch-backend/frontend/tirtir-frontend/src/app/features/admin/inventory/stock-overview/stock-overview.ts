import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminInventoryService } from '../../services/admin-inventory.service';

@Component({
    selector: 'app-stock-overview',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="page">
      <div class="page-header">
        <h1>Stock Overview</h1>
        <button class="btn-outline" (click)="cleanup()">Cleanup Pending Reservations</button>
      </div>
      <div class="table-card"><div class="table-wrapper">
        <table class="admin-table">
          <thead><tr><th>Product ID</th><th>Name</th><th>Category</th><th>In Stock</th><th>Reserved</th><th>Status</th></tr></thead>
          <tbody>
            @if (loading) { <tr><td colspan="6" class="empty">Loading...</td></tr> }
            @for (item of items; track item.Product_ID) {
              <tr>
                <td class="mono">{{ item.Product_ID }}</td>
                <td class="bold">{{ item.Name }}</td>
                <td>{{ item.Category || '—' }}</td>
                <td [class]="item.Stock_Quantity < 10 ? 'danger-text' : ''">{{ item.Stock_Quantity }}</td>
                <td>{{ item.Stock_Reserved || 0 }}</td>
                <td><span class="badge" [class]="item.Stock_Quantity === 0 ? 'out' : item.Stock_Quantity < 10 ? 'low' : 'ok'">{{ item.Stock_Quantity === 0 ? 'Out of Stock' : item.Stock_Quantity < 10 ? 'Low' : 'In Stock' }}</span></td>
              </tr>
            }
          </tbody>
        </table>
      </div></div>
    </div>
  `,
    styleUrl: './stock-overview.css',
})
export class StockOverviewComponent implements OnInit {
    private svc = inject(AdminInventoryService);
    items: any[] = []; loading = true;

    ngOnInit() {
        this.svc.getAlerts(999).subscribe({
            next: (r: any) => { this.items = r.lowStock?.items || []; this.loading = false; },
            error: () => this.loading = false,
        });
    }

    cleanup() {
        if (!confirm('Cleanup pending stock reservations?')) return;
        this.svc.cleanupPending().subscribe({ next: () => alert('Cleanup complete'), error: (e) => alert('Error: ' + e.message) });
    }
}
