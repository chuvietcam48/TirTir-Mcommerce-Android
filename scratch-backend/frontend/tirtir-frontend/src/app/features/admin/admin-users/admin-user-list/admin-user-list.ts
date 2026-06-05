import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminUserService, AdminUser } from '../../services/admin-user.service';

@Component({
    selector: 'app-admin-user-list',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="page">
      <div class="page-header"><h1>Admin Users</h1><button class="btn-primary" (click)="showForm = !showForm">{{ showForm ? 'Cancel' : '+ Create Admin' }}</button></div>

      @if (showForm) {
        <div class="form-card">
          <div class="form-grid">
            <div class="g"><label>Name</label><input class="input" [(ngModel)]="newAdmin.name" name="name"></div>
            <div class="g"><label>Email</label><input class="input" [(ngModel)]="newAdmin.email" name="email" type="email"></div>
            <div class="g"><label>Password</label><input class="input" [(ngModel)]="newAdmin.password" name="password" type="password"></div>
            <div class="g"><label>Role</label>
              <select class="input" [(ngModel)]="newAdmin.role" name="role"><option value="admin">Admin</option><option value="inventory_staff">Inventory Staff</option><option value="customer_service">Customer Service</option></select>
            </div>
          </div>
          <button class="btn-primary" style="margin-top:16px" (click)="createAdmin()" [disabled]="saving">{{ saving ? 'Creating...' : 'Create' }}</button>
        </div>
      }

      <div class="table-card"><div class="table-wrapper">
        <table class="admin-table">
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Joined</th></tr></thead>
          <tbody>
            @if (loading) { <tr><td colspan="4" class="empty">Loading...</td></tr> }
            @for (a of admins; track a._id) {
              <tr><td class="bold">{{ a.name }}</td><td class="muted">{{ a.email }}</td><td><span class="role-badge">{{ a.role }}</span></td><td class="muted">{{ a.createdAt | date:'dd/MM/yy' }}</td></tr>
            }
          </tbody>
        </table>
      </div></div>
    </div>
  `,
    styleUrl: './admin-user-list.css',
})
export class AdminUserListComponent implements OnInit {
    private svc = inject(AdminUserService);
    admins: AdminUser[] = []; loading = true; showForm = false; saving = false;
    newAdmin = { name: '', email: '', password: '', role: 'admin' };

    ngOnInit() { this.load(); }

    load() {
        this.loading = true;
        this.svc.getAdmins().subscribe({
            next: (r: any) => { this.admins = Array.isArray(r) ? r : (r.admins || r.data || []); this.loading = false; },
            error: () => this.loading = false,
        });
    }

    createAdmin() {
        this.saving = true;
        this.svc.createAdmin(this.newAdmin).subscribe({
            next: () => { this.saving = false; this.showForm = false; this.newAdmin = { name: '', email: '', password: '', role: 'admin' }; this.load(); },
            error: (e) => { this.saving = false; alert('Error: ' + (e.error?.message || e.message)); },
        });
    }
}
