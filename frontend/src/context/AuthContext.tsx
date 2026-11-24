import {createContext, useContext, useState, useCallback, useEffect, useRef, type ReactNode} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {login as requestLogin} from '../services/authService';
import {setApiSession} from '../services/apiClient';
import type {LoginRequest, User} from '../types/auth';
interface AuthState { user: User | null; login: (credentials: LoginRequest) => Promise<void>; logout: () => void }
const AuthContext = createContext<AuthState | null>(null);
export function AuthProvider({children}: {children: ReactNode}) {
  const [user,setUser] = useState<User|null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const queryClient = useQueryClient();
  const logout = useCallback(() => {
    clearTimeout(timer.current); setApiSession(null); setUser(null);
    void queryClient.cancelQueries(); queryClient.clear();
  },[queryClient]);
  useEffect(() => () => {clearTimeout(timer.current); setApiSession(null);},[]);
  const login = async (credentials: LoginRequest) => {
    const result = await requestLogin(credentials);
    logout();
    setApiSession(result.accessToken,logout);
    timer.current = setTimeout(logout,result.expiresIn * 1000);
    setUser(result.user);
  };
  return <AuthContext.Provider value={{user,login,logout}}>{children}</AuthContext.Provider>;
}
export function useAuth() {const value = useContext(AuthContext); if (!value) throw new Error('AuthProvider is required'); return value;}
