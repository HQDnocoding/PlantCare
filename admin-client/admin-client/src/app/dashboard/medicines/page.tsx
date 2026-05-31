'use client';
import { useEffect, useState } from 'react';
import { useMedicineStore } from '@/store/medicineStore';
import { useDiseaseStore } from '@/store/diseaseStore';
import { Medicine } from '@/store/types';
import { MedicineForm } from '@/lib/api';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { Plus, Pencil, Trash2, Search, ChevronUp, ChevronDown } from 'lucide-react';

const EMPTY_FORM: MedicineForm = {
    name: '',
    activeIngredient: '',
    formulation: '',
    usage: '',
    dosage: '',
    weatherCondition: '',
    toxicity: '',
    safetyWarnings: [],
    preHarvestInterval: '',
    diseaseIds: [],
};

export default function MedicinesPage() {
    const { fetchItems, addItem, updateItem, assignDiseases, deleteItem, setSearchQuery, setSortOrder, searchQuery, sortOrder, loading } = useMedicineStore();
    const medicines = useMedicineStore(s => s.getFiltered)();
    const { items: diseases, fetchItems: fetchDiseases } = useDiseaseStore();

    const [modalOpen, setModalOpen] = useState(false);
    const [editItem, setEditItem] = useState<Medicine | null>(null);
    const [form, setForm] = useState<MedicineForm>(EMPTY_FORM);
    const [warningsText, setWarningsText] = useState('');
    const [deleteId, setDeleteId] = useState<number | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});

    useEffect(() => { fetchItems(); fetchDiseases(); }, []);

    // ── Validation rules ──────────────────────────────────────────────
    const validateForm = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!form.name.trim()) {
            newErrors.name = 'Tên thuốc không được để trống';
        } else if (form.name.length > 200) {
            newErrors.name = 'Tên thuốc tối đa 200 ký tự';
        }

        if (!form.activeIngredient.trim()) {
            newErrors.activeIngredient = 'Hoạt chất không được để trống';
        } else if (form.activeIngredient.length > 200) {
            newErrors.activeIngredient = 'Hoạt chất tối đa 200 ký tự';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const openAdd = () => {
        setEditItem(null);
        setForm(EMPTY_FORM);
        setWarningsText('');
        setErrors({});
        setModalOpen(true);
    };

    const openEdit = (m: Medicine) => {
        setEditItem(m);
        setForm({
            name: m.name,
            activeIngredient: m.activeIngredient,
            formulation: m.formulation,
            usage: m.usage,
            dosage: m.dosage,
            weatherCondition: m.weatherCondition,
            toxicity: m.toxicity,
            safetyWarnings: m.safetyWarnings,
            preHarvestInterval: m.preHarvestInterval,
            diseaseIds: m.diseases?.map(d => d.id) ?? [],
        });
        setWarningsText(m.safetyWarnings.join('\n'));
        setErrors({});
        setModalOpen(true);
    };

    const handleSave = async () => {
        if (!validateForm()) return;
        const finalForm = { ...form, safetyWarnings: warningsText.split('\n').map(s => s.trim()).filter(Boolean) };
        if (editItem) {
            await updateItem(editItem.id, finalForm);
            if (finalForm.diseaseIds) await assignDiseases(editItem.id, finalForm.diseaseIds);
        } else {
            await addItem(finalForm);
        }
        setModalOpen(false);
    };

    const toggleDisease = (id: number) => {
        setForm(f => ({
            ...f,
            diseaseIds: f.diseaseIds?.includes(id)
                ? f.diseaseIds.filter(x => x !== id)
                : [...(f.diseaseIds ?? []), id],
        }));
    };

    return (
        <div>
            <Header title="Quản lý thuốc" subtitle={`${medicines.length} loại thuốc`}
                action={<Button onClick={openAdd}><Plus size={16} />Thêm thuốc</Button>} />

            <div className="flex gap-3 mb-4">
                <div className="relative flex-1 max-w-sm">
                    <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#94A3B8]" />
                    <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                        placeholder="Tìm theo tên, hoạt chất..." className="w-full pl-9 pr-4 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] bg-white" />
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
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Tên thuốc</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Hoạt chất</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Dạng bào chế</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Độc tính</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Bệnh liên quan</th>
                                <th className="text-left px-5 py-3 text-xs font-semibold text-[#475569] uppercase">Ngày tạo</th>
                                <th className="px-5 py-3" />
                            </tr>
                        </thead>
                        <tbody>
                            {medicines.map((m, i) => (
                                <tr key={m.id} className={`border-b border-[#F1F5F9] hover:bg-[#F8FAFC] ${i === medicines.length - 1 ? 'border-0' : ''}`}>
                                    <td className="px-5 py-3 text-sm font-medium text-[#0F172A]">{m.name}</td>
                                    <td className="px-5 py-3 text-sm text-[#475569]">{m.activeIngredient}</td>
                                    <td className="px-5 py-3 text-sm text-[#475569]">{m.formulation}</td>
                                    <td className="px-5 py-3 text-sm text-[#475569]">{m.toxicity}</td>
                                    <td className="px-5 py-3">
                                        {m.diseases && m.diseases.length > 0
                                            ? <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#FEF3C7] text-[#F59E0B]">{m.diseases.length} bệnh</span>
                                            : <span className="text-[#94A3B8] text-sm">—</span>}
                                    </td>
                                    <td className="px-5 py-3 text-sm text-[#475569]">{new Date(m.createdAt).toLocaleDateString('vi-VN')}</td>
                                    <td className="px-5 py-3">
                                        <div className="flex items-center justify-end gap-1">
                                            <Button size="sm" variant="ghost" onClick={() => openEdit(m)}><Pencil size={14} /></Button>
                                            <Button size="sm" variant="ghost" onClick={() => setDeleteId(m.id)}><Trash2 size={14} /></Button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            <Modal open={modalOpen} onClose={() => setModalOpen(false)}
                title={editItem ? 'Chỉnh sửa thuốc' : 'Thêm thuốc'} size="lg"
                footer={<><Button variant="outline" onClick={() => setModalOpen(false)}>Hủy</Button><Button onClick={handleSave}>{editItem ? 'Lưu' : 'Thêm'}</Button></>}>
                <div className="flex flex-col gap-4">
                    <Input label="Tên thuốc" value={form.name} onChange={v => setForm(f => ({ ...f, name: v }))} required error={errors.name} />
                    <div className="grid grid-cols-2 gap-4">
                        <Input label="Hoạt chất" value={form.activeIngredient} onChange={v => setForm(f => ({ ...f, activeIngredient: v }))} required error={errors.activeIngredient} />
                        <Input label="Dạng bào chế" value={form.formulation} onChange={v => setForm(f => ({ ...f, formulation: v }))} placeholder="EC, WP, SC..." />
                        <Input label="Liều dùng" value={form.dosage} onChange={v => setForm(f => ({ ...f, dosage: v }))} />
                        <Input label="Thời gian cách ly (PHI)" value={form.preHarvestInterval} onChange={v => setForm(f => ({ ...f, preHarvestInterval: v }))} />
                        <Input label="Điều kiện thời tiết" value={form.weatherCondition} onChange={v => setForm(f => ({ ...f, weatherCondition: v }))} />
                        <Input label="Độc tính" value={form.toxicity} onChange={v => setForm(f => ({ ...f, toxicity: v }))} />
                    </div>
                    <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-medium text-[#0F172A]">Cách dùng</label>
                        <textarea value={form.usage} onChange={e => setForm(f => ({ ...f, usage: e.target.value }))} rows={2} className="w-full px-3 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] resize-none" />
                    </div>
                    <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-medium text-[#0F172A]">Cảnh báo an toàn <span className="text-[#94A3B8] font-normal">(mỗi dòng 1 cảnh báo)</span></label>
                        <textarea value={warningsText} onChange={e => setWarningsText(e.target.value)} rows={3} className="w-full px-3 py-2 text-sm border border-[#E2E8F0] rounded-xl outline-none focus:border-[#059669] resize-none" />
                    </div>
                    {diseases.length > 0 && (
                        <div className="flex flex-col gap-2">
                            <label className="text-sm font-medium text-[#0F172A]">Bệnh điều trị được <span className="text-[#94A3B8] font-normal">(click để chọn/bỏ chọn)</span></label>
                            <div className="flex flex-wrap gap-2 p-3 border border-[#E2E8F0] rounded-xl min-h-[48px]">
                                {diseases.map(d => {
                                    const selected = form.diseaseIds?.includes(d.id);
                                    return (
                                        <button key={d.id} type="button" onClick={() => toggleDisease(d.id)}
                                            className={`px-3 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer ${selected ? 'bg-[#059669] text-white' : 'bg-[#F1F5F9] text-[#475569] hover:bg-[#E2E8F0]'}`}>
                                            {d.name}
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