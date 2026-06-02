export interface SkinProfile {
  skinTone: string;
  undertone: string;
  skinType: string;
  concerns: string[];
  confidence: number;
  lastAnalyzedAt: Date | string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: 'user' | 'admin';
  isEmailVerified?: boolean;
  avatar?: string;
  phone?: string;
  gender?: 'Male' | 'Female' | 'Other';
  birthDate?: Date | string;
  addresses?: any[];
  skinProfile?: SkinProfile;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  success: boolean;
  token: string;
  user: User;
  message?: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  password: string;
}
