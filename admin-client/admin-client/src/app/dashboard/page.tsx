'use client';
import { useUserStore } from '@/store/userStore';
import { usePostStore } from '@/store/postStore';
import { useMedicineStore } from '@/store/medicineStore';
import { useDiseaseStore } from '@/store/diseaseStore';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Users, FileText, Pill, Bug } from 'lucide-react';
import Header from '@/components/layout/Header';

export default function DashboardPage() {
    const router = useRouter();
    const { items: users = [], fetchItems: fetchUsers } = useUserStore();
    const { items: posts = [], fetchItems: fetchPosts } = usePostStore();
    const { items: medicines = [], fetchItems: fetchMedicines } = useMedicineStore();
    const { items: diseases = [], fetchItems: fetchDiseases } = useDiseaseStore();

    useEffect(() => {
        fetchUsers(); fetchPosts(); fetchMedicines(); fetchDiseases();
    }, []);

    const stats = [
        { label: 'Người dùng', value: users.length, icon: Users, color: '#059669', bg: '#D1FAE5', href: '/dashboard/users' },
        { label: 'Bài đăng', value: posts.length, icon: FileText, color: '#14B8A6', bg: '#CCFBF1', href: '/dashboard/posts' },
        { label: 'Thuốc', value: medicines.length, icon: Pill, color: '#3B82F6', bg: '#DBEAFE', href: '/dashboard/medicines' },
        { label: 'Loại bệnh', value: diseases.length, icon: Bug, color: '#F59E0B', bg: '#FEF3C7', href: '/dashboard/diseases' },
    ];

    return (
        <div>
            <Header title="Tổng quan" subtitle="Thống kê hệ thống Plant App" />

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {stats.map(({ label, value, icon: Icon, color, bg, href }) => (
                    <div
                        key={label}
                        onClick={() => router.push(href)}
                        className="bg-white rounded-2xl p-5 border border-[#F1F5F9] shadow-sm hover:shadow-md hover:border-[#E2E8F0] cursor-pointer transition-all duration-200 hover:scale-105"
                    >
                        <div className="flex items-center justify-between mb-3">
                            <span className="text-sm text-[#475569] font-medium">{label}</span>
                            <div className="w-9 h-9 rounded-xl flex items-center justify-center" style={{ background: bg }}>
                                <Icon size={18} style={{ color }} />
                            </div>
                        </div>
                        <div className="text-3xl font-bold text-[#0F172A]">{value}</div>
                    </div>
                ))}
            </div>
        </div>
    );
}