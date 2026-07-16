import { Card } from '@/components/ui/Card';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { PercentageDisplay } from '@/components/shared/PercentageDisplay';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import type { AssetResponse } from '@/features/market/types/asset';

interface AssetDetailProps {
  asset: AssetResponse | undefined;
  currentPrice: number;
  change24hPct: number;
  isLoading?: boolean;
}

export function AssetDetail({ asset, currentPrice, change24hPct, isLoading }: AssetDetailProps) {
  if (isLoading || !asset) {
    return (
      <Card padding="md">
        <div className="space-y-3">
          <Skeleton className="h-6 w-24" />
          <Skeleton className="h-8 w-36" />
          <Skeleton className="h-4 w-20" />
        </div>
      </Card>
    );
  }

  return (
    <Card padding="md">
      <div className="space-y-2">
        <div className="flex items-center gap-3">
          <h2 className="text-lg font-semibold text-neutral-100">{asset.symbol}</h2>
          <Badge variant={asset.tradable ? 'brand' : 'default'}>
            {asset.tradable ? 'Tradable' : 'Not tradable'}
          </Badge>
        </div>
        <p className="text-sm text-neutral-400">{asset.name}</p>
        <div className="pt-1">
          <PriceDisplay value={currentPrice} className="text-2xl font-semibold text-neutral-100" />
        </div>
        <div>
          <PercentageDisplay value={change24hPct} className="text-sm" />
          <span className="text-xs text-neutral-500 ml-1">24h</span>
        </div>
      </div>
    </Card>
  );
}
