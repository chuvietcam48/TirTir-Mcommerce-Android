import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CustomerService, Customer } from '../../../core/services/customer.service';

@Component({
    selector: 'app-customer-list',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule],
    templateUrl: './customer-list.html',
    styleUrls: ['./customer-list.css']
})
export class CustomerListComponent implements OnInit {
    customers: Customer[] = [];
    filteredCustomers: Customer[] = [];
    loading = true;
    error: string | null = null;

    // Pagination
    currentPage = 1;
    pageSize = 10;
    totalPages = 1;

    // Filters
    searchQuery = '';
    /** '' = All, 'active' = !isBlocked, 'blocked' = isBlocked */
    selectedStatus = '';

    sortMode: 'newest' | 'none' = 'none';

    constructor(
        private customerService: CustomerService,
        private route: ActivatedRoute
    ) { }

    ngOnInit(): void {
        this.route.queryParams.subscribe(params => {
            const sort = params['sort'];
            this.sortMode = sort === 'newest' ? 'newest' : 'none';
            this.loadCustomers();
        });
    }

    loadCustomers(): void {
        this.loading = true;
        this.error = null;

        const params: any = {
            page: this.currentPage,
            limit: this.pageSize,
            role: 'user'
        };

        if (this.searchQuery.trim()) {
            params.search = this.searchQuery.trim();
        }

        this.customerService.getAllCustomers(params).subscribe({
            next: (data: any) => {
                // Backend returns { users, page, pages, total, roleFilter }
                this.customers = Array.isArray(data) ? data : (data.users || data.data || []);
                this.totalPages = data.pages || 1;
                this.applyFilters();
                this.loading = false;
            },
            error: (err: any) => {
                this.error = 'Failed to load customers';
                this.loading = false;
                console.error('Customer load error:', err);
            }
        });
    }

    applyFilters(): void {
        let filtered = [...this.customers];

        // Sort newest first when deep-linked from General
        if (this.sortMode === 'newest') {
            filtered.sort((a: any, b: any) => {
                const ta = new Date(a.createdAt || 0).getTime();
                const tb = new Date(b.createdAt || 0).getTime();
                return tb - ta;
            });
        }

        // Status filter — uses isBlocked (boolean) from backend User model
        if (this.selectedStatus === 'active') {
            filtered = filtered.filter(c => !c.isBlocked);
        } else if (this.selectedStatus === 'blocked') {
            filtered = filtered.filter(c => c.isBlocked);
        }

        this.filteredCustomers = filtered;
    }

    onSearch(): void {
        this.currentPage = 1;
        this.loadCustomers();
    }

    onStatusChange(): void { this.applyFilters(); }

    get paginatedCustomers(): Customer[] {
        // Since we switched to server-side search/pagination, 
        // the `customers` array is already the current page.
        // But the `selectedStatus` filter is still client-side here.
        // Let's keep it client-side for now as it's simpler.
        return this.filteredCustomers;
    }

    changePage(page: number): void {
        if (page >= 1 && page <= this.totalPages) {
            this.currentPage = page;
            this.loadCustomers();
        }
    }

    formatDate(date: string): string {
        return new Date(date).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    }
}
