interface Props {
    title: string;
    subtitle?: string;
    action?: React.ReactNode;
}

export default function Header({ title, subtitle, action }: Props) {
    return (
        <div className="flex items-center justify-between mb-6">
            <div>
                <h1 className="text-xl font-bold text-[#0F172A]">{title}</h1>
                {subtitle && <p className="text-sm text-[#475569] mt-0.5">{subtitle}</p>}
            </div>
            {action && <div>{action}</div>}
        </div>
    );
}