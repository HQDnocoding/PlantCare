'use client';
import { ReactNode, useEffect } from 'react';
import { X } from 'lucide-react';

interface Props {
    open: boolean;
    onClose: () => void;
    title: string;
    children: ReactNode;
    footer?: ReactNode;
    size?: 'md' | 'lg';
}

export default function Modal({ open, onClose, title, children, footer, size = 'md' }: Props) {
    useEffect(() => {
        if (open) document.body.style.overflow = 'hidden';
        else document.body.style.overflow = '';
        return () => { document.body.style.overflow = ''; };
    }, [open]);

    if (!open) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'rgba(0,0,0,0.4)' }}>
            <div
                className={`bg-white rounded-2xl shadow-xl w-full ${size === 'lg' ? 'max-w-2xl' : 'max-w-lg'} max-h-[90vh] flex flex-col`}
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-[#F1F5F9]">
                    <h3 className="text-base font-semibold text-[#0F172A]">{title}</h3>
                    <button onClick={onClose} className="p-1 rounded-lg hover:bg-[#F8FAFC] transition-colors cursor-pointer">
                        <X size={18} className="text-[#475569]" />
                    </button>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto px-6 py-4">
                    {children}
                </div>

                {/* Footer */}
                {footer && (
                    <div className="flex justify-end gap-3 px-6 py-4 border-t border-[#F1F5F9]">
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );
}