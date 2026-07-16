import { Card } from '@/components/ui/Card';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { PercentageDisplay } from '@/components/shared/PercentageDisplay';
import { Skeleton } from '@/components/ui/Skeleton';
import { useAnalyticsSummary } from '@/features/analytics/hooks/useAnalytics';

export function DailyPnlCard() {
  const { data, isLoading, error } = useAnalyticsSummary();

  if (isLoading) {
    return (
      <Card padding="md">
        <Skeleton className="h-3 w-20 mb-2" />
        <Skeleton className="h-7 w-28 mb-1" />
        <Skeleton className="h-3 w-24" />
      </Card>
    );
  }

  if (error) {
    return (
      <Card padding="md">
        <p className="text-xs text-neutral-500">Daily P/L</p>
        <p className="text-xl font-semibold text-neutral-400 tabular-nums mt-1">--</p>
      </Card>
    );
  }

  const dailyPnl = data?.dailyPnlSeries?.[data.dailyPnlSeries.length - 1]?.pnl ?? 0;
  const totalReturn = data?.totalReturnPct ?? 0;

  return (
    <Card padding="md">
      <p className="text-xs text-neutral-500 uppercase tracking-wider">Daily P/L</p>
      <div className="mt-1">
        <PriceDisplay value={dailyPnl} className="text-xl font-semibold text-neutral-100" />
      </div>
      <div className="mt-1">
        <span className="text-xs text-neutral-500">Total return </span>
        <PercentageDisplay value={totalReturn} className="text-xs" />
      </div>
    </Card>
  );
}
