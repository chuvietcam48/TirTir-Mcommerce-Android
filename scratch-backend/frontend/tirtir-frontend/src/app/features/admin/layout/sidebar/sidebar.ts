import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface NavItem {
    label: string;
    route: string;
    children?: { label: string; route: string }[];
}

@Component({
    selector: 'app-admin-sidebar',
    standalone: true,
    imports: [CommonModule, RouterLink, RouterLinkActive],
    templateUrl: './sidebar.html',
    styleUrl: './sidebar.css',
})
export class AdminSidebarComponent {
    @Input() collapsed = false;
    @Output() collapseToggle = new EventEmitter<void>();

    expandedMenu: string | null = null;

    navItems: NavItem[] = [
        { label: 'Dashboard', route: '/admin/dashboard' },
        {
            label: 'Products',
            route: '/admin/products',
            children: [
                { label: 'All Products', route: '/admin/products' },
                { label: 'Add Product', route: '/admin/products/new' },
            ],
        },
        { label: 'Orders', route: '/admin/orders' },
        { label: 'Customers', route: '/admin/customers' },
        {
            label: 'Inventory',
            route: '/admin/inventory',
            children: [
                { label: 'Stock Overview', route: '/admin/inventory' },
                { label: 'Stock Logs', route: '/admin/inventory/logs' },
            ],
        },
        {
            label: 'Promotions',
            route: '/admin/promotions',
            children: [
                { label: 'All Coupons', route: '/admin/promotions' },
                { label: 'Create Coupon', route: '/admin/promotions/new' },
            ],
        },
        { label: 'Reviews', route: '/admin/reviews' },
        { label: 'Analytics', route: '/admin/analytics' },
        { label: 'Settings', route: '/admin/settings' },
        { label: 'Admin Users', route: '/admin/admin-users' },
    ];

    toggleMenu(label: string): void {
        this.expandedMenu = this.expandedMenu === label ? null : label;
    }

    onToggleSidebar(): void {
        this.collapseToggle.emit();
    }
}
