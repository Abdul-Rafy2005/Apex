import { Card } from '@/components/ui/Card';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { PercentageDisplay } from '@/components/shared/PercentageDisplay';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { useMarketOverview } from '../hooks/useMarket';
import { usePriceStream } from '../hooks/usePriceStream';
import { useMemo } from 'react';

interface MarketOverviewProps {
  onAssetSelect?: (symbol: string) => void;
}

export function MarketOverview({ onAssetSelect }: MarketOverviewProps) {
  const { data, isLoading } = useMarketOverview();

  const allSymbols = useMemo(() => {
    if (!data) return [];
    return [
      ...data.topGainers.map((p) => p.symbol),
      ...data.topLosers.map((p) => p.symbol),
    ];
  }, [data]);

  usePriceStream(allSymbols);

  if (isLoading) {
    return (
      <Card padding="md">
        <Skeleton className="h-3 w-24 mb-3" />
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="flex items-center justify-between">
              <Skeleton className="h-4 w-12" />
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      </Card>
    );
  }

  if (!data) {
    return (
      <Card padding="md">
        <p className="text-xs text-neutral-500 uppercase tracking-wider mb-2">Market Overview</p>
        <EmptyState title="No market data" description="Market overview unavailable" />
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card padding="md">
        <p className="text-xs text-neutral-500 uppercase tracking-wider mb-2">Top Gainers</p>
        {data.topGainers.length === 0 ? (
          <p className="text-xs text-neutral-500">No data</p>
        ) : (
          <div className="space-y-1">
            {data.topGainers.slice(0, 5).map((g) => (
              <div
                key={g.symbol}
                className="flex items-center justify-between py-1 cursor-pointer hover:bg-neutral-800/30 px-1 rounded"
                onClick={() => onAssetSelect?.(g.symbol)}
              >
                <span className="text-sm text-neutral-200">{g.symbol}</span>
                <div className="text-right">
                  <PriceDisplay value={g.priceUsd} className="text-sm text-neutral-100" />
                  <span className="ml-2">
                    <PercentageDisplay value={g.change24hPct} className="text-xs" />
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card padding="md">
        <p className="text-xs text-neutral-500 uppercase tracking-wider mb-2">Top Losers</p>
        {data.topLosers.length === 0 ? (
          <p className="text-xs text-neutral-500">No data</p>
        ) : (
          <div className="space-y-1">
            {data.topLosers.slice(0, 5).map((l) => (
              <div
                key={l.symbol}
                className="flex items-center justify-between py-1 cursor-pointer hover:bg-neutral-800/30 px-1 rounded"
                onClick={() => onAssetSelect?.(l.symbol)}
              >
                <span className="text-sm text-neutral-200">{l.symbol}</span>
                <div className="text-right">
                  <PriceDisplay value={l.priceUsd} className="text-sm text-neutral-100" />
                  <span className="ml-2">
                    <PercentageDisplay value={l.change24hPct} className="text-xs" />
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
