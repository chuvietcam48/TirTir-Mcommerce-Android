import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { adminAuthGuard, adminAuthChildGuard } from './core/guards/admin-auth.guard';

export const routes: Routes = [
    {
        path: 'login',
        component: LoginComponent
    },
    {
        path: '',
        component: MainLayoutComponent,
        canActivate: [adminAuthGuard],
        canActivateChild: [adminAuthChildGuard],
        children: [
            {
                path: 'dashboard',
                component: DashboardComponent
            },
            // ─── Products ─────────────────────────────────────────
            {
                path: 'products',
                data: { roles: ['admin'] },
                children: [
                    {
                        path: '',
                        loadComponent: () =>
                            import('./features/products/product-list/product-list').then(
                                (m) => m.ProductListComponent
                            ),
                    },
                    {
                        path: 'add',
                        loadComponent: () =>
                            import('./features/products/product-form/product-form').then(
                                (m) => m.ProductFormComponent
                            ),
                    },
                    {
                        path: 'detail/:id',
                        loadComponent: () =>
                            import('./features/products/product-detail/product-detail').then(
                                (m) => m.ProductDetailComponent
                            ),
                    },
                    {
                        path: 'edit/:id',
                        loadComponent: () =>
                            import('./features/products/product-form/product-form').then(
                                (m) => m.ProductFormComponent
                            ),
                    },
                    {
                        path: 'low-stock',
                        loadComponent: () =>
                            import('./features/products/low-stock-alerts/low-stock-alerts').then(
                                (m) => m.LowStockAlertsComponent
                            ),
                    },
                ],
            },
            // ─── Orders ───────────────────────────────────────────
            {
                path: 'orders',
                children: [
                    {
                        path: '',
                        loadComponent: () =>
                            import('./features/orders/order-list/order-list').then(
                                (m) => m.OrderListComponent
                            ),
                    },
                    {
                        path: ':id',
                        loadComponent: () =>
                            import('./features/orders/order-detail/order-detail').then(
                                (m) => m.OrderDetailComponent
                            ),
                    },
                ],
            },
            // ─── Customers ────────────────────────────────────────
            {
                path: 'customers',
                children: [
                    {
                        path: '',
                        loadComponent: () =>
                            import('./features/customers/customer-list/customer-list').then(
                                (m) => m.CustomerListComponent
                            ),
                    },
                    {
                        path: ':id',
                        loadComponent: () =>
                            import('./features/customers/customer-detail/customer-detail').then(
                                (m) => m.CustomerDetailComponent
                            ),
                    },
                ],
            },
            // ─── Marketing (alias → Coupons) ──────────────────────
            {
                path: 'marketing',
                children: [
                    {
                        path: '',
                        loadComponent: () =>
                            import('./features/coupons/coupon-list/coupon-list').then(
                                (m) => m.CouponListComponent
                            ),
                    },
                    {
                        path: 'add',
                        loadComponent: () =>
                            import('./features/coupons/coupon-form/coupon-form').then(
                                (m) => m.CouponFormComponent
                            ),
                    },
                    {
                        path: 'edit/:id',
                        loadComponent: () =>
                            import('./features/coupons/coupon-form/coupon-form').then(
                                (m) => m.CouponFormComponent
                            ),
                    },
                ],
            },
            // ─── Coupons (kept for backward compat) ───────────────
            {
                path: 'coupons',
                children: [
                    {
                        path: '',
                        loadComponent: () =>
                            import('./features/coupons/coupon-list/coupon-list').then(
                                (m) => m.CouponListComponent
                            ),
                    },
                    {
                        path: 'add',
                        loadComponent: () =>
                            import('./features/coupons/coupon-form/coupon-form').then(
                                (m) => m.CouponFormComponent
                            ),
                    },
                    {
                        path: 'edit/:id',
                        loadComponent: () =>
                            import('./features/coupons/coupon-form/coupon-form').then(
                                (m) => m.CouponFormComponent
                            ),
                    },
                ],
            },
            // ─── Reviews ──────────────────────────────────────────
            {
                path: 'reviews',
                children: [
                    {
                        path: '',
                        loadComponent: () =>
                            import('./features/reviews/reviews.component').then(
                                (m) => m.ReviewsComponent
                            ),
                    },
                    {
                        path: ':id',
                        loadComponent: () =>
                            import('./features/reviews/review-detail/review-detail').then(
                                (m) => m.ReviewDetailComponent
                            ),
                    },
                ],
            },
            // ─── Analytics ────────────────────────────────────────
            {
                path: 'analytics',
                data: { roles: ['admin'] },
                loadComponent: () =>
                    import('./features/analytics/analytics.component').then(
                        (m) => m.AnalyticsComponent
                    ),
            },
            // ─── Inventory ────────────────────────────────────────
            {
                path: 'inventory',
                data: { roles: ['admin', 'inventory_staff'] },
                children: [
                    {
                        path: '',
                        loadComponent: () =>
                            import('./features/inventory/inventory-dashboard/inventory-dashboard').then(
                                (m) => m.InventoryDashboardComponent
                            ),
                    },
                    {
                        path: 'logs',
                        loadComponent: () =>
                            import('./features/inventory/stock-logs/stock-logs').then(
                                (m) => m.StockLogsComponent
                            ),
                    },
                    {
                        path: 'adjust/:id',
                        loadComponent: () =>
                            import('./features/inventory/stock-adjustment/stock-adjustment').then(
                                (m) => m.StockAdjustmentComponent
                            ),
                    },
                ],
            },
            // ─── Staff Users ──────────────────────────────────────
            {
                path: 'users',
                data: { roles: ['admin'] },
                children: [
                    {
                        path: '',
                        loadComponent: () => import('./features/users/user-list/user-list.component').then(m => m.UserListComponent)
                    },
                    {
                        path: 'add',
                        loadComponent: () => import('./features/users/user-form/user-form.component').then(m => m.UserFormComponent)
                    }
                ]
            },
            // ─── Settings ─────────────────────────────────────────
            {
                path: 'settings',
                data: { roles: ['admin'] },
                loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
            },
            // ─── Default ──────────────────────────────────────────
            {
                path: 'shipping-simulator',
                data: { roles: ['admin'] },
                loadComponent: () =>
                    import('./features/shipping-simulator/shipping-simulator').then(
                        (m) => m.ShippingSimulatorComponent
                    ),
            },
            {
                path: '',
                redirectTo: 'dashboard',
                pathMatch: 'full'
            }
        ]
    }
];
