'use client';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Sidebar from '@/components/ui/Sidebar';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
    const router = useRouter();

    useEffect(() => {
        // Check auth before showing dashboard
        try {
            const raw = localStorage.getItem('admin-auth');
            const authState = raw ? JSON.parse(raw)?.state : null;
            const hasToken = authState?.token && authState?.isAuthenticated;

            if (!hasToken) {
                router.replace('/login');
            }
        } catch (e) {
            router.replace('/login');
        }
    }, [router]);

    return (
        <div className="flex h-screen bg-[#F8FAFC]">
            <Sidebar />
            <div className="flex-1 overflow-auto">
                <main className="p-8">
                    {children}
                </main>
            </div>
        </div>
    );
}