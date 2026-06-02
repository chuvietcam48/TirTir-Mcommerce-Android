import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';

@Component({
    selector: 'app-admin-topbar',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './topbar.html',
    styleUrl: './topbar.css',
})
export class AdminTopbarComponent {
    @Input() sidebarCollapsed = false;
    @Output() toggleSidebar = new EventEmitter<void>();

    private authService = inject(AuthService);
    private router = inject(Router);

    showUserMenu = false;

    get userName(): string {
        return this.authService.currentUserValue?.name || 'Admin';
    }

    onToggleSidebar(): void {
        this.toggleSidebar.emit();
    }

    toggleUserMenu(): void {
        this.showUserMenu = !this.showUserMenu;
    }

    logout(): void {
        this.authService.logout();
        this.router.navigate(['/login']);
    }
}
