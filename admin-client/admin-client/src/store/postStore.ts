import { create } from 'zustand';
import { Post } from './types';
import { postApi } from '@/lib/api';

const MOCK_POSTS: Post[] = [
    { id: 'p1', authorId: 'u1', content: 'Cây sầu riêng nhà tôi xuất hiện vết loét trên thân, chảy nhựa màu nâu. Mọi người có biết cách xử lý không?', imageUrls: ['https://placehold.co/400x300/dcfce7/166534?text=SauRieng'], imagePaths: [], upvoteCount: 24, downvoteCount: 2, commentCount: 8, tags: ['sầu riêng', 'bệnh xì mủ', 'cầu giúp đỡ'], isDeleted: false, createdAt: '2024-02-05T08:30:00Z', updatedAt: '2024-02-05T08:30:00Z' },
    { id: 'p2', authorId: 'u2', content: 'Chia sẻ kinh nghiệm phòng trừ bệnh đạo ôn trên lúa hiệu quả. Phun Tricyclazole 2 lần cách nhau 7 ngày.', imageUrls: [], imagePaths: [], upvoteCount: 45, downvoteCount: 1, commentCount: 12, tags: ['lúa', 'đạo ôn', 'kinh nghiệm'], isDeleted: false, createdAt: '2024-02-10T14:00:00Z', updatedAt: '2024-02-10T14:00:00Z' },
    { id: 'p3', authorId: 'u1', content: 'Lá cây xuất hiện đốm vàng lan rộng, có vẻ như bị bệnh rỉ sắt. Ai có thuốc đặc trị không?', imageUrls: ['https://placehold.co/400x300/fef9c3/854d0e?text=LaVang', 'https://placehold.co/400x300/fef9c3/854d0e?text=DomVang'], imagePaths: [], upvoteCount: 11, downvoteCount: 0, commentCount: 5, tags: ['rỉ sắt', 'lá vàng'], isDeleted: false, createdAt: '2024-02-18T09:15:00Z', updatedAt: '2024-02-18T09:15:00Z' },
    { id: 'p4', authorId: 'u4', content: 'Spam content vi phạm nội quy cộng đồng', imageUrls: [], imagePaths: [], upvoteCount: 0, downvoteCount: 15, commentCount: 0, tags: [], isDeleted: true, deletedAt: '2024-02-20T10:00:00Z', createdAt: '2024-02-20T09:00:00Z', updatedAt: '2024-02-20T10:00:00Z' },
    { id: 'p5', authorId: 'u6', content: 'Hướng dẫn bón phân hữu cơ cho cây ăn trái. Sử dụng phân vi sinh kết hợp với phân chuồng ủ hoai mục.', imageUrls: ['https://placehold.co/400x300/dbeafe/1e40af?text=PhânBón'], imagePaths: [], upvoteCount: 67, downvoteCount: 3, commentCount: 21, tags: ['phân bón', 'hữu cơ', 'cây ăn trái'], isDeleted: false, createdAt: '2024-03-01T11:00:00Z', updatedAt: '2024-03-01T11:00:00Z' },
];

interface PostStore {
    items: Post[];
    loading: boolean;
    error: string | null;
    searchQuery: string;
    sortOrder: 'asc' | 'desc';
    fetchItems: () => Promise<void>;
    updateItem: (id: string, content: string, tags: string[]) => Promise<void>;
    deleteItem: (id: string) => Promise<void>;
    setSearchQuery: (q: string) => void;
    setSortOrder: (o: 'asc' | 'desc') => void;
    getFiltered: () => Post[];
}

export const usePostStore = create<PostStore>((set, get) => ({
    items: [],
    loading: false,
    error: null,
    searchQuery: '',
    sortOrder: 'desc',

    fetchItems: async () => {
        set({ loading: true, error: null });
        try {
            const data = await postApi.listPosts();
            set({ items: data.content, loading: false });
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Failed to load posts';
            set({ items: [], error: message, loading: false });
        }
    },

    updateItem: async (id, content, tags) => {
        const updated = await postApi.updatePost(id, content, tags);
        set(s => ({ items: s.items.map(i => i.id === id ? updated : i) }));
    },

    deleteItem: async (id) => {
        await postApi.deletePost(id);
        set(s => ({ items: s.items.map(i => i.id === id ? { ...i, isDeleted: true } : i) }));
    },

    setSearchQuery: (q) => set({ searchQuery: q }),
    setSortOrder: (o) => set({ sortOrder: o }),

    getFiltered: () => {
        const { items, searchQuery, sortOrder } = get();
        const q = searchQuery.toLowerCase();
        return (items ?? [])
            .filter(p =>
                p.content.toLowerCase().includes(q) ||
                p.tags.some(t => t.toLowerCase().includes(q))
            )
            .sort((a, b) => {
                const d = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                return sortOrder === 'asc' ? d : -d;
            });
    },
}));