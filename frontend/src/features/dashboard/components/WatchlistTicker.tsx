import { useMemo } from 'react';
import { Card } from '@/components/ui/Card';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { usePortfolio } from '@/features/portfolio/hooks/usePortfolioQuery';
import { usePriceStream } from '@/features/market/hooks/usePriceStream';

export function WatchlistTicker() {
  const { data: portfolio, isLoading } = usePortfolio();
  const symbols = useMemo(
    () => portfolio?.holdings?.map((h) => h.symbol) ?? [],
    [portfolio],
  );

  usePriceStream(symbols);

  if (isLoading) {
    return (
      <Card padding="md">
        <Skeleton className="h-3 w-20 mb-3" />
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex items-center justify-between">
              <Skeleton className="h-4 w-12" />
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      </Card>
    );
  }

  if (!symbols.length) {
    return (
      <Card padding="md">
        <p className="text-xs text-neutral-500 uppercase tracking-wider mb-2">Watchlist</p>
        <EmptyState title="No holdings yet" description="Execute a trade to see live prices" />
      </Card>
    );
  }

  return (
    <Card padding="md">
      <p className="text-xs text-neutral-500 uppercase tracking-wider mb-2">Watchlist</p>
      <div className="space-y-1">
        {portfolio?.holdings?.map((h) => (
          <div key={h.assetId} className="flex items-center justify-between py-1">
            <span className="text-sm font-medium text-neutral-200">{h.symbol}</span>
            <div className="text-right">
              <PriceDisplay value={h.currentPrice} className="text-sm text-neutral-100" />
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}
