'use client';
import { useEffect, useState } from 'react';
import { useDiseaseStore } from '@/store/diseaseStore';
import { useMedicineStore } from '@/store/medicineStore';
import { Disease } from '@/store/types';
import { DiseaseForm } from '@/lib/api';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { Plus, Pencil, Trash2, Search, ChevronUp, ChevronDown } from 'lucide-react';

const EMPTY_FORM: DiseaseForm = {
    order: 0,
    className: '',
    name: '',
    description: '',
    symptoms: [],
    cause: '',
    favorableConditions: '',
    treatment: '',
    prevention: '',
    version: '1.0',
    medicineIds: [],
};

export default function DiseasesPage() {
    const { fetchItems, addItem, updateItem, assignMedicines, deleteItem, setSearchQuery, setSortOrder, searchQuery, sortOrder, loading } = useDiseaseStore();
    const diseases = useDiseaseStore(s => s.getFiltered)();
    const { items: medicines, fetchItems: fetchMedicines } = useMedicineStore();

    const [modalOpen, setModalOpen] = useState(false);
    const [editItem, setEditItem] = useState<Disease | null>(null);
    const [form, setForm] = useState<DiseaseForm>(EMPTY_FORM);
    const [symptomsText, setSymptomsText] = useState('');
    const [deleteId, setDeleteId] = useState<number | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});

    useEffect(() => { fetchItems(); fetchMedicines(); }, []);

    // ── Validation rules ──────────────────────────────────────────────
    const validateForm = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!form.className.trim()) {
            newErrors.className = 'Class name không được để trống';
        } else if (form.className.length > 50) {
            newErrors.className = 'Class name tối đa 50 ký tự';
        }

        if (!form.name.trim()) {
            newErrors.name = 'Tên bệnh không được để trống';
        } else if (form.name.length > 200) {
            newErrors.name = 'Tên bệnh tối đa 200 ký tự';
        }

        if (!form.description.trim()) {
            newErrors.description = 'Mô tả không được để trống';
        } else if (form.description.length > 2000) {
            newErrors.description = 'Mô tả tối đa 2000 ký tự';
        }

        if (form.order < 0 || form.order > 999) {
            newErrors.order = 'Thứ tự phải từ 0-999';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const openAdd = () => {
        setEditItem(null);
        setForm(EMPTY_FORM);
        setSymptomsText('');
        setErrors({});
        setModalOpen(true);
    };

    const openEdit = (d: Disease) => {
        setEditItem(d);
        setForm({
            order: d.order,
            className: d.className,
            name: d.name,
            description: d.description,
            symptoms: d.symptoms,
            cause: d.cause,
            favorableConditions: d.favorableConditions,
            treatment: d.treatment,
            prevention: d.prevention,
            version: d.version,
            medicineIds: d.medicines?.map(m => m.id) ?? [],
        });
        setSymptomsText(d.symptoms.join('\n'));
        setErrors({});
        setModalOpen(true);
    };

    const handleSave = async () => {
        if (!validateForm()) return;
        const finalForm = { ...form, symptoms: symptomsText.split('\n').map(s => s.trim()).filter(Boolean) };
        if (editItem) {
            await updateItem(editItem.id, finalForm);
            if (finalForm.medicineIds) await assignMedicines(editItem.id, finalForm.medicineIds);
        } else {
            await addItem(finalForm);
        }
        setModalOpen(false);
    };

    const toggleMedicine = (id: number) => {
        setForm(f => ({
            ...f,
            medicineIds: f.medicineIds?.includes(id)
                ? f.medicineIds.filter(x => x !== id)
                : [...(f.medicineIds ?? []), id],
        }));
    };

    return (
        <div>
            <Header title="Quản lý loại bệnh" subtitle={`${diseases.length} loại bệnh`}
                action={<Button onClick={openAdd}><Plus size={16} />Thêm loại bệnh</Button>} />

            <div className="flex gap-3 mb-4">
                <div className="relative flex-1 max-w-sm">
                    <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#94A3B8]" />
                    <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                        placeholder="Tìm theo tên, class..." className="w-full pl-9 pr-4 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] bg-white" />
                </div>
                <Button variant="outline" onClick={() => setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')}>
                    {sortOrder === 'desc' ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
                    Thứ tự
                </Button>
            </div>

            <div className="bg-white rounded-2xl border border-[#F1F5F9] shadow-sm overflow-hidden">
                {loading ? (
                    <div className="py-16 text-center text-[#94A3B8] text-sm">Đang tải...</div>
                ) : (
                    <table className="w-full">
                        <thead>
                            <tr className="bg-[#F8FAFC] border-b border-[#F1F5F9]">
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">#</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Tên bệnh</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Class</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Thuốc</th>
                                {/* <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Phiên bản</th> */}
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Ngày tạo</th>
                                <th className="px-5 py-3" />
                            </tr>
                        </thead>
                        <tbody>
                            {diseases.map((d, i) => (
                                <tr key={d.id} className={`border-b border-[#F1F5F9] hover:bg-[#F8FAFC] ${i === diseases.length - 1 ? 'border-0' : ''}`}>
                                    <td className="px-5 py-3 text-sm text-[#94A3B8]">{d.order}</td>
                                    <td className="px-5 py-3 text-sm font-medium text-[#0F172A]">{d.name}</td>
                                    <td className="px-5 py-3 text-sm text-[#475569] font-mono">{d.className}</td>
                                    <td className="px-5 py-3">
                                        {d.medicines && d.medicines.length > 0
                                            ? <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#DBEAFE] text-[#3B82F6]">{d.medicines.length} thuốc</span>
                                            : <span className="text-[#94A3B8] text-sm">—</span>}
                                    </td>
                                    {/* <td className="px-5 py-3 text-sm text-[#475569]">{d.version}</td> */}
                                    <td className="px-5 py-3 text-sm text-[#475569]">{new Date(d.createdAt).toLocaleDateString('vi-VN')}</td>
                                    <td className="px-5 py-3">
                                        <div className="flex items-center justify-end gap-1">
                                            <Button size="sm" variant="ghost" onClick={() => openEdit(d)}><Pencil size={14} /></Button>
                                            <Button size="sm" variant="ghost" onClick={() => setDeleteId(d.id)}><Trash2 size={14} /></Button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            <Modal open={modalOpen} onClose={() => setModalOpen(false)}
                title={editItem ? 'Chỉnh sửa loại bệnh' : 'Thêm loại bệnh'} size="lg"
                footer={<><Button variant="outline" onClick={() => setModalOpen(false)}>Hủy</Button><Button onClick={handleSave}>{editItem ? 'Lưu' : 'Thêm'}</Button></>}>
                <div className="flex flex-col gap-4">
                    <div className="grid grid-cols-2 gap-4">
                        <Input label="Thứ tự (order)" type="number" value={String(form.order)} onChange={v => setForm(f => ({ ...f, order: Number(v) }))} error={errors.order} />
                        <Input label="Class name" value={form.className} onChange={v => setForm(f => ({ ...f, className: v }))} required placeholder="Vd: XiMu" error={errors.className} />
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                        <Input label="Tên bệnh" value={form.name} onChange={v => setForm(f => ({ ...f, name: v }))} required error={errors.name} />
                        <Input label="Phiên bản" value={form.version} onChange={v => setForm(f => ({ ...f, version: v }))} />
                    </div>
                    <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-medium text-[#0F172A]">Mô tả {errors.description && <span className="text-[#E11D48] text-xs font-normal">- {errors.description}</span>}</label>
                        <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={2} className={`w-full px-3 py-2 text-sm border rounded-xl outline-none focus:border-[#059669] resize-none transition-colors ${errors.description ? 'border-[#E11D48]' : 'border-[#E2E8F0]'}`} />
                    </div>
                    <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-medium text-[#0F172A]">Triệu chứng <span className="text-[#94A3B8] font-normal">(mỗi dòng 1 triệu chứng)</span></label>
                        <textarea value={symptomsText} onChange={e => setSymptomsText(e.target.value)} rows={3} className="w-full px-3 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] resize-none" />
                    </div>
                    <Input label="Nguyên nhân" value={form.cause} onChange={v => setForm(f => ({ ...f, cause: v }))} />
                    <Input label="Điều kiện thuận lợi" value={form.favorableConditions} onChange={v => setForm(f => ({ ...f, favorableConditions: v }))} />
                    <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-medium text-[#0F172A]">Điều trị</label>
                        <textarea value={form.treatment} onChange={e => setForm(f => ({ ...f, treatment: e.target.value }))} rows={2} className="w-full px-3 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] resize-none" />
                    </div>
                    <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-medium text-[#0F172A]">Phòng ngừa</label>
                        <textarea value={form.prevention} onChange={e => setForm(f => ({ ...f, prevention: e.target.value }))} rows={2} className="w-full px-3 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] resize-none" />
                    </div>
                    {medicines.length > 0 && (
                        <div className="flex flex-col gap-2">
                            <label className="text-sm font-medium text-[#0F172A]">Thuốc điều trị <span className="text-[#94A3B8] font-normal">(click để chọn/bỏ chọn)</span></label>
                            <div className="flex flex-wrap gap-2 p-3 border border-[#E2E8F0] rounded-xl min-h-[48px]">
                                {medicines.map(m => {
                                    const selected = form.medicineIds?.includes(m.id);
                                    return (
                                        <button key={m.id} type="button" onClick={() => toggleMedicine(m.id)}
                                            className={`px-3 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer ${selected ? 'bg-[#059669] text-white' : 'bg-[#F1F5F9] text-[#475569] hover:bg-[#E2E8F0]'}`}>
                                            {m.name}
                                        </button>
                                    );
                                })}
                            </div>
                        </div>
                    )}
                </div>
            </Modal>

            <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)}
                onConfirm={() => { if (deleteId !== null) { deleteItem(deleteId); setDeleteId(null); } }} />
        </div>
    );
}