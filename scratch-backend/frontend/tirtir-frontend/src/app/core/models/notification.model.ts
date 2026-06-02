export interface INotification {
    _id: string;
    user: string;
    type: 'order' | 'promotion' | 'system';
    title: string;
    message: string;
    link: string;
    image?: string;
    isRead: boolean;
    createdAt: string | Date;
    updatedAt?: string | Date;
}
