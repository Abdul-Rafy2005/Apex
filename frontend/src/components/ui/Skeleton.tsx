interface SkeletonProps {
  className?: string;
  variant?: 'text' | 'circular' | 'rectangular';
}

export function Skeleton({ className = '', variant = 'text' }: SkeletonProps) {
  const variantClasses: Record<string, string> = {
    text: 'h-4 rounded',
    circular: 'rounded-full',
    rectangular: 'rounded-md',
  };

  return (
    <div
      className={[
        'animate-pulse bg-neutral-800',
        variantClasses[variant],
        className,
      ].join(' ')}
      aria-hidden="true"
    />
  );
}
