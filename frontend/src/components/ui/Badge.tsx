import type { HTMLAttributes } from 'react';

type Variant = 'default' | 'gain' | 'loss' | 'warning' | 'brand';

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: Variant;
}

const variantClasses: Record<Variant, string> = {
  default: 'bg-neutral-800 text-neutral-300 border-neutral-700',
  gain: 'bg-gain-dim text-gain border-gain/20',
  loss: 'bg-loss-dim text-loss border-loss/20',
  warning: 'bg-warning/10 text-warning border-warning/20',
  brand: 'bg-brand-500/10 text-brand-400 border-brand-500/20',
};

export function Badge({ variant = 'default', className = '', children, ...props }: BadgeProps) {
  return (
    <span
      className={[
        'inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-md border',
        variantClasses[variant],
        className,
      ].join(' ')}
      {...props}
    >
      {children}
    </span>
  );
}
