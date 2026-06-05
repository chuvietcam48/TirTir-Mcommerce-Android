import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, of } from 'rxjs';
import { ProductData, PRODUCTS, getProductBySlug } from '../constants/products.data';
import { environment } from '../../../environments/environment';

export interface BackendProduct {
  Product_ID: string;
  Name: string;
  Price: number;
  Thumbnail_Images: string;
  Category: string;
  Is_Skincare: boolean;
  slug?: string;
  description?: string;
  images?: string[];
  category?: string;
  shades?: any[];
  // Matches backend response key from product.controller.js
  descriptionImages?: string[];
  Description_Images?: string[]; // Deprecated/Fallback
  Full_Description?: string;
  How_To_Use?: string;
  Rating_Average?: number; // ADDED
  Rating_Count?: number;   // ADDED
}

@Injectable({
  providedIn: 'root',
})
export class ProductService {
  private apiUrl = `${environment.apiUrl}/products`;
  private http = inject(HttpClient);

  getProducts(params?: any): Observable<{ data: ProductData[], total: number, categories: any[], concerns: any[], skinTypes: any[] }> {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach((key) => {
        if (params[key] !== null && params[key] !== undefined) {
          httpParams = httpParams.set(key, params[key]);
        }
      });
    }
    return this.http.get<{ data: BackendProduct[], total: number, categories: any[], concerns: any[], skinTypes: any[] }>(this.apiUrl, { params: httpParams }).pipe(
      map(response => {
        try {
          // 1. Filter out the specific variants we don't want to show in list
          const hiddenVariants = ['tirtir-gift-card-25', 'tirtir-gift-card-50', 'tirtir-gift-card-100'];

          const filteredBackendData = response.data.filter(p => {
            // Filter hidden variants
            if (hiddenVariants.includes(p.Product_ID)) return false;

            // Filter junk data
            const name = p.Name.toLowerCase();
            if (name.includes('bulk')) return false;
            if (name.includes('test')) return false;
            if (name.includes('demo')) return false;

            return true;
          });

          const mappedData = filteredBackendData.map(item => {
            const product = this.mapToProductData(item);
            // 2. Remap the main Gift Card to generic slug
            if (product.id === 'tirtir-gift-card-10') {
              product.slug = 'tirtir-gift-card';
              product.name = 'TIRTIR GIFT CARD';
              product.category = 'Gift Card';
              product.price = 10; // Ensure display starts at lowest
            }
            return product;
          });

          return {
            data: mappedData,
            total: response.total, // Use total from backend for correct pagination
            categories: response.categories || [],
            concerns: response.concerns || [],
            skinTypes: response.skinTypes || []
          };
        } catch (e) {
          console.error('ProductService: Error mapping data', e);
          throw e;
        }
      })
    );
  }

  getProductsByCategory(category: string, limit: number = 20): Observable<{ data: ProductData[], total: number, categories: any[], concerns: any[], skinTypes: any[] }> {
    return this.getProducts({ category, limit });
  }

  getProductDetail(idOrSlug: string): Observable<ProductData> {
    return this.http.get<BackendProduct>(`${this.apiUrl}/${idOrSlug}`).pipe(
      map(this.mapToProductData)
    );
  }

  getProductFilters(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/filters`);
  }

  private mapToProductData(bp: BackendProduct): ProductData {
    // Helper to ensure full URL for images
    const backendBase = environment.apiUrl.replace('/api/v1', '');
    const fixUrl = (url: string) => {
      if (!url) return '';
      if (url.startsWith('http')) return url;
      // Prepend backend URL. Remove leading slash if present to avoid double slashes.
      const cleanUrl = url.startsWith('/') ? url.substring(1) : url;
      return `${backendBase}/${cleanUrl}`;
    };

    return {
      id: bp.Product_ID,
      slug: bp.slug || bp.Product_ID.toLowerCase(), // Fallback
      name: bp.Name,
      price: bp.Price,
      originalPrice: bp.Price, // Use price since no real originalPrice field exists in backend
      rating: bp.Rating_Average || 0, // USE REAL FIELD
      reviewCount: bp.Rating_Count || 0, // USE REAL FIELD
      description: bp.description || bp.Name,
      fullDescription: bp.Full_Description || bp.description || bp.Name,
      keyFeatures: [], // Mock or empty
      howToUse: bp.How_To_Use || 'Apply gently to skin.',
      ingredients: 'See packaging for details.',
      images: bp.images && bp.images.length > 0
        ? bp.images.map(fixUrl)
        : [fixUrl(bp.Thumbnail_Images)],
      descriptionImages: bp.descriptionImages
        ? bp.descriptionImages.map(fixUrl)
        : (bp.Description_Images ? bp.Description_Images.map(fixUrl) : []),
      shades: bp.shades?.map(s => ({
        name: s.name || s.Shade_Name || s.Name,
        color: s.color || s.Hex_Code || s.Color_Code || '#000000',
        image: fixUrl(s.image || s.Image_Url || s.Shade_Image),
        Stock_Quantity: s.Stock_Quantity || 0
      })),
      sizes: [{ name: 'Standard', price: bp.Price }],
      // Smart Category Mapping from DB Data
      category: ((): any => {
        const cat = bp.Category ? bp.Category.toLowerCase() : '';
        if (cat.includes('cushion')) return 'cushion';
        if (cat.includes('lip') || cat.includes('tint') || cat.includes('balm')) return 'lip';
        if (cat.includes('toner')) return 'toner';
        if (cat.includes('serum')) return 'serum';
        if (cat.includes('foam') || cat.includes('cleanser')) return 'cleanser';
        if (cat.includes('cream')) return 'cream';
        if (cat.includes('sunscreen')) return 'sunscreen';
        if (cat.includes('mask')) return 'mask';
        if (cat.includes('primer')) return 'primer';
        if (cat.includes('fixer') || cat.includes('spray')) return 'fixer';
        if (cat.includes('oil')) return 'facial-oil';
        if (cat.includes('eye')) return 'eye-cream';
        if (cat.includes('ampoule')) return 'ampoule';

        // Fallback: Check ID for generic Makeup
        if (bp.Product_ID && bp.Product_ID.toUpperCase().includes('MK')) return 'makeup';

        return 'skincare';
      })(),
      subcategory: 'face', // Default fallback
    };
  }
}
