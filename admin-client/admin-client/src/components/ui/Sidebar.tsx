'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Leaf, Users, FileText, Pill, Bug, LogOut } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';

export default function Sidebar() {
    const pathname = usePathname();
    const { logout } = useAuthStore();

    const menuItems = [
        { href: '/dashboard', label: 'Tổng quan', icon: Leaf },
        { href: '/dashboard/users', label: 'Người dùng', icon: Users },
        { href: '/dashboard/posts', label: 'Bài đăng', icon: FileText },
        { href: '/dashboard/diseases', label: 'Loại bệnh', icon: Bug },
        { href: '/dashboard/medicines', label: 'Thuốc', icon: Pill },
    ];

    const isActive = (href: string) => pathname === href;

    return (
        <div className="w-64 bg-white border-r border-[#F1F5F9] flex flex-col h-screen">
            {/* Logo */}
            <div className="p-6 border-b border-[#F1F5F9]">
                <div className="flex items-center gap-2">
                    <div className="w-10 h-10 rounded-xl bg-[#059669] flex items-center justify-center">
                        <Leaf size={24} className="text-white" />
                    </div>
                    <div>
                        <h2 className="text-sm font-bold text-[#0F172A]">Plant App</h2>
                        <p className="text-xs text-[#475569]">Admin</p>
                    </div>
                </div>
            </div>

            {/* Menu */}
            <nav className="flex-1 p-4">
                {menuItems.map(({ href, label, icon: Icon }) => {
                    const active = isActive(href);
                    return (
                        <Link key={href} href={href}>
                            <div
                                className={`flex items-center gap-3 px-4 py-3 rounded-lg mb-2 cursor-pointer transition-colors ${active
                                        ? 'bg-[#DCFCE7] text-[#059669]'
                                        : 'text-[#475569] hover:bg-[#F1F5F9]'
                                    }`}
                            >
                                <Icon size={20} />
                                <span className="text-sm font-medium">{label}</span>
                            </div>
                        </Link>
                    );
                })}
            </nav>

            {/* Logout */}
            <div className="p-4 border-t border-[#F1F5F9]">
                <button
                    onClick={() => {
                        logout();
                        window.location.href = '/';
                    }}
                    className="w-full flex items-center gap-3 px-4 py-3 text-[#475569] hover:bg-[#FEE2E2] rounded-lg transition-colors"
                >
                    <LogOut size={20} />
                    <span className="text-sm font-medium">Đăng xuất</span>
                </button>
            </div>
        </div>
    );
}