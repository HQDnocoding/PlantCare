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
                } catch (err) {
                    // Extract friendly error message
                    let errorMsg = 'Đăng nhập không thành công';
                    if (err instanceof Error) {
                        errorMsg = err.message;
                    }
                    set({ error: errorMsg, isLoading: false });
                }
            },

            logout: () => {
                // Clear auth state and ensure localStorage is updated
                set({ user: null, token: null, isAuthenticated: false, error: null });
                // Clear localStorage explicitly to ensure it's persisted
                if (typeof window !== 'undefined') {
                    localStorage.removeItem('admin-auth');
                }
            },
        }),
        {
            name: 'admin-auth',
            partialize: (s) => ({ user: s.user, token: s.token, isAuthenticated: s.isAuthenticated }),
        }
    )
);