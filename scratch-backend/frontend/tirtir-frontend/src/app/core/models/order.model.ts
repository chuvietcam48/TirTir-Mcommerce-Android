export interface OrderItem {
    product: string | {
        _id?: string;
        Name?: string;
        Thumbnail_Images?: string;
        Price?: number;
        Product_ID?: string;
        slug?: string;
    };
    name: string;
    quantity: number;
    price: number;
    shade?: string;
    image?: string;
}

export interface OrderStatusHistory {
    status: OrderStatus;
    timestamp: string | Date;
    note?: string;
}

export interface ShippingAddress {
    fullName: string;
    phone: string;
    address: string;
    ward?: string;
    district?: string;
    city: string;
}

export type OrderStatus = 'Pending' | 'Processing' | 'Shipped' | 'Delivered' | 'Cancelled';

export type PaymentMethod = 'VNPAY' | 'MOMO' | 'CARD';

export interface Order {
    _id: string;
    user: string;
    items: OrderItem[];
    shippingAddress: ShippingAddress;
    paymentMethod: PaymentMethod;
    totalAmount: number;
    status: OrderStatus;
    statusHistory?: OrderStatusHistory[];
    createdAt: Date;
    updatedAt: Date;
}

export interface CreateOrderRequest {
    shippingAddress: ShippingAddress;
    paymentMethod: PaymentMethod | string;
    saveToAddressBook?: boolean;
}

export interface CreateOrderResponse {
    message: string;
    orderId: string;
}
