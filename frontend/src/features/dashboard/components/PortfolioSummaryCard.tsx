import { Card } from '@/components/ui/Card';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { PercentageDisplay } from '@/components/shared/PercentageDisplay';
import { Skeleton } from '@/components/ui/Skeleton';
import { useAnalyticsSummary } from '@/features/analytics/hooks/useAnalytics';

export function PortfolioSummaryCard() {
  const { data, isLoading, error } = useAnalyticsSummary();

  if (isLoading) {
    return (
      <Card padding="md">
        <Skeleton className="h-3 w-20 mb-2" />
        <Skeleton className="h-7 w-32 mb-1" />
        <Skeleton className="h-3 w-16" />
      </Card>
    );
  }

  if (error) {
    return (
      <Card padding="md">
        <p className="text-xs text-neutral-500">Portfolio value</p>
        <p className="text-xl font-semibold text-neutral-400 tabular-nums mt-1">--</p>
      </Card>
    );
  }

  const value = data?.portfolioValue ?? 0;
  const dailyReturn = data?.dailyReturnPct ?? 0;

  return (
    <Card padding="md">
      <p className="text-xs text-neutral-500 uppercase tracking-wider">Portfolio Value</p>
      <div className="mt-1">
        <PriceDisplay value={value} className="text-xl font-semibold text-neutral-100" />
      </div>
      <div className="mt-1">
        <PercentageDisplay value={dailyReturn} className="text-xs" />
        <span className="text-xs text-neutral-500 ml-1">today</span>
      </div>
    </Card>
  );
}
