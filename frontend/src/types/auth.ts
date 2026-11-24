export type UserRole = 'operator' | 'manager' | 'planner' | 'business_manager' | 'admin';
export interface User { id: number; login: string; fullName: string; role: UserRole }
export interface LoginRequest { login: string; password: string }
export interface LoginResponse { accessToken: string; tokenType: 'Bearer'; expiresIn: number; user: User }
