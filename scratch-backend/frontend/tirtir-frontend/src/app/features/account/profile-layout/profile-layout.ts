import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
    selector: 'app-profile-layout',
    standalone: true,
    imports: [CommonModule, RouterModule, RouterLink, RouterLinkActive],
    templateUrl: './profile-layout.html',
    styleUrl: './profile-layout.css'
})
export class ProfileLayoutComponent implements OnInit {
    private authService = inject(AuthService);
    private router = inject(Router);

    userName = '';
    userEmail = '';
    userInitials = '';

    menuItems = [
        { path: 'profile', label: 'Profile', icon: '👤' },
        { path: 'addresses', label: 'Address Book', icon: '📍' },
        { path: 'password', label: 'Change Password', icon: '🔒' },
        { path: 'orders', label: 'Order History', icon: '📦' },
        { path: 'notifications', label: 'Notifications', icon: '🔔' }
    ];

    ngOnInit() {
        this.authService.currentUser$.subscribe(user => {
            const u = user as any;
            this.userName = u?.name || u?.fullName || '';
            this.userEmail = u?.email || '';
            this.userInitials = this.getInitials(this.userName);
        });
    }

    private getInitials(name: string): string {
        if (!name) return 'U';
        const parts = name.trim().split(' ');
        if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
        return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    logout(): void {
        this.authService.logout();
    }
}
