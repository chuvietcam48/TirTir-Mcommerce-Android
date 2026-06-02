import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { InventoryService, StockLog } from '../../../core/services/inventory.service';

@Component({
    selector: 'app-stock-logs',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule],
    templateUrl: './stock-logs.html',
    styleUrls: ['./stock-logs.css']
})
export class StockLogsComponent implements OnInit {
    logs: StockLog[] = [];
    loading = true;
    error: string | null = null;

    // Filters
    selectedAction = '';
    searchQuery = '';
    startDate = '';
    endDate = '';

    // Pagination (client-side for now)
    currentPage = 1;
    pageSize = 20;

    constructor(private inventoryService: InventoryService) { }

    ngOnInit(): void {
        this.loadLogs();
    }

    loadLogs(): void {
        this.loading = true;
        this.error = null;

        const filters: any = {};
        if (this.selectedAction) filters.action = this.selectedAction;
        if (this.startDate) filters.startDate = this.startDate;
        if (this.endDate) filters.endDate = this.endDate;

        this.inventoryService.getStockLogs(filters).subscribe({
            next: (data) => {
                this.logs = Array.isArray(data) ? data : [];
                this.loading = false;
            },
            error: (err) => {
                console.error('Logs load error:', err);
                this.error = 'Failed to load stock logs: ' + (err.error?.message || err.message || 'Server error');
                this.loading = false;
            }
        });
    }

    get filteredLogs(): StockLog[] {
        let filtered = this.logs;

        if (this.searchQuery) {
            const query = this.searchQuery.toLowerCase();
            filtered = filtered.filter(log =>
                log.product?.name?.toLowerCase().includes(query) ||
                log.product?.sku?.toLowerCase().includes(query)
            );
        }

        return filtered;
    }

    get paginatedLogs(): StockLog[] {
        const startIndex = (this.currentPage - 1) * this.pageSize;
        return this.filteredLogs.slice(startIndex, startIndex + this.pageSize);
    }

    get totalPages(): number {
        return Math.ceil(this.filteredLogs.length / this.pageSize);
    }

    changePage(page: number): void {
        if (page >= 1 && page <= this.totalPages) {
            this.currentPage = page;
        }
    }

    onFilterChange(): void {
        this.currentPage = 1;
        this.loadLogs();
    }

    formatDate(date: string): string {
        return new Date(date).toLocaleString();
    }
}
