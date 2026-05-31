interface Props {
    label?: string;
    value: string;
    onChange: (val: string) => void;
    placeholder?: string;
    type?: string;
    required?: boolean;
    error?: string;
}

export default function Input({ label, value, onChange, placeholder, type = 'text', required, error }: Props) {
    return (
        <div className="flex flex-col gap-1">
            {label && (
                <label className="text-sm font-medium text-[#0F172A]">
                    {label}{required && <span className="text-[#E11D48] ml-1">*</span>}
                </label>
            )}
            <input
                type={type}
                value={value}
                onChange={e => onChange(e.target.value)}
                placeholder={placeholder}
                className={`w-full px-3 py-2 text-sm border rounded-xl outline-none transition-colors bg-white text-[#0F172A] placeholder-[#94A3B8]
          ${error ? 'border-[#E11D48]' : 'border-[#E2E8F0] focus:border-[#059669]'}`}
            />
            {error && <span className="text-xs text-[#E11D48]">{error}</span>}
        </div>
    );
}