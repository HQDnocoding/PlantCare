import { create } from 'zustand';
import { Disease } from './types';
import { diseaseApi, DiseaseForm } from '@/lib/api';

const MOCK_DISEASES: Disease[] = [
    { id: 1, order: 1, className: 'XiMu', name: 'Bệnh xì mủ', description: 'Bệnh do nấm Phytophthora palmivora gây ra, phổ biến nhất trên cây sầu riêng', symptoms: ['Vết loét trên thân chảy nhựa màu nâu', 'Lá vàng và rụng sớm', 'Cành khô từng phần'], cause: 'Nấm Phytophthora palmivora', favorableConditions: 'Độ ẩm cao, mưa nhiều, đất thoát nước kém', treatment: 'Dùng Metalaxyl hoặc Fosetyl-Al, cạo sạch vết bệnh và bôi thuốc', prevention: 'Thoát nước tốt, không tưới nước vào chiều tối', medicines: [{ id: 1, name: 'Ridomil Gold 68WG', activeIngredient: 'Metalaxyl-M', formulation: 'WG', usage: '', dosage: '2g/lít', weatherCondition: '', toxicity: 'Nhóm III', safetyWarnings: [], preHarvestInterval: '7 ngày', createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' }], version: '1.0', createdAt: '2024-01-10T00:00:00Z', updatedAt: '2024-01-10T00:00:00Z' },
    { id: 2, order: 2, className: 'ThanThu', name: 'Bệnh thán thư', description: 'Bệnh do nấm Colletotrichum gloeosporioides gây ra', symptoms: ['Đốm nâu trên lá', 'Vết bệnh hình tròn viền vàng', 'Lá khô và rụng'], cause: 'Nấm Colletotrichum gloeosporioides', favorableConditions: 'Thời tiết nóng ẩm, mưa nhiều', treatment: 'Phun Carbendazim hoặc Mancozeb', prevention: 'Vệ sinh vườn, tỉa cành thông thoáng', medicines: [], version: '1.0', createdAt: '2024-01-12T00:00:00Z', updatedAt: '2024-01-12T00:00:00Z' },
    { id: 3, order: 3, className: 'RiSat', name: 'Bệnh rỉ sắt', description: 'Bệnh do nấm Phakopsora pachyrhizi gây ra trên nhiều loại cây', symptoms: ['Vết vàng da cam trên lá', 'Phần dưới lá có bào tử màu nâu đỏ', 'Lá vàng và rụng sớm'], cause: 'Nấm Phakopsora pachyrhizi', favorableConditions: 'Thời tiết mát mẻ, sương mù nhiều', treatment: 'Phun Propiconazole hoặc Azoxystrobin', prevention: 'Trồng giống kháng bệnh, luân canh cây trồng', medicines: [], version: '1.0', createdAt: '2024-01-15T00:00:00Z', updatedAt: '2024-01-15T00:00:00Z' },
    { id: 4, order: 4, className: 'DaoOn', name: 'Bệnh đạo ôn', description: 'Bệnh hại phổ biến nhất trên lúa, do nấm Magnaporthe oryzae', symptoms: ['Vết bệnh hình thoi màu nâu xám', 'Tâm vết bệnh màu xám trắng', 'Cổ bông thối đen'], cause: 'Nấm Magnaporthe oryzae', favorableConditions: 'Thời tiết lạnh, ẩm ướt, bón nhiều đạm', treatment: 'Phun Tricyclazole 75WP hoặc Isoprothiolane', prevention: 'Bón phân cân đối, không bón thừa đạm', medicines: [], version: '1.0', createdAt: '2024-01-20T00:00:00Z', updatedAt: '2024-01-20T00:00:00Z' },
];

export type { DiseaseForm };

interface DiseaseStore {
    items: Disease[];
    loading: boolean;
    error: string | null;
    searchQuery: string;
    sortOrder: 'asc' | 'desc';
    fetchItems: () => Promise<void>;
    addItem: (data: DiseaseForm) => Promise<void>;
    updateItem: (id: number, data: Partial<DiseaseForm>) => Promise<void>;
    assignMedicines: (id: number, medicineIds: number[]) => Promise<void>;
    deleteItem: (id: number) => Promise<void>;
    setSearchQuery: (q: string) => void;
    setSortOrder: (o: 'asc' | 'desc') => void;
    getFiltered: () => Disease[];
}

export const useDiseaseStore = create<DiseaseStore>((set, get) => ({
    items: [],
    loading: false,
    error: null,
    searchQuery: '',
    sortOrder: 'asc',

    fetchItems: async () => {
        set({ loading: true, error: null });
        try {
            const data = await diseaseApi.listDiseases();
            set({ items: data, loading: false });
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Failed to load diseases';
            set({ items: [], error: message, loading: false });
        }
    },

    addItem: async (data) => {
        const created = await diseaseApi.createDisease(data);
        set(s => ({ items: [...s.items, created] }));
    },

    updateItem: async (id, data) => {
        const updated = await diseaseApi.updateDisease(id, data);
        set(s => ({ items: s.items.map(i => i.id === id ? updated : i) }));
    },

    assignMedicines: async (id, medicineIds) => {
        const updated = await diseaseApi.assignMedicines(id, medicineIds);
        set(s => ({ items: s.items.map(i => i.id === id ? updated : i) }));
    },

    deleteItem: async (id) => {
        await diseaseApi.deleteDisease(id);
        set(s => ({ items: s.items.filter(i => i.id !== id) }));
    },

    setSearchQuery: (q) => set({ searchQuery: q }),
    setSortOrder: (o) => set({ sortOrder: o }),

    getFiltered: () => {
        const { items, searchQuery, sortOrder } = get();
        const q = searchQuery.toLowerCase();
        return (items ?? [])
            .filter(d =>
                d.name.toLowerCase().includes(q) ||
                d.className.toLowerCase().includes(q)
            )
            .sort((a, b) => {
                const d = a.order - b.order;
                return sortOrder === 'asc' ? d : -d;
            });
    },
}));