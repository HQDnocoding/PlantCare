'use client';
import { AlertTriangle } from 'lucide-react';
import Modal from './Modal';
import Button from './Button';

interface Props {
    open: boolean;
    onClose: () => void;
    onConfirm: () => void;
    title?: string;
    message?: string;
}

export default function ConfirmDialog({ open, onClose, onConfirm, title = 'Xác nhận xóa', message = 'Bạn có chắc chắn muốn xóa không? Hành động này không thể hoàn tác.' }: Props) {
    return (
        <Modal
            open={open}
            onClose={onClose}
            title={title}
            footer={
                <>
                    <Button variant="outline" onClick={onClose}>Hủy</Button>
                    <Button variant="danger" onClick={() => { onConfirm(); onClose(); }}>Xóa</Button>
                </>
            }
        >
            <div className="flex gap-3 items-start">
                <div className="flex-shrink-0 w-10 h-10 rounded-full bg-[#FFF1F2] flex items-center justify-center">
                    <AlertTriangle size={20} className="text-[#E11D48]" />
                </div>
                <p className="text-sm text-[#475569] pt-2">{message}</p>
            </div>
        </Modal>
    );
}