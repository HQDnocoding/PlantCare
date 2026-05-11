import { ReactNode } from 'react';

interface Props {
    children: ReactNode;
    onClick?: () => void;
    variant?: 'primary' | 'outline' | 'danger' | 'ghost';
    size?: 'sm' | 'md';
    disabled?: boolean;
    type?: 'button' | 'submit';
    className?: string;
}

export default function Button({ children, onClick, variant = 'primary', size = 'md', disabled, type = 'button', className = '' }: Props) {
    const base = 'inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed';
    const sizes = { sm: 'px-3 py-1.5 text-sm', md: 'px-4 py-2 text-sm' };
    const variants = {
        primary: 'bg-[#059669] text-white hover:bg-[#047857]',
        outline: 'border border-[#E2E8F0] text-[#0F172A] hover:bg-[#F8FAFC]',
        danger: 'bg-[#E11D48] text-white hover:bg-[#BE123C]',
        ghost: 'text-[#475569] hover:bg-[#F8FAFC]',
    };

    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled}
            className={`${base} ${sizes[size]} ${variants[variant]} ${className}`}
        >
            {children}
        </button>
    );
}