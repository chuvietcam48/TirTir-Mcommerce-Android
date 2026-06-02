import { Routes } from '@angular/router';
import { AdminLayoutComponent } from './layout/admin-layout';
import { adminGuard } from './guards/admin.guard';

export const ADMIN_ROUTES: Routes = [
    {
        path: '',
        component: AdminLayoutComponent,
        canActivate: [adminGuard],
        children: [
            { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
            {
                path: 'dashboard',
                loadComponent: () =>
                    import('./dashboard/dashboard').then(m => m.AdminDashboardComponent),
            },
            {
                path: 'products',
                loadComponent: () =>
                    import('./products/product-list/product-list').then(m => m.ProductListComponent),
            },
            {
                path: 'products/new',
                loadComponent: () =>
                    import('./products/product-form/product-form').then(m => m.ProductFormComponent),
            },
            {
                path: 'products/:id/edit',
                loadComponent: () =>
                    import('./products/product-form/product-form').then(m => m.ProductFormComponent),
            },
            {
                path: 'orders',
                loadComponent: () =>
                    import('./orders/order-list/order-list').then(m => m.AdminOrderListComponent),
            },
            {
                path: 'orders/:id',
                loadComponent: () =>
                    import('./orders/order-detail/order-detail').then(m => m.AdminOrderDetailComponent),
            },
            {
                path: 'customers',
                loadComponent: () =>
                    import('./customers/customer-list/customer-list').then(m => m.CustomerListComponent),
            },
            {
                path: 'customers/:id',
                loadComponent: () =>
                    import('./customers/customer-detail/customer-detail').then(m => m.CustomerDetailComponent),
            },
            {
                path: 'inventory',
                loadComponent: () =>
                    import('./inventory/stock-overview/stock-overview').then(m => m.StockOverviewComponent),
            },
            {
                path: 'inventory/logs',
                loadComponent: () =>
                    import('./inventory/stock-logs/stock-logs').then(m => m.StockLogsComponent),
            },
            {
                path: 'promotions',
                loadComponent: () =>
                    import('./promotions/coupon-list/coupon-list').then(m => m.CouponListComponent),
            },
            {
                path: 'promotions/new',
                loadComponent: () =>
                    import('./promotions/coupon-form/coupon-form').then(m => m.CouponFormComponent),
            },
            {
                path: 'promotions/:id/edit',
                loadComponent: () =>
                    import('./promotions/coupon-form/coupon-form').then(m => m.CouponFormComponent),
            },
            {
                path: 'reviews',
                loadComponent: () =>
                    import('./reviews/review-list/review-list').then(m => m.ReviewListComponent),
            },
            {
                path: 'analytics',
                loadComponent: () =>
                    import('./analytics/analytics').then(m => m.AnalyticsComponent),
            },
            {
                path: 'settings',
                loadComponent: () =>
                    import('./settings/settings.component').then(m => m.AdminSettingsComponent),
            },
            {
                path: 'admin-users',
                loadComponent: () =>
                    import('./admin-users/admin-user-list/admin-user-list').then(m => m.AdminUserListComponent),
            },
        ],
    },
];
