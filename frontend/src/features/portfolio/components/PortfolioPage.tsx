import { usePortfolio, useTrades } from '../hooks/usePortfolioQuery';
import { usePortfolioStream } from '../hooks/usePortfolioStream';
import { useAssets } from '@/features/market/hooks/useMarket';
import { usePriceStream } from '@/features/market/hooks/usePriceStream';
import { Card } from '@/components/ui/Card';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { ErrorState } from '@/components/shared/ErrorState';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { PercentageDisplay } from '@/components/shared/PercentageDisplay';
import { useState, useMemo } from 'react';

function HoldingsTable() {
  const { data: portfolio, isLoading, error, refetch } = usePortfolio();

  if (isLoading) {
    return (
      <Card>
        <div className="space-y-3">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <ErrorState message="Failed to load portfolio" onRetry={refetch} />
      </Card>
    );
  }

  if (!portfolio || portfolio.holdings.length === 0) {
    return (
      <Card>
        <EmptyState
          title="No holdings yet"
          description="Execute your first trade to start building your portfolio."
        />
      </Card>
    );
  }

  return (
    <Card padding="none">
      <div className="px-4 py-3 border-b border-neutral-800">
        <h2 className="text-sm font-semibold text-neutral-100">Holdings</h2>
      </div>
      <Table>
        <Thead>
          <Tr>
            <Th>Asset</Th>
            <Th className="text-right">Qty</Th>
            <Th className="text-right">Avg Entry</Th>
            <Th className="text-right">Current</Th>
            <Th className="text-right">Value</Th>
            <Th className="text-right">Unrealized P/L</Th>
          </Tr>
        </Thead>
        <Tbody>
          {portfolio.holdings.map((h) => (
            <Tr key={h.assetId}>
              <Td>
                <div>
                  <span className="font-medium text-neutral-100">{h.symbol}</span>
                  <span className="text-neutral-500 ml-2 text-xs">{h.name}</span>
                </div>
              </Td>
              <Td className="text-right tabular-nums font-mono">
                {h.quantity.toFixed(8)}
              </Td>
              <Td className="text-right tabular-nums font-mono">
                <PriceDisplay value={h.avgEntryPrice} />
              </Td>
              <Td className="text-right tabular-nums font-mono">
                <PriceDisplay value={h.currentPrice} />
              </Td>
              <Td className="text-right tabular-nums font-mono">
                <PriceDisplay value={h.quantity * h.currentPrice} />
              </Td>
              <Td className="text-right tabular-nums font-mono">
                <PercentageDisplay
                  value={
                    h.avgEntryPrice > 0
                      ? ((h.currentPrice - h.avgEntryPrice) / h.avgEntryPrice) * 100
                      : 0
                  }
                />
              </Td>
            </Tr>
          ))}
        </Tbody>
      </Table>
    </Card>
  );
}

function TradeHistory() {
  const [page, setPage] = useState(0);
  const { data, isLoading, error, refetch } = useTrades(page, 15);
  const { data: assets } = useAssets();

  if (isLoading) {
    return (
      <Card>
        <div className="space-y-3">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <ErrorState message="Failed to load trade history" onRetry={refetch} />
      </Card>
    );
  }

  const trades = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  if (trades.length === 0) {
    return (
      <Card>
        <EmptyState
          title="No trades yet"
          description="Your trade history will appear here after your first execution."
        />
      </Card>
    );
  }

  return (
    <Card padding="none">
      <div className="px-4 py-3 border-b border-neutral-800">
        <h2 className="text-sm font-semibold text-neutral-100">Trade History</h2>
      </div>
      <Table>
        <Thead>
          <Tr>
            <Th>Date</Th>
            <Th>Asset</Th>
            <Th>Side</Th>
            <Th className="text-right">Qty</Th>
            <Th className="text-right">Price</Th>
            <Th className="text-right">Fee</Th>
            <Th className="text-right">Total</Th>
          </Tr>
        </Thead>
        <Tbody>
          {trades.map((t) => (
            <Tr key={t.id}>
              <Td className="text-neutral-400 text-xs">
                {new Date(t.executedAt).toLocaleDateString()}
              </Td>
              <Td className="font-medium text-neutral-100">{assets?.find(a => a.id === t.assetId)?.symbol ?? t.assetId.slice(0, 8)}</Td>
              <Td>
                <Badge variant={t.side === 'BUY' ? 'gain' : 'loss'}>
                  {t.side}
                </Badge>
              </Td>
              <Td className="text-right tabular-nums font-mono">
                {t.quantity.toFixed(8)}
              </Td>
              <Td className="text-right tabular-nums font-mono">
                <PriceDisplay value={t.price} />
              </Td>
              <Td className="text-right tabular-nums font-mono text-neutral-400">
                <PriceDisplay value={t.fee} />
              </Td>
              <Td className="text-right tabular-nums font-mono">
                <PriceDisplay value={t.quantity * t.price + t.fee} />
              </Td>
            </Tr>
          ))}
        </Tbody>
      </Table>
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-4 py-3 border-t border-neutral-800">
          <span className="text-xs text-neutral-500">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="ghost"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

export function PortfolioPage() {
  const { data: portfolio } = usePortfolio();

  usePortfolioStream();
  const symbols = useMemo(
    () => portfolio?.holdings?.map((h) => h.symbol) ?? [],
    [portfolio],
  );
  usePriceStream(symbols);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-neutral-100">Portfolio</h1>
      </div>

      {portfolio && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <p className="text-xs text-neutral-400 mb-1">Cash Balance</p>
            <p className="text-lg font-semibold tabular-nums font-mono text-neutral-100">
              <PriceDisplay value={portfolio.cashBalance} />
            </p>
          </Card>
          <Card>
            <p className="text-xs text-neutral-400 mb-1">Invested Value</p>
            <p className="text-lg font-semibold tabular-nums font-mono text-neutral-100">
              <PriceDisplay
                value={
                  portfolio.holdings.reduce(
                    (sum, h) => sum + h.quantity * h.currentPrice,
                    0,
                  )
                }
              />
            </p>
          </Card>
          <Card>
            <p className="text-xs text-neutral-400 mb-1">Unrealized P/L</p>
            <p className="text-lg font-semibold tabular-nums font-mono">
              <PriceDisplay value={portfolio.totalUnrealizedPnl} />
            </p>
          </Card>
        </div>
      )}

      <HoldingsTable />
      <TradeHistory />
    </div>
  );
}
