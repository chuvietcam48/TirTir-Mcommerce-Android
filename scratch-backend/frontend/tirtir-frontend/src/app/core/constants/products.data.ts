
export interface ProductShade {
    name: string;
    color: string;
    image?: string;
    Stock_Quantity?: number;
}

export interface ProductData {
    id: string;
    slug: string;
    name: string;
    price: number;
    originalPrice?: number;
    rating: number;
    reviewCount: number;
    description: string;
    fullDescription: string;
    keyFeatures: string[];
    howToUse: string;
    ingredients: string;
    images: string[];
    descriptionImages: string[];
    shades?: ProductShade[];
    sizes?: { name: string; price: number }[];
    category: 'makeup' | 'skincare' | 'other' | 'Gift Card' | string;
    subcategory?: 'face' | 'lip' | 'cleanse-tone' | 'treatments' | 'moisturize-sunscreen';
}

export const PRODUCTS: ProductData[] = [];

// Helper function to find product by slug
// @deprecated - Use ProductService.getProductDetail(slug) instead
export function getProductBySlug(slug: string): ProductData | undefined {
    return undefined;
}
