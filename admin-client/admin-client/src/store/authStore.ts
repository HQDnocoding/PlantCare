import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { authApi } from '@/lib/api';

interface AdminUser {
    id: string;
    email: string;
    fullName: string;
}

interface AuthState {
    user: AdminUser | null;
    token: string | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    error: string | null;
    login: (email: string, password: string) => Promise<void>;
    logout: () => void;
}

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            user: null,
            token: null,
            isAuthenticated: false,
            isLoading: false,
            error: null,

            login: async (email, password) => {
                set({ isLoading: true, error: null });
                try {
                    const data = await authApi.adminLogin(email, password);
                    set({
                        user: data.user,
                        token: data.token,
                        isAuthenticated: true,
                        isLoading: false,
                    });
                } catch {
                    // Fallback: mock login khi backend chưa khởi động
                    if (email === 'admin@plantapp.com' && password === 'admin123') {
                        set({
                            user: { id: 'mock-1', email, fullName: 'Admin' },
                            token: 'mock-token',
                            isAuthenticated: true,
                            isLoading: false,
                        });
                    } else {
                        set({ error: 'Email hoặc mật khẩu không đúng', isLoading: false });
                    }
                }
            },

            logout: () => {
                set({ user: null, token: null, isAuthenticated: false });
            },
        }),
        {
            name: 'admin-auth',
            partialize: (s) => ({ user: s.user, token: s.token, isAuthenticated: s.isAuthenticated }),
        }
    )
);