import { forwardRef, type InputHTMLAttributes } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, className = '', id, ...props }, ref) => {
    const inputId = id ?? label?.toLowerCase().replace(/\s+/g, '-');

    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label
            htmlFor={inputId}
            className="text-sm font-medium text-neutral-300"
          >
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={[
            'h-9 px-3 text-sm rounded-md bg-neutral-900 border border-neutral-700',
            'text-neutral-100 placeholder:text-neutral-500',
            'focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent',
            'disabled:opacity-50 disabled:cursor-not-allowed',
            'transition-colors',
            error ? 'border-loss focus:ring-loss' : '',
            className,
          ].join(' ')}
          {...props}
        />
        {error && (
          <p className="text-xs text-loss">{error}</p>
        )}
      </div>
    );
  },
);
Input.displayName = 'Input';
