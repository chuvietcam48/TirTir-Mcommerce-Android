import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Shade {
    _id?: string;
    Shade_ID: string;
    Product_ID: string;
    Parent_ID: string;
    Shade_Code: string;
    Shade_Category_Name: string;
    Shade_Name: string;
    Hex_Code: string;
    Shade_Image?: string;
    Shade_Type?: string;
    Undertone?: string;
    Finish_Type?: string;
    Coverage_Profile?: string;
    Oxidation_Risk_Level?: string;
    Skin_Tone?: string;
    Skin_Type?: string;
    No?: number;
    // Color analytics
    R?: number; G?: number; B?: number;
    L?: number; a?: number; b?: number;
}

@Injectable({ providedIn: 'root' })
export class ShadeService {
    private apiUrl = `${environment.apiUrl}/shades`;

    constructor(private http: HttpClient) { }

    /** GET /api/v1/shades?productId=xxx — fetch all shades of a product */
    getShadesByProduct(productId: string): Observable<Shade[]> {
        const params = new HttpParams().set('productId', productId).set('limit', '200');
        return this.http.get<Shade[]>(this.apiUrl, { params });
    }

    /** Get single shade by Shade_ID */
    getShadeById(shadeId: string): Observable<Shade> {
        return this.http.get<Shade>(`${this.apiUrl}/${shadeId}`);
    }

    /** POST /api/v1/shades — create shade (requires admin auth) */
    createShade(shade: Partial<Shade>): Observable<Shade> {
        return this.http.post<Shade>(this.apiUrl, shade);
    }

    /** PUT /api/v1/shades/:shadeId — update shade */
    updateShade(shadeId: string, shade: Partial<Shade>): Observable<Shade> {
        return this.http.put<Shade>(`${this.apiUrl}/${shadeId}`, shade);
    }

    /** DELETE /api/v1/shades/:shadeId — delete shade */
    deleteShade(shadeId: string): Observable<any> {
        return this.http.delete(`${this.apiUrl}/${shadeId}`);
    }
}
