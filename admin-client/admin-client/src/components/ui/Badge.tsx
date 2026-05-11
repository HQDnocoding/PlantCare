interface Props {
    label: string;
    variant: 'success' | 'warning' | 'error' | 'info' | 'default';
}

const styles = {
    success: 'bg-[#D1FAE5] text-[#059669]',
    warning: 'bg-[#FEF3C7] text-[#F59E0B]',
    error: 'bg-[#FFF1F2] text-[#E11D48]',
    info: 'bg-[#DBEAFE] text-[#3B82F6]',
    default: 'bg-[#F1F5F9] text-[#475569]',
};

export default function Badge({ label, variant }: Props) {
    return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${styles[variant]}`}>
            {label}
        </span>
    );
}