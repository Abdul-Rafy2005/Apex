import { Card } from '@/components/ui/Card';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { useTrades } from '@/features/portfolio/hooks/usePortfolioQuery';

function formatTime(iso: string) {
  const d = new Date(iso);
  return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

export function RecentTradesTable() {
  const { data, isLoading } = useTrades(0, 5);

  if (isLoading) {
    return (
      <Card padding="none">
        <div className="p-4 border-b border-neutral-800">
          <Skeleton className="h-3 w-24" />
        </div>
        <div className="p-4 space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex items-center gap-4">
              <Skeleton className="h-4 w-10" />
              <Skeleton className="h-4 w-16" />
              <Skeleton className="h-4 w-12 ml-auto" />
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      </Card>
    );
  }

  const trades = data?.content ?? [];

  if (!trades.length) {
    return (
      <Card padding="md">
        <p className="text-xs text-neutral-500 uppercase tracking-wider mb-2">Recent Trades</p>
        <EmptyState title="No trades yet" description="Your trade history will appear here" />
      </Card>
    );
  }

  return (
    <Card padding="none">
      <div className="px-4 py-3 border-b border-neutral-800">
        <p className="text-xs text-neutral-500 uppercase tracking-wider">Recent Trades</p>
      </div>
      <Table>
        <Thead>
          <Tr>
            <Th>Side</Th>
            <Th>Asset</Th>
            <Th className="text-right">Qty</Th>
            <Th className="text-right">Price</Th>
            <Th className="text-right">Time</Th>
          </Tr>
        </Thead>
        <Tbody>
          {trades.map((t) => (
            <Tr key={t.id}>
              <Td>
                <Badge variant={t.side === 'BUY' ? 'gain' : 'loss'}>
                  {t.side}
                </Badge>
              </Td>
              <Td className="font-medium">{t.assetId.slice(0, 6)}</Td>
              <Td className="text-right tabular-nums">{Number(t.quantity).toFixed(4)}</Td>
              <Td className="text-right tabular-nums">${Number(t.price).toFixed(2)}</Td>
              <Td className="text-right text-neutral-500">{formatTime(t.executedAt)}</Td>
            </Tr>
          ))}
        </Tbody>
      </Table>
    </Card>
  );
}
