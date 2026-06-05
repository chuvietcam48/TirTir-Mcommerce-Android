import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User, Address, UpdateProfileRequest, ChangePasswordRequest } from '../models';

@Injectable({
    providedIn: 'root'
})
export class UserService {
    private apiUrl = `${environment.apiUrl}/users`;
    private http = inject(HttpClient);

    // ===== PROFILE OPERATIONS =====

    /**
     * Get current user profile
     */
    getProfile(): Observable<User> {
        return this.http.get<{ success: boolean; data: User }>(`${this.apiUrl}/profile`).pipe(
            map(response => response.data)
        );
    }

    getMyReviews(): Observable<any[]> {
        return this.http.get<any>(`${this.apiUrl}/my-reviews`).pipe(
            map((response: any) => response?.data || response?.reviews || response || [])
        );
    }

    /**
     * Update user profile
     */
    updateProfile(data: UpdateProfileRequest): Observable<User> {
        return this.http.put<{ success: boolean; data: User }>(`${this.apiUrl}/profile`, data).pipe(
            map(response => response.data)
        );
    }

    /**
     * Change user password
     */
    changePassword(data: ChangePasswordRequest): Observable<{ success: boolean; message: string }> {
        return this.http.post<{ success: boolean; message: string }>(
            `${this.apiUrl}/change-password`,
            data
        );
    }

    /**
     * Upload avatar image
     */
    uploadAvatar(file: File): Observable<{ avatar: string; user: User }> {
        const formData = new FormData();
        formData.append('avatar', file);

        return this.http.post<{ success: boolean; data: { avatar: string; user: User } }>(
            `${this.apiUrl}/avatar/upload`,
            formData
        ).pipe(
            map(response => response.data)
        );
    }


    // ===== ADDRESS OPERATIONS =====

    /**
     * Get all user addresses
     */
    getAddresses(): Observable<Address[]> {
        return this.http.get<{ success: boolean; data: Address[] }>(`${this.apiUrl}/addresses`).pipe(
            map(response => response.data)
        );
    }

    /**
     * Add new address
     */
    addAddress(address: Omit<Address, '_id'>): Observable<Address[]> {
        return this.http.post<{ success: boolean; data: Address[] }>(`${this.apiUrl}/addresses`, address).pipe(
            map(response => response.data)
        );
    }

    /**
     * Update existing address
     */
    updateAddress(id: string, address: Partial<Address>): Observable<Address[]> {
        return this.http.put<{ success: boolean; data: Address[] }>(`${this.apiUrl}/addresses/${id}`, address).pipe(
            map(response => response.data)
        );
    }

    /**
     * Delete address
     */
    deleteAddress(id: string): Observable<Address[]> {
        return this.http.delete<{ success: boolean; data: Address[] }>(`${this.apiUrl}/addresses/${id}`).pipe(
            map(response => response.data)
        );
    }

    /**
     * Set address as default
     */
    setDefaultAddress(id: string): Observable<Address[]> {
        return this.http.patch<{ success: boolean; data: Address[] }>(
            `${this.apiUrl}/addresses/${id}/set-default`,
            {}
        ).pipe(
            map(response => response.data)
        );
    }
}
