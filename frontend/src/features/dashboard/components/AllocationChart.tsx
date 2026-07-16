import { Card } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';
import { useAnalyticsSummary } from '@/features/analytics/hooks/useAnalytics';

const COLORS = [
  '#3b82f6', '#22c55e', '#ef4444', '#f59e0b', '#a855f7',
  '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1',
];

export function AllocationChart() {
  const { data, isLoading, error } = useAnalyticsSummary();

  if (isLoading) {
    return (
      <Card padding="md">
        <Skeleton className="h-3 w-24 mb-3" />
        <Skeleton className="h-40 w-40 rounded-full mx-auto" />
      </Card>
    );
  }

  if (error || !data?.allocationBreakdown?.length) {
    return (
      <Card padding="md">
        <p className="text-xs text-neutral-500 uppercase tracking-wider">Allocation</p>
        <p className="text-sm text-neutral-400 mt-3">No holdings yet</p>
      </Card>
    );
  }

  const entries = data.allocationBreakdown;
  const total = entries.reduce((sum, e) => sum + e.pct, 0);
  let cumulative = 0;

  const segments = entries.map((entry, i) => {
    const start = cumulative;
    cumulative += (entry.pct / total) * 100;
    return {
      ...entry,
      color: COLORS[i % COLORS.length],
      startDeg: (start / 100) * 360,
      endDeg: (cumulative / 100) * 360,
    };
  });

  const gradientStops = segments
    .map((s) => `${s.color} ${s.startDeg}deg ${s.endDeg}deg`)
    .join(', ');

  return (
    <Card padding="md">
      <p className="text-xs text-neutral-500 uppercase tracking-wider mb-3">Allocation</p>
      <div className="flex items-center gap-6">
        <div
          className="w-32 h-32 rounded-full shrink-0"
          style={{
            background: `conic-gradient(${gradientStops})`,
          }}
        />
        <div className="space-y-1.5 min-w-0">
          {segments.map((s) => (
            <div key={s.symbol} className="flex items-center gap-2 text-xs">
              <span
                className="w-2 h-2 rounded-full shrink-0"
                style={{ backgroundColor: s.color }}
              />
              <span className="text-neutral-300 truncate">{s.symbol}</span>
              <span className="text-neutral-500 tabular-nums ml-auto">
                {s.pct.toFixed(1)}%
              </span>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
}
