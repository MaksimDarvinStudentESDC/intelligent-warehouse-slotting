import {api} from './apiClient';
import type {LoginRequest, LoginResponse, User} from '../types/auth';
export const login = (credentials: LoginRequest) => api<LoginResponse>('/auth/login', {method:'POST', body:JSON.stringify(credentials)});
export const getCurrentUser = () => api<User>('/auth/me');
