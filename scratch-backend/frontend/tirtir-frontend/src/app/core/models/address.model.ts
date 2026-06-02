export interface Address {
    _id?: string;
    fullName: string;
    phone: string;
    street: string;
    city: string;
    district: string;
    ward: string;
    isDefault: boolean;
}

export interface UpdateProfileRequest {
    name?: string;
    avatar?: string;
    phone?: string;
    gender?: 'Male' | 'Female' | 'Other';
    birthDate?: Date | string;
}

export interface ChangePasswordRequest {
    currentPassword: string;
    newPassword: string;
}
