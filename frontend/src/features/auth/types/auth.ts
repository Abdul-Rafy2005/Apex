export type UserRole = 'SUPER_ADMIN' | 'ORG_ADMIN' | 'INSTRUCTOR' | 'TRADER';

export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  user: UserResponse;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}
