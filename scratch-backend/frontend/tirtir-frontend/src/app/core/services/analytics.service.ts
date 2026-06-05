import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Minimal website view tracking for admin analytics.
 * Sends one "view" per route path per browser session to avoid spam.
 *
 * Backend endpoint: POST /api/v1/analytics/view
 * - If productId is omitted, backend still increments DailyStats.views (site-wide views).
 */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private started = false;

  startPageViewTracking(): void {
    if (this.started) return;
    this.started = true;

    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        const url = e.urlAfterRedirects || e.url;
        const path = url.split('?')[0] || '/';

        const key = `tirtir_pv:${path}`;
        if (sessionStorage.getItem(key)) return;
        sessionStorage.setItem(key, '1');

        this.http
          .post(`${environment.apiUrl}/analytics/view`, {})
          .subscribe({ error: () => void 0 });
      });
  }
}

