import { Component, OnInit, ChangeDetectorRef, inject, signal } from '@angular/core';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of, map } from 'rxjs';
import { ProductData } from '../../../core/constants/products.data';
import { MenuItem, MenuService } from '../../../core/services/menu.service';
import { CartService } from '../../../core/services/cart.service';
import { AuthService } from '../../../core/services/auth.service';
import { MakeupMegaMenuComponent } from '../makeup-mega-menu/makeup-mega-menu';
import { SkincareMegaMenuComponent } from '../skincare-mega-menu/skincare-mega-menu';
import { ProductService } from '../../../core/services/product.service';
import { NotificationService } from '../../../core/services/notification.service';
import { TimeAgoPipe } from '../../pipes/time-ago.pipe';
import { INotification } from '../../../core/models/notification.model';
import { FreeShippingBarComponent } from '../free-shipping-bar/free-shipping-bar.component';
import { CurrencyPipe } from '@angular/common';

import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MakeupMegaMenuComponent, SkincareMegaMenuComponent, TimeAgoPipe, FreeShippingBarComponent, CurrencyPipe], // Add TimeAgoPipe
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class HeaderComponent implements OnInit {
  private menuService = inject(MenuService);
  private cartService = inject(CartService);
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);
  private productService = inject(ProductService);
  public notificationService = inject(NotificationService);
  public authService = inject(AuthService);

  searchTerm = '';
  showSearch = false;
  suggestions = signal<ProductData[]>([]);
  private searchSubject = new Subject<string>();

  cartCount = this.cartService.cartCount;
  cart$ = this.cartService.cart$;
  showMiniCart = false;

  // Notification State
  showNotifications = false;
  unreadCount$ = this.notificationService.unreadCount$;
  // Limit to 5 for dropdown
  recentNotifications$ = this.notificationService.notifications$.pipe(
    map(list => list.slice(0, 5))
  );

  menuItems: MenuItem[] = [];
  showMakeupMenu = false;
  showSkincareMenu = false;

  constructor() { }

  ngOnInit() {
    this.menuService.getMenuItems().subscribe({
      next: (data) => {
        this.menuItems = data;
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Failed to load menu items', err),
    });

    // Setup search suggestions
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(term => {
        if (!term.trim()) return of({ data: [] });
        return this.productService.getProducts({ keyword: term, limit: 5 });
      })
    ).subscribe({
      next: (response: any) => {
        this.suggestions.set(response.data);
      }
    });
  }

  get makeupCategories() {
    return this.menuItems.find(item => item.label === 'Makeup')?.children || [];
  }

  get skincareCategories() {
    return this.menuItems.find(item => item.label === 'Skincare')?.children || [];
  }

  // Giữ lại logic hover của bạn để kích hoạt Mega Menu
  onMakeupHover(show: boolean) {
    this.showMakeupMenu = show;
  }

  onSkincareHover(show: boolean) {
    this.showSkincareMenu = show;
  }

  onSearchInput() {
    this.searchSubject.next(this.searchTerm);
  }

  toggleSearch() {
    this.showSearch = !this.showSearch;
    if (!this.showSearch) {
      this.closeSearch();
    }
  }

  onSearch() {
    if (this.searchTerm.trim()) {
      this.router.navigate(['/shop'], {
        queryParams: { q: this.searchTerm.trim() }
      });
      this.closeSearch();
    }
  }

  closeSearch() {
    this.showSearch = false;
    this.searchTerm = '';
    this.suggestions.set([]);
  }

  selectSuggestion(product: ProductData) {
    this.router.navigate(['/products', product.slug]);
    this.closeSearch();
  }

  // ===== Notification Logic =====
  toggleNotifications(show: boolean) {
    this.showNotifications = show;
  }

  onNotificationClick(notification: INotification) {
    if (!notification.isRead) {
      this.notificationService.markAsRead(notification._id).subscribe();
    }
    this.showNotifications = false;
    this.router.navigateByUrl(notification.link);
  }

  markAllRead(event: Event) {
    event.stopPropagation(); // Prevent dropdown close or navigation
    this.notificationService.markAllAsRead().subscribe();
  }

  // ===== Mini Cart Logic =====
  toggleMiniCart(show: boolean) {
    this.showMiniCart = show;
  }

  // ===== Account Menu Logic =====
  showAccountMenu = false;

  toggleAccountMenu(show: boolean) {
    this.showAccountMenu = show;
  }

  logout() {
    this.showAccountMenu = false;
    this.authService.logout();
  }
}
