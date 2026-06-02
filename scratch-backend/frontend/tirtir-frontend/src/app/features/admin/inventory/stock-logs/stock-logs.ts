import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdminInventoryService } from '../../services/admin-inventory.service';

@Component({
    selector: 'app-stock-logs',
    standalone: true,
    imports: [CommonModule, RouterLink],
    template: `
    <div class="page">
      <div class="page-header"><h1>Stock Logs</h1><a class="back" routerLink="/admin/inventory">← Back</a></div>
      <div class="table-card"><div class="table-wrapper">
        <table class="admin-table">
          <thead><tr><th>Date</th><th>Product</th><th>Change</th><th>Reason</th><th>Admin</th></tr></thead>
          <tbody>
            @if (loading) { <tr><td colspan="5" class="empty">Loading...</td></tr> }
            @if (!loading && logs.length === 0) { <tr><td colspan="5" class="empty">No logs found</td></tr> }
            @for (log of logs; track log._id) {
              <tr>
                <td class="muted">{{ log.createdAt | date:'dd/MM/yy HH:mm' }}</td>
                <td>{{ log.product?.Name || log.productId || '—' }}</td>
                <td [class]="log.quantity > 0 ? 'positive' : 'negative'">{{ log.quantity > 0 ? '+' : '' }}{{ log.quantity }}</td>
                <td>{{ log.reason || '—' }}</td>
                <td class="muted">{{ log.admin?.name || '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div></div>
    </div>
  `,
    styleUrl: './stock-logs.css',
})
export class StockLogsComponent implements OnInit {
    private svc = inject(AdminInventoryService);
    logs: any[] = []; loading = true;

    ngOnInit() {
        this.svc.getLogs({ limit: 50 }).subscribe({
            next: (r: any) => { this.logs = Array.isArray(r) ? r : (r.logs || r.data || []); this.loading = false; },
            error: () => this.loading = false,
        });
    }
}
