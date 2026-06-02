import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterLink, RouterLinkActive } from '@angular/router';
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

    userName = '';

    menuItems = [
        { path: 'profile', label: 'Profile' },
        { path: 'addresses', label: 'Address Book' },
        { path: 'password', label: 'Change Password' },
        { path: 'orders', label: 'Order History' },
        { path: 'notifications', label: 'Notifications' }
    ];

    ngOnInit() {
        this.authService.currentUser$.subscribe(user => {
            this.userName = (user as any)?.name || (user as any)?.fullName || '';
        });
    }
}
