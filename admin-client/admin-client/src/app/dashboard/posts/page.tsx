'use client';
import { useEffect, useState } from 'react';
import { usePostStore } from '@/store/postStore';
import { Post } from '@/store/types';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { Pencil, Trash2, Search, ChevronUp, ChevronDown, ThumbsUp, ThumbsDown, Images } from 'lucide-react';

export default function PostsPage() {
    const { fetchItems, updateItem, deleteItem, setSearchQuery, setSortOrder, searchQuery, sortOrder, loading } = usePostStore();
    const posts = usePostStore(s => s.getFiltered)();

    const [modalOpen, setModalOpen] = useState(false);
    const [editItem, setEditItem] = useState<Post | null>(null);
    const [content, setContent] = useState('');
    const [tagsText, setTagsText] = useState('');
    const [deleteId, setDeleteId] = useState<string | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});

    useEffect(() => { fetchItems(); }, []);

    // ── Validation rules ──────────────────────────────────────────────
    const validateForm = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!content.trim()) {
            newErrors.content = 'Nội dung không được để trống';
        } else if (content.length > 10000) {
            newErrors.content = 'Nội dung tối đa 10,000 ký tự';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const openEdit = (p: Post) => {
        setEditItem(p);
        setContent(p.content);
        setTagsText(p.tags.join(', '));
        setErrors({});
        setModalOpen(true);
    };

    const handleSave = async () => {
        if (!editItem || !validateForm()) return;
        const tags = tagsText.split(',').map(t => t.trim()).filter(Boolean);
        await updateItem(editItem.id, content.trim(), tags);
        setModalOpen(false);
    };

    return (
        <div>
            <Header title="Quản lý bài đăng" subtitle={`${posts.length} bài đăng`} />

            <div className="flex gap-3 mb-4">
                <div className="relative flex-1 max-w-sm">
                    <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#94A3B8]" />
                    <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                        placeholder="Tìm theo nội dung, tag..." className="w-full pl-9 pr-4 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] bg-white" />
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
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Nội dung</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Tags</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Ảnh</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Tương tác</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Ngày tạo</th>
                                <th className="px-5 py-3" />
                            </tr>
                        </thead>
                        <tbody>
                            {posts.map((p, i) => (
                                <tr key={p.id} className={`border-b border-[#F1F5F9] hover:bg-[#F8FAFC] ${i === posts.length - 1 ? 'border-0' : ''} ${p.isDeleted ? 'opacity-40' : ''}`}>
                                    <td className="px-5 py-3 text-sm text-[#0F172A] max-w-xs">
                                        <span className={`block truncate ${p.isDeleted ? 'line-through text-[#94A3B8]' : ''}`}>{p.content}</span>
                                    </td>
                                    <td className="px-5 py-3">
                                        <div className="flex gap-1 flex-wrap max-w-[180px]">
                                            {p.tags.slice(0, 3).map(t => (
                                                <span key={t} className="px-2 py-0.5 rounded-full text-xs bg-[#F1F5F9] text-[#475569]">{t}</span>
                                            ))}
                                            {p.tags.length > 3 && <span className="text-xs text-[#94A3B8]">+{p.tags.length - 3}</span>}
                                        </div>
                                    </td>
                                    <td className="px-5 py-3 text-sm text-[#94A3B8]">
                                        {p.imageUrls.length > 0 && (
                                            <span className="inline-flex items-center gap-1"><Images size={14} />{p.imageUrls.length}</span>
                                        )}
                                    </td>
                                    <td className="px-5 py-3">
                                        <div className="flex items-center gap-2 text-xs">
                                            <span className="flex items-center gap-0.5 text-[#059669]"><ThumbsUp size={12} />{p.upvoteCount}</span>
                                            <span className="flex items-center gap-0.5 text-[#E11D48]"><ThumbsDown size={12} />{p.downvoteCount}</span>
                                        </div>
                                    </td>
                                    <td className="px-5 py-3 text-sm text-[#475569]">{new Date(p.createdAt).toLocaleDateString('vi-VN')}</td>
                                    <td className="px-5 py-3">
                                        {!p.isDeleted && (
                                            <div className="flex items-center justify-end gap-1">
                                                <Button size="sm" variant="ghost" onClick={() => openEdit(p)}><Pencil size={14} /></Button>
                                                <Button size="sm" variant="ghost" onClick={() => setDeleteId(p.id)}><Trash2 size={14} /></Button>
                                            </div>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Chỉnh sửa bài đăng" size="lg"
                footer={<><Button variant="outline" onClick={() => setModalOpen(false)}>Hủy</Button><Button onClick={handleSave}>Lưu</Button></>}>
                {editItem && (
                    <div className="flex flex-col gap-4">
                        {editItem.imageUrls.length > 0 && (
                            <div className="flex flex-col gap-2">
                                <label className="text-sm font-medium text-[#0F172A]">
                                    Ảnh đính kèm <span className="text-[#94A3B8] font-normal">(chỉ xem)</span>
                                </label>
                                <div className="flex gap-2 flex-wrap">
                                    {editItem.imageUrls.map((url, idx) => (
                                        // eslint-disable-next-line @next/next/no-img-element
                                        <img key={idx} src={url} alt={`img-${idx}`}
                                            className="w-24 h-24 object-cover rounded-xl border border-[#E2E8F0]" />
                                    ))}
                                </div>
                            </div>
                        )}
                        <div className="flex flex-col gap-1.5">
                            <label className="text-sm font-medium text-[#0F172A]">Nội dung {errors.content && <span className="text-[#E11D48] text-xs font-normal">- {errors.content}</span>}</label>
                            <textarea value={content} onChange={e => setContent(e.target.value)} rows={5}
                                className={`w-full px-3 py-2 text-sm border rounded-xl outline-none focus:border-[#059669] resize-none transition-colors ${errors.content ? 'border-[#E11D48]' : 'border-[#E2E8F0]'}`} />
                            {content.length > 0 && (
                                <div className="text-xs text-[#94A3B8]">({content.length}/10,000 ký tự)</div>
                            )}
                        </div>
                        <div className="flex flex-col gap-1.5">
                            <label className="text-sm font-medium text-[#0F172A]">
                                Tags <span className="text-[#94A3B8] font-normal">(phân cách bằng dấu phẩy)</span>
                            </label>
                            <input value={tagsText} onChange={e => setTagsText(e.target.value)}
                                placeholder="tag1, tag2, tag3"
                                className="w-full px-3 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669]" />
                        </div>
                    </div>
                )}
            </Modal>

            <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)}
                onConfirm={() => { if (deleteId) { deleteItem(deleteId); setDeleteId(null); } }} />
        </div>
    );
}
