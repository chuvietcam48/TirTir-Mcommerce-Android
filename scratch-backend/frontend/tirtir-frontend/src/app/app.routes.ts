import { Routes } from '@angular/router';
import { MakeupComponent } from './features/makeup/makeup';
import { CushionComponent } from './features/cushion/cushion';
import { LoginComponent } from './features/auth/login/login';
import { RegisterComponent } from './features/auth/register/register';
import { ForgotPasswordComponent } from './features/auth/forgot-password/forgot-password';
import { ResetPasswordComponent } from './features/auth/reset-password/reset-password';
import { CartComponent } from './features/cart/cart';
import { CheckoutComponent } from './features/checkout/checkout';
import { OrderConfirmationComponent } from './features/order-confirmation/order-confirmation';
import { OrderHistoryComponent } from './features/account/order-history/order-history';
import { authGuard } from './core/guards/auth.guard';
import { canDeactivateGuard } from './core/guards/can-deactivate.guard';
import { ContactComponent } from './features/contact/contact';

export const routes: Routes = [
    { path: '', loadComponent: () => import('./features/home/home').then(m => m.HomeComponent) },
    { path: 'shop', loadComponent: () => import('./features/shop/shop').then(m => m.ShopComponent) },
    { path: 'contact', component: ContactComponent },
    // Auth routes
    { path: 'login', component: LoginComponent },
    { path: 'register', component: RegisterComponent },
    { path: 'forgot-password', component: ForgotPasswordComponent },
    { path: 'reset-password/:token', component: ResetPasswordComponent },
    // Cart & Checkout (protected)
    { path: 'cart', component: CartComponent, canActivate: [authGuard] },
    { path: 'checkout', component: CheckoutComponent, canActivate: [authGuard] },
    { path: 'checkout/success', loadComponent: () => import('./features/checkout/success/success').then(m => m.OrderSuccessComponent), canActivate: [authGuard] },
    { path: 'checkout/failure', loadComponent: () => import('./features/checkout/failure/failure').then(m => m.OrderFailureComponent), canActivate: [authGuard] },
    { path: 'order-confirmation/:id', component: OrderConfirmationComponent, canActivate: [authGuard] },
    // Account (protected) - Profile with nested routes
    {
        path: 'account',
        loadComponent: () => import('./features/account/profile-layout/profile-layout').then(m => m.ProfileLayoutComponent),
        canActivate: [authGuard],
        children: [
            { path: '', redirectTo: 'profile', pathMatch: 'full' },
            {
                path: 'profile',
                loadComponent: () => import('./features/account/profile-info/profile-info').then(m => m.ProfileInfoComponent),
                canDeactivate: [canDeactivateGuard]
            },
            {
                path: 'addresses',
                loadComponent: () => import('./features/account/address-book/address-book').then(m => m.AddressBookComponent)
            },
            {
                path: 'password',
                loadComponent: () => import('./features/account/change-password/change-password').then(m => m.ChangePasswordComponent)
            },
            {
                path: 'orders',
                component: OrderHistoryComponent
            },
            {
                path: 'orders/:id',
                loadComponent: () => import('./features/account/order-detail/order-detail.component').then(m => m.OrderDetailComponent)
            },
            {
                path: 'notifications',
                loadComponent: () => import('./features/account/notifications/notifications.component').then(m => m.NotificationsComponent)
            }
        ]
    },
    // Admin routes
    {
        path: 'admin/settings',
        loadComponent: () => import('./features/admin/settings/settings.component').then(m => m.AdminSettingsComponent),
        canActivate: [authGuard]
    },
    // Collection pages (category-based product listings)
    { path: 'deals', loadComponent: () => import('./features/deals/deals').then(m => m.DealsComponent) },
    { path: 'collections/:slug', loadComponent: () => import('./features/collection/collection').then(m => m.CollectionComponent) },
    // Virtual Services (Shade Finder)
    { path: 'virtual-services', loadComponent: () => import('./features/shade-finder/shade-finder').then(m => m.ShadeFinderComponent) },
    // Legacy routes for backward compatibility
    { path: 'makeup/cushions', component: CushionComponent },
    // Gift Card route (must be before :slug)
    {
        path: 'products/tirtir-gift-card',
        loadComponent: () => import('./features/gift-card/gift-card').then(m => m.GiftCardComponent)
    },
    // Product detail page
    { path: 'products/:slug', loadComponent: () => import('./features/product-detail/product-detail').then(m => m.ProductDetailComponent) },
    // Policy pages
    { path: 'policies/terms-of-service', loadComponent: () => import('./features/policies/terms/terms').then(m => m.TermsComponent) },
    { path: 'policies/privacy-policy', loadComponent: () => import('./features/policies/privacy/privacy').then(m => m.PrivacyComponent) },
    { path: 'policies/shipping-policy', loadComponent: () => import('./features/policies/shipping/shipping').then(m => m.ShippingComponent) },
    { path: 'policies/refund-policy', loadComponent: () => import('./features/policies/refund/refund').then(m => m.RefundComponent) },
    { path: '**', redirectTo: '' }
];
