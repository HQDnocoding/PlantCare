import Sidebar from '@/components/ui/Sidebar';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
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