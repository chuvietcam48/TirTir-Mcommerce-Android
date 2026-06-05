export interface CartItem {
    product: {
        _id: string;
        Name: string;
        Price: number;
        Thumbnail_Images: string;
        Original_Price?: number;
        Size?: string;
        slug: string; // Added for hyperlinks
        Product_Attributes?: any[]; // Added for quick edit
    };
    quantity: number;
    shade?: string;
    _id?: string;
}

export interface Cart {
    _id: string;
    user: string;
    items: CartItem[];
    totalPrice: number;
    createdAt?: Date;
    updatedAt?: Date;

    coupon?: any; 
    discountAmount?: number;
}

export interface AddToCartRequest {
    productId: string;
    quantity: number;
    shade?: string;
}

export interface UpdateCartItemRequest {
    productId: string;
    quantity: number;
    shade?: string;
    oldShade?: string; // Quick edit support
    newShade?: string; // Quick edit support
}
