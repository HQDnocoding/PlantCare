import type {
    User, UserRole, UserStatus,
    Post,
    Disease,
    Medicine,
} from '@/store/types';

export type { User, Post, Disease, Medicine };

// ─── Shared ──────────────────────────────────────────────────────────────────

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
}

export interface DiseaseForm {
    order: number;
    className: string;
    name: string;
    description: string;
    symptoms: string[];
    cause: string;
    favorableConditions: string;
    treatment: string;
    prevention: string;
    version: string;
    medicineIds?: number[];
}

export interface MedicineForm {
    name: string;
    activeIngredient: string;
    formulation: string;
    usage: string;
    dosage: string;
    weatherCondition: string;
    toxicity: string;
    safetyWarnings: string[];
    preHarvestInterval: string;
    diseaseIds?: number[];
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

const BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

function getToken(): string {
    if (typeof window === 'undefined') return '';
    try {
        const raw = localStorage.getItem('admin-auth');
        return raw ? (JSON.parse(raw)?.state?.token ?? '') : '';
    } catch {
        return '';
    }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const token = getToken();
    const res = await fetch(`${BASE}${path}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...options.headers,
        },
    });
    if (!res.ok) {
        const msg = await res.text().catch(() => res.statusText);
        throw new Error(msg || `HTTP ${res.status}`);
    }
    // 204 No Content
    if (res.status === 204) return undefined as T;

    const json = await res.json() as any;

    // Unwrap ApiResponse wrapper if present
    if (json.success !== undefined && json.data !== undefined) {
        return json.data as T;
    }
    return json as T;
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

export const authApi = {
    adminLogin: (email: string, password: string) =>
        request<{ token: string; user: { id: string; email: string; fullName: string } }>(
            '/api/v1/admin/auth/login',
            { method: 'POST', body: JSON.stringify({ email, password }) }
        ),
};

// ─── Users ───────────────────────────────────────────────────────────────────

export const userApi = {
    listUsers: (page = 0, size = 20) =>
        request<PageResponse<User>>(`/api/v1/admin/users?page=${page}&size=${size}`),

    updateStatus: (id: string, status: UserStatus) =>
        request<User>(`/api/v1/admin/users/${id}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status }),
        }),

    updateRole: (id: string, role: UserRole) =>
        request<User>(`/api/v1/admin/users/${id}/role`, {
            method: 'PATCH',
            body: JSON.stringify({ role }),
        }),
};

// ─── Posts ───────────────────────────────────────────────────────────────────

export const postApi = {
    listPosts: (page = 0, size = 20) =>
        request<PageResponse<Post>>(`/api/v1/admin/posts?page=${page}&size=${size}`),

    updatePost: (id: string, content: string, tags: string[]) =>
        request<Post>(`/api/v1/admin/posts/${id}`, {
            method: 'PATCH',
            body: JSON.stringify({ content, tags }),
        }),

    deletePost: (id: string) =>
        request<void>(`/api/v1/admin/posts/${id}`, { method: 'DELETE' }),
};

// ─── Diseases ────────────────────────────────────────────────────────────────

export const diseaseApi = {
    listDiseases: () =>
        request<Disease[]>('/api/v1/admin/diseases'),

    createDisease: (data: DiseaseForm) =>
        request<Disease>('/api/v1/admin/diseases', {
            method: 'POST',
            body: JSON.stringify(data),
        }),

    updateDisease: (id: number, data: Partial<DiseaseForm>) =>
        request<Disease>(`/api/v1/admin/diseases/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data),
        }),

    deleteDisease: (id: number) =>
        request<void>(`/api/v1/admin/diseases/${id}`, { method: 'DELETE' }),

    assignMedicines: (id: number, medicineIds: number[]) =>
        request<Disease>(`/api/v1/admin/diseases/${id}/medicines`, {
            method: 'PUT',
            body: JSON.stringify({ medicineIds }),
        }),
};

// ─── Medicines ───────────────────────────────────────────────────────────────

export const medicineApi = {
    listMedicines: () =>
        request<Medicine[]>('/api/v1/admin/medicines'),

    createMedicine: (data: MedicineForm) =>
        request<Medicine>('/api/v1/admin/medicines', {
            method: 'POST',
            body: JSON.stringify(data),
        }),

    updateMedicine: (id: number, data: Partial<MedicineForm>) =>
        request<Medicine>(`/api/v1/admin/medicines/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data),
        }),

    deleteMedicine: (id: number) =>
        request<void>(`/api/v1/admin/medicines/${id}`, { method: 'DELETE' }),

    assignDiseases: (id: number, diseaseIds: number[]) =>
        request<Medicine>(`/api/v1/admin/medicines/${id}/diseases`, {
            method: 'PUT',
            body: JSON.stringify({ diseaseIds }),
        }),
};
