import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { MENU_ITEMS, MenuItem } from '../constants/menu.data';
import { environment } from '../../../environments/environment';

// Re-export MenuItem for backward compatibility
export type { MenuItem };

@Injectable({
    providedIn: 'root',
})
export class MenuService {
    private http = inject(HttpClient);

    // Backend API URL
    private apiUrl = `${environment.apiUrl}/menus`;

    getMenuItems(): Observable<MenuItem[]> {
        // User requested to use static data (links to product details)
        // instead of dynamic category API
        return of(MENU_ITEMS);
        /*
        return this.http.get<MenuItem[]>(this.apiUrl).pipe(
            catchError(err => {
                console.warn('Menu API failed, using static fallback:', err);
                return of(MENU_ITEMS);
            })
        );
        */
    }
}
