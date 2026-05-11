'use client';
import { useEffect, useState } from 'react';
import { useUserStore } from '@/store/userStore';
import { User, UserRole, UserStatus } from '@/store/types';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Modal from '@/components/ui/Modal';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { Pencil, Search, ChevronUp, ChevronDown, ShieldCheck, Ban } from 'lucide-react';

const roleBadge = (r: UserRole) => {
    if (r === 'ADMIN') return <Badge label="Admin" variant="error" />;

    return <Badge label="Nông dân" variant="success" />;
};

const statusBadge = (s: UserStatus) => {
    if (s === 'ACTIVE') return <Badge label="Hoạt động" variant="success" />;
    if (s === 'BLOCKED') return <Badge label="Đã khóa" variant="error" />;
    return <Badge label="Chưa xác thực" variant="warning" />;
};

export default function UsersPage() {
    const { fetchItems, updateStatus, updateRole, setSearchQuery, setSortOrder, searchQuery, sortOrder, loading } = useUserStore();
    const users = useUserStore(s => s.getFiltered)();

    const [modalOpen, setModalOpen] = useState(false);
    const [editItem, setEditItem] = useState<User | null>(null);
    const [selectedRole, setSelectedRole] = useState<UserRole>('FARMER');
    const [selectedStatus, setSelectedStatus] = useState<UserStatus>('ACTIVE');
    const [blockId, setBlockId] = useState<string | null>(null);

    useEffect(() => { fetchItems(); }, []);

    const openEdit = (u: User) => {
        setEditItem(u);
        setSelectedRole(u.role);
        setSelectedStatus(u.status);
        setModalOpen(true);
    };

    const handleSave = async () => {
        if (!editItem) return;
        if (selectedRole !== editItem.role) await updateRole(editItem.id, selectedRole);
        if (selectedStatus !== editItem.status) await updateStatus(editItem.id, selectedStatus);
        setModalOpen(false);
    };

    return (
        <div>
            <Header title="Quản lý người dùng" subtitle={`${users.length} người dùng`} />

            <div className="flex gap-3 mb-4">
                <div className="relative flex-1 max-w-sm">
                    <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#94A3B8]" />
                    <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                        placeholder="Tìm theo tên, email..." className="w-full pl-9 pr-4 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] bg-white" />
                </div>
                <Button variant="outline" onClick={() => setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')}>
                    {sortOrder === 'desc' ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
                    Ngày tạo
                </Button>
            </div>

            <div className="bg-white rounded-2xl border border-[#F1F5F9] shadow-sm overflow-hidden">
                {loading ? (
                    <div className="py-16 text-center text-[#94A3B8] text-sm">Đang tải...</div>
                ) : (
                    <table className="w-full">
                        <thead>
                            <tr className="bg-[#F8FAFC] border-b border-[#F1F5F9]">
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Họ tên</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Email</th>
                                {/* <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">SĐT</th> */}
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Vai trò</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Trạng thái</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Ngày tạo</th>
                                <th className="px-5 py-3" />
                            </tr>
                        </thead>
                        <tbody>
                            {users.map((u, i) => (
                                <tr key={u.id} className={`border-b border-[#F1F5F9] hover:bg-[#F8FAFC] ${i === users.length - 1 ? 'border-0' : ''}`}>
                                    <td className="px-5 py-3 text-sm font-medium text-[#0F172A]">{u.fullName}</td>
                                    <td className="px-5 py-3 text-sm text-[#475569]">{u.email}</td>
                                    {/* <td className="px-5 py-3 text-sm text-[#475569]">{u.phone ?? '—'}</td> */}
                                    <td className="px-5 py-3">{roleBadge(u.role)}</td>
                                    <td className="px-5 py-3">{statusBadge(u.status)}</td>
                                    <td className="px-5 py-3 text-sm text-[#475569]">{new Date(u.createdAt).toLocaleDateString('vi-VN')}</td>
                                    <td className="px-5 py-3">
                                        <div className="flex items-center justify-end gap-1">
                                            <Button size="sm" variant="ghost" onClick={() => openEdit(u)}><Pencil size={14} /></Button>
                                            {u.status !== 'BLOCKED'
                                                ? <Button size="sm" variant="ghost" onClick={() => setBlockId(u.id)}><Ban size={14} /></Button>
                                                : <Button size="sm" variant="ghost" onClick={() => updateStatus(u.id, 'ACTIVE')}><ShieldCheck size={14} /></Button>
                                            }
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Chỉnh sửa người dùng"
                footer={<><Button variant="outline" onClick={() => setModalOpen(false)}>Hủy</Button><Button onClick={handleSave}>Lưu</Button></>}>
                {editItem && (
                    <div className="flex flex-col gap-4">
                        <div className="flex items-center gap-3 p-3 bg-[#F8FAFC] rounded-xl">
                            <div className="w-10 h-10 rounded-full bg-[#D1FAE5] flex items-center justify-center text-[#059669] font-semibold text-sm">
                                {editItem.fullName.charAt(0).toUpperCase()}
                            </div>
                            <div>
                                <div className="text-sm font-semibold text-[#0F172A]">{editItem.fullName}</div>
                                <div className="text-xs text-[#94A3B8]">{editItem.email}</div>
                            </div>
                        </div>
                        <div className="flex flex-col gap-1.5">
                            <label className="text-sm font-medium text-[#0F172A]">Vai trò</label>
                            <select value={selectedRole} onChange={e => setSelectedRole(e.target.value as UserRole)}
                                className="w-full px-3 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669]">
                                <option value="FARMER">Nông dân</option>
                                <option value="ADMIN">Admin</option>
                            </select>
                        </div>
                        <div className="flex flex-col gap-1.5">
                            <label className="text-sm font-medium text-[#0F172A]">Trạng thái</label>
                            <select value={selectedStatus} onChange={e => setSelectedStatus(e.target.value as UserStatus)}
                                className="w-full px-3 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669]">
                                <option value="ACTIVE">Hoạt động</option>
                                <option value="UNVERIFIED">Chưa xác thực</option>
                                <option value="BLOCKED">Đã khóa</option>
                            </select>
                        </div>
                    </div>
                )}
            </Modal>

            <ConfirmDialog open={!!blockId} onClose={() => setBlockId(null)}
                onConfirm={() => { if (blockId) { updateStatus(blockId, 'BLOCKED'); setBlockId(null); } }} />
        </div>
    );
}
