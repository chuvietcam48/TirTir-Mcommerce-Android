import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AdminSidebarComponent } from './sidebar/sidebar';
import { AdminTopbarComponent } from './topbar/topbar';

@Component({
    selector: 'app-admin-layout',
    standalone: true,
    imports: [RouterOutlet, AdminSidebarComponent, AdminTopbarComponent],
    templateUrl: './admin-layout.html',
    styleUrl: './admin-layout.css',
})
export class AdminLayoutComponent {
    sidebarCollapsed = false;

    toggleSidebar(): void {
        this.sidebarCollapsed = !this.sidebarCollapsed;
    }
}
