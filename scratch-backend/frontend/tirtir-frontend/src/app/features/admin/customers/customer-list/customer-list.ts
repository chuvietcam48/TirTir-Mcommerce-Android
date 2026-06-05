import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminUserService, AdminUser } from '../../services/admin-user.service';

@Component({
    selector: 'app-customer-list',
    standalone: true,
    imports: [CommonModule, RouterLink, FormsModule],
    template: `
    <div class="page">
      <div class="page-header"><h1>Customers</h1></div>
      <div class="filters-bar">
        <input type="text" class="filter-input" placeholder="Search customers..." [(ngModel)]="search" (keyup.enter)="load()">
      </div>
      <div class="table-card"><div class="table-wrapper">
        <table class="admin-table">
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Verified</th><th>Status</th><th>Joined</th><th>Actions</th></tr></thead>
          <tbody>
            @if (loading) { <tr><td colspan="7" class="empty">Loading...</td></tr> }
            @if (!loading && users.length === 0) { <tr><td colspan="7" class="empty">No customers found</td></tr> }
            @for (u of users; track u._id) {
              <tr>
                <td class="bold">{{ u.name }}</td>
                <td class="muted">{{ u.email }}</td>
                <td><span class="role-badge">{{ u.role }}</span></td>
                <td>{{ u.isEmailVerified ? 'Yes' : 'No' }}</td>
                <td>
                  <span class="status-dot" [class]="u.isBlocked ? 'dot-blocked' : 'dot-active'"></span>
                  {{ u.isBlocked ? 'Blocked' : 'Active' }}
                </td>
                <td class="muted">{{ u.createdAt | date:'dd/MM/yy' }}</td>
                <td>
                  <a class="link" [routerLink]="['/admin/customers', u._id]">View</a>
                  <button class="link-btn" [class.danger]="!u.isBlocked" (click)="toggleBlock(u)">{{ u.isBlocked ? 'Unblock' : 'Block' }}</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div></div>
    </div>
  `,
    styleUrl: './customer-list.css',
})
export class CustomerListComponent implements OnInit {
    private svc = inject(AdminUserService);
    users: AdminUser[] = []; loading = true; search = '';

    ngOnInit() { this.load(); }

    load() {
        this.loading = true;
        this.svc.getUsers({ search: this.search || undefined }).subscribe({
            next: (r: any) => { this.users = Array.isArray(r) ? r : (r.users || r.data || []); this.loading = false; },
            error: () => this.loading = false,
        });
    }

    toggleBlock(u: AdminUser) {
        const action = u.isBlocked ? 'unblock' : 'block';
        if (!confirm(`${action} ${u.name}?`)) return;
        this.svc.updateUserStatus(u._id, !u.isBlocked).subscribe({ next: () => { u.isBlocked = !u.isBlocked; } });
    }
}
