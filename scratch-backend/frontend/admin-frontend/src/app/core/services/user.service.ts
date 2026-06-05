import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * UserService — maps to backend admin.users.routes.js:
 *   GET  /api/v1/admin/users            (list all users, admin+cs)
 *   GET  /api/v1/admin/users/:id        (user detail, admin+cs)
 *   GET  /api/v1/admin/users/:id/orders (user orders, admin+cs)
 *   PUT  /api/v1/admin/users/:id/status (block/unblock, admin+cs)
 *   GET  /api/v1/admin/users/admins     (admin users, admin only)
 *   POST /api/v1/admin/users/admin      (create admin user, admin only)
 *   PUT  /api/v1/admin/users/:id/role   (update role, admin only)
 *   DELETE /api/v1/admin/users/:id      (delete user, admin only)
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private apiUrl = `${environment.apiUrl}/admin/users`;

  constructor(private http: HttpClient) { }

  /** GET /api/v1/admin/users — list all users */
  getAllUsers(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }

  /** GET /api/v1/admin/users/:id */
  getUserById(id: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}`);
  }

  /** GET /api/v1/admin/users/:id/orders */
  getUserOrders(id: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}/orders`);
  }

  /**
   * PUT /api/v1/admin/users/:id/status
   * Body: { status: 'active' | 'blocked' }
   */
  updateUserStatus(id: string, status: 'active' | 'blocked'): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}/status`, { status });
  }

  /** Convenience wrappers */
  blockUser(id: string): Observable<any> {
    return this.updateUserStatus(id, 'blocked');
  }

  unblockUser(id: string): Observable<any> {
    return this.updateUserStatus(id, 'active');
  }

  // ── Admin management (admin role only) ──────────────────────
  getAdminUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/admins`);
  }

  createAdminUser(user: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/admin`, user);
  }

  updateUserRole(id: string, role: string): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}/role`, { role });
  }

  deleteUser(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`);
  }
}
