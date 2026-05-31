import { create } from 'zustand';
import { User, UserRole, UserStatus } from './types';
import { userApi } from '@/lib/api';

const MOCK_USERS: User[] = [
    { id: 'u1', email: 'nguyenvana@email.com', phone: '0901234567', fullName: 'Nguyễn Văn A', avatarUrl: undefined, role: 'FARMER', status: 'ACTIVE', createdAt: '2024-01-15T10:00:00Z', updatedAt: '2024-01-15T10:00:00Z' },
    { id: 'u2', email: 'tranthib@email.com', phone: '0912345678', fullName: 'Trần Thị B', avatarUrl: undefined, role: 'FARMER', status: 'ACTIVE', createdAt: '2024-01-20T10:00:00Z', updatedAt: '2024-01-20T10:00:00Z' },
    { id: 'u3', email: 'levanc@email.com', phone: undefined, fullName: 'Lê Văn C', avatarUrl: undefined, role: 'ADMIN', status: 'ACTIVE', createdAt: '2024-02-01T10:00:00Z', updatedAt: '2024-02-01T10:00:00Z' },
    { id: 'u4', email: 'phamthid@email.com', phone: '0934567890', fullName: 'Phạm Thị D', avatarUrl: undefined, role: 'FARMER', status: 'BLOCKED', createdAt: '2024-02-10T10:00:00Z', updatedAt: '2024-03-01T10:00:00Z' },
    { id: 'u5', email: 'hoangvane@email.com', phone: '0945678901', fullName: 'Hoàng Văn E', avatarUrl: undefined, role: 'FARMER', status: 'UNVERIFIED', createdAt: '2024-03-05T10:00:00Z', updatedAt: '2024-03-05T10:00:00Z' },
    { id: 'u6', email: 'vuthif@email.com', phone: '0956789012', fullName: 'Vũ Thị F', avatarUrl: undefined, role: 'FARMER', status: 'ACTIVE', createdAt: '2024-03-20T10:00:00Z', updatedAt: '2024-03-20T10:00:00Z' },
];

interface UserStore {
    items: User[];
    loading: boolean;
    error: string | null;
    searchQuery: string;
    sortOrder: 'asc' | 'desc';
    fetchItems: () => Promise<void>;
    updateStatus: (id: string, status: UserStatus) => Promise<void>;
    updateRole: (id: string, role: UserRole) => Promise<void>;
    setSearchQuery: (q: string) => void;
    setSortOrder: (o: 'asc' | 'desc') => void;
    getFiltered: () => User[];
}

export const useUserStore = create<UserStore>((set, get) => ({
    items: [],
    loading: false,
    error: null,
    searchQuery: '',
    sortOrder: 'desc',

    fetchItems: async () => {
        set({ loading: true, error: null });
        try {
            const data = await userApi.listUsers();
            set({ items: data.content, loading: false });
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Failed to load users';
            set({ items: [], error: message, loading: false });
        }
    },

    updateStatus: async (id, status) => {
        const updated = await userApi.updateStatus(id, status);
        set(s => ({ items: s.items.map(i => i.id === id ? updated : i) }));
    },

    updateRole: async (id, role) => {
        const updated = await userApi.updateRole(id, role);
        set(s => ({ items: s.items.map(i => i.id === id ? updated : i) }));
    },

    setSearchQuery: (q) => set({ searchQuery: q }),
    setSortOrder: (o) => set({ sortOrder: o }),

    getFiltered: () => {
        const { items, searchQuery, sortOrder } = get();
        const q = searchQuery.toLowerCase();
        return (items ?? [])
            .filter(u =>
                u.fullName.toLowerCase().includes(q) ||
                u.email.toLowerCase().includes(q)
            )
            .sort((a, b) => {
                const d = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                return sortOrder === 'asc' ? d : -d;
            });
    },
}));