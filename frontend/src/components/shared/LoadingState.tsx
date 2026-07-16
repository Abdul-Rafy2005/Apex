import { Skeleton } from '@/components/ui/Skeleton';

interface LoadingStateProps {
  rows?: number;
  variant?: 'cards' | 'table';
}

export function LoadingState({ rows = 3, variant = 'cards' }: LoadingStateProps) {
  if (variant === 'table') {
    return (
      <div className="space-y-2">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="flex items-center gap-4">
            <Skeleton className="h-4 w-16" />
            <Skeleton className="h-4 w-24 ml-auto" />
            <Skeleton className="h-4 w-16" />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="rounded-lg bg-neutral-900 border border-neutral-800 p-4 space-y-3">
          <Skeleton className="h-3 w-20" />
          <Skeleton className="h-6 w-28" />
          <Skeleton className="h-3 w-16" />
        </div>
      ))}
    </div>
  );
}
