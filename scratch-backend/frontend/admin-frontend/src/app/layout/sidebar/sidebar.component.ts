import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
    label: string;
    route: string;
    roles?: string[];
}

interface MenuGroup {
    title: string;
    items: NavItem[];
}

@Component({
    selector: 'app-sidebar',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.css']
})
export class SidebarComponent implements OnInit {

    showLogout = false;

    allMenuGroups: MenuGroup[] = [
        {
            title: '',
            items: [
                { label: 'General', route: '/dashboard' },
                { label: 'Products', route: '/products', roles: ['admin'] },
                { label: 'Inventory', route: '/inventory', roles: ['admin', 'inventory_staff'] },
                { label: 'Orders', route: '/orders', roles: ['admin', 'customer_service'] },
                { label: 'GHN Simulator', route: '/shipping-simulator', roles: ['admin'] },
                { label: 'Customers', route: '/customers', roles: ['admin', 'customer_service'] },
                { label: 'Coupons', route: '/coupons', roles: ['admin'] },
                { label: 'Reviews', route: '/reviews', roles: ['admin', 'customer_service'] },
            ]
        },
        {
            title: 'SYSTEM',
            items: [
                { label: 'Staff Users', route: '/users', roles: ['admin'] },
                { label: 'Settings', route: '/settings', roles: ['admin'] },
            ]
        }
    ];

    visibleGroups: MenuGroup[] = [];

    constructor(private authService: AuthService, private router: Router) { }

    ngOnInit(): void {
        this.filterItems();
        this.authService.currentUser$.subscribe(() => this.filterItems());
    }

    filterItems(): void {
        const user = this.authService.getCurrentUser();
        if (!user) { this.visibleGroups = []; return; }
        const role = user.role;
        this.visibleGroups = this.allMenuGroups.map(group => ({
            ...group,
            items: group.items.filter(item => !item.roles || item.roles.includes(role))
        })).filter(group => group.items.length > 0);
    }

    toggleLogout(): void {
        this.showLogout = !this.showLogout;
    }

    logout(): void {
        this.showLogout = false;
        this.authService.logout();
        this.router.navigate(['/login']);
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: Event): void {
        const target = event.target as HTMLElement;
        if (!target.closest('.sidebar-brand')) {
            this.showLogout = false;
        }
    }
}
