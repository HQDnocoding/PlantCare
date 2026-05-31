import { create } from 'zustand';
import { Medicine } from './types';
import { medicineApi, MedicineForm } from '@/lib/api';

const MOCK_MEDICINES: Medicine[] = [
    { id: 1, name: 'Ridomil Gold 68WG', activeIngredient: 'Metalaxyl-M + Mancozeb', formulation: 'WG (hạt thấm nước)', usage: 'Pha 2g/lít nước, phun đều lên tán lá và thân cây', dosage: '2–3 g/lít nước', weatherCondition: 'Tránh phun khi trời nắng gắt hoặc sắp mưa', toxicity: 'Nhóm III (ít độc)', safetyWarnings: ['Không để gần thực phẩm', 'Đeo khẩu trang khi phun', 'Rửa tay sau khi sử dụng'], preHarvestInterval: '7 ngày', diseases: [{ id: 1, order: 1, className: 'XiMu', name: 'Bệnh xì mủ', description: '', symptoms: [], cause: '', favorableConditions: '', treatment: '', prevention: '', version: '1.0', createdAt: '', updatedAt: '' }], createdAt: '2024-01-05T00:00:00Z', updatedAt: '2024-01-05T00:00:00Z' },
    { id: 2, name: 'Carbendazim 50SC', activeIngredient: 'Carbendazim', formulation: 'SC (huyền phù đậm đặc)', usage: 'Pha 1–1.5 ml/lít nước, phun phòng định kỳ 14 ngày/lần', dosage: '1–1.5 ml/lít nước', weatherCondition: 'Hiệu quả cao khi trời không mưa 4–6 giờ sau phun', toxicity: 'Nhóm II (độc trung bình)', safetyWarnings: ['Không phun khi ra hoa', 'Độc với cá và sinh vật thủy sinh'], preHarvestInterval: '14 ngày', diseases: [], createdAt: '2024-01-08T00:00:00Z', updatedAt: '2024-01-08T00:00:00Z' },
    { id: 3, name: 'Mancozeb 80WP', activeIngredient: 'Mancozeb', formulation: 'WP (bột thấm nước)', usage: 'Pha 2–3 g/lít nước, phun đều toàn bộ tán cây', dosage: '2–3 g/lít nước', weatherCondition: 'Không dùng khi trời nắng nóng trên 35°C', toxicity: 'Nhóm III (ít độc)', safetyWarnings: ['Không hít bụi thuốc', 'Bảo quản nơi khô ráo thoáng mát'], preHarvestInterval: '10 ngày', diseases: [], createdAt: '2024-01-10T00:00:00Z', updatedAt: '2024-01-10T00:00:00Z' },
    { id: 4, name: 'Tricyclazole 75WP', activeIngredient: 'Tricyclazole', formulation: 'WP (bột thấm nước)', usage: 'Pha 0.6 g/lít nước, phun khi bệnh mới xuất hiện', dosage: '0.5–0.75 g/lít nước', weatherCondition: 'Hiệu quả nhất khi phun buổi sáng sớm', toxicity: 'Nhóm II (độc trung bình)', safetyWarnings: ['Không pha chung với thuốc có tính kiềm', 'Đọc kỹ hướng dẫn trước khi dùng'], preHarvestInterval: '21 ngày', diseases: [], createdAt: '2024-01-15T00:00:00Z', updatedAt: '2024-01-15T00:00:00Z' },
    { id: 5, name: 'Propiconazole 25EC', activeIngredient: 'Propiconazole', formulation: 'EC (nhũ dầu)', usage: 'Pha 1 ml/lít nước, phun 2 lần cách nhau 10 ngày', dosage: '0.8–1 ml/lít nước', weatherCondition: 'Tránh phun khi nhiệt độ trên 32°C', toxicity: 'Nhóm II (độc trung bình)', safetyWarnings: ['Tránh tiếp xúc với mắt', 'Không đổ ra nguồn nước'], preHarvestInterval: '14 ngày', diseases: [], createdAt: '2024-01-20T00:00:00Z', updatedAt: '2024-01-20T00:00:00Z' },
];

export type { MedicineForm };

interface MedicineStore {
    items: Medicine[];
    loading: boolean;
    error: string | null;
    searchQuery: string;
    sortOrder: 'asc' | 'desc';
    fetchItems: () => Promise<void>;
    addItem: (data: MedicineForm) => Promise<void>;
    updateItem: (id: number, data: Partial<MedicineForm>) => Promise<void>;
    assignDiseases: (id: number, diseaseIds: number[]) => Promise<void>;
    deleteItem: (id: number) => Promise<void>;
    setSearchQuery: (q: string) => void;
    setSortOrder: (o: 'asc' | 'desc') => void;
    getFiltered: () => Medicine[];
}

export const useMedicineStore = create<MedicineStore>((set, get) => ({
    items: [],
    loading: false,
    error: null,
    searchQuery: '',
    sortOrder: 'desc',

    fetchItems: async () => {
        set({ loading: true, error: null });
        try {
            const data = await medicineApi.listMedicines();
            set({ items: data, loading: false });
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Failed to load medicines';
            set({ items: [], error: message, loading: false });
        }
    },

    addItem: async (data) => {
        const created = await medicineApi.createMedicine(data);
        set(s => ({ items: [...s.items, created] }));
    },

    updateItem: async (id, data) => {
        const updated = await medicineApi.updateMedicine(id, data);
        set(s => ({ items: s.items.map(i => i.id === id ? updated : i) }));
    },

    assignDiseases: async (id, diseaseIds) => {
        const updated = await medicineApi.assignDiseases(id, diseaseIds);
        set(s => ({ items: s.items.map(i => i.id === id ? updated : i) }));
    },

    deleteItem: async (id) => {
        await medicineApi.deleteMedicine(id);
        set(s => ({ items: s.items.filter(i => i.id !== id) }));
    },

    setSearchQuery: (q) => set({ searchQuery: q }),
    setSortOrder: (o) => set({ sortOrder: o }),

    getFiltered: () => {
        const { items, searchQuery, sortOrder } = get();
        const q = searchQuery.toLowerCase();
        return (items ?? [])
            .filter(m =>
                m.name.toLowerCase().includes(q) ||
                m.activeIngredient.toLowerCase().includes(q)
            )
            .sort((a, b) => {
                const d = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                return sortOrder === 'asc' ? d : -d;
            });
    },
}));