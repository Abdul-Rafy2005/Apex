import { useMemo, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { PercentageDisplay } from '@/components/shared/PercentageDisplay';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { useAssets, useAssetPrices } from '../hooks/useMarket';
import { usePriceStream } from '../hooks/usePriceStream';

type SortKey = 'symbol' | 'priceUsd' | 'change24hPct';
type SortDir = 'asc' | 'desc';

interface PriceTableProps {
  onAssetSelect?: (symbol: string) => void;
  selectedSymbol?: string;
}

export function PriceTable({ onAssetSelect, selectedSymbol }: PriceTableProps) {
  const [sortKey, setSortKey] = useState<SortKey>('symbol');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [search, setSearch] = useState('');

  const { data: assets, isLoading: assetsLoading } = useAssets();
  const symbols = useMemo(() => assets?.map((a) => a.symbol) ?? [], [assets]);
  const { data: prices, isLoading: pricesLoading } = useAssetPrices(symbols);

  usePriceStream(symbols);

  const isLoading = assetsLoading || pricesLoading;

  const priceMap = useMemo(() => {
    if (!prices) return {};
    return Object.fromEntries(prices.map((p) => [p.symbol, p]));
  }, [prices]);

  const rows = useMemo(() => {
    if (!assets) return [];
    const filtered = assets.filter((a) => {
      if (!search) return true;
      const q = search.toLowerCase();
      return a.symbol.toLowerCase().includes(q) || a.name.toLowerCase().includes(q);
    });

    return filtered
      .map((a) => ({
        ...a,
        priceUsd: priceMap[a.symbol]?.priceUsd ?? 0,
        change24hPct: priceMap[a.symbol]?.change24hPct ?? 0,
      }))
      .sort((a, b) => {
        const aVal = a[sortKey];
        const bVal = b[sortKey];
        const cmp = typeof aVal === 'string' ? aVal.localeCompare(bVal as string) : (aVal as number) - (bVal as number);
        return sortDir === 'asc' ? cmp : -cmp;
      });
  }, [assets, priceMap, search, sortKey, sortDir]);

  function handleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  }

  function sortIndicator(key: SortKey) {
    if (sortKey !== key) return null;
    return <span className="ml-1 text-neutral-500">{sortDir === 'asc' ? '↑' : '↓'}</span>;
  }

  if (isLoading) {
    return (
      <Card padding="none">
        <div className="p-4 border-b border-neutral-800">
          <Skeleton className="h-8 w-64" />
        </div>
        <div className="p-4 space-y-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="flex items-center gap-4">
              <Skeleton className="h-4 w-12" />
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-4 w-20 ml-auto" />
              <Skeleton className="h-4 w-16" />
            </div>
          ))}
        </div>
      </Card>
    );
  }

  return (
    <Card padding="none">
      <div className="p-4 border-b border-neutral-800">
        <input
          type="text"
          placeholder="Search assets..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="h-8 px-3 text-sm rounded-md bg-neutral-900 border border-neutral-700 text-neutral-100 placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent w-full max-w-sm"
        />
      </div>
      <Table>
        <Thead>
          <Tr>
            <Th className="cursor-pointer select-none" onClick={() => handleSort('symbol')}>
              Symbol{sortIndicator('symbol')}
            </Th>
            <Th>Name</Th>
            <Th className="text-right cursor-pointer select-none" onClick={() => handleSort('priceUsd')}>
              Price{sortIndicator('priceUsd')}
            </Th>
            <Th className="text-right cursor-pointer select-none" onClick={() => handleSort('change24hPct')}>
              24h{sortIndicator('change24hPct')}
            </Th>
          </Tr>
        </Thead>
        <Tbody>
          {rows.length === 0 ? (
            <Tr>
              <Td colSpan={4}>
                <EmptyState title="No assets found" />
              </Td>
            </Tr>
          ) : (
            rows.map((row) => (
              <Tr
                key={row.id}
                className={`cursor-pointer ${selectedSymbol === row.symbol ? 'bg-neutral-800/50' : ''}`}
                onClick={() => onAssetSelect?.(row.symbol)}
              >
                <Td className="font-medium text-neutral-100">{row.symbol}</Td>
                <Td className="text-neutral-400 truncate max-w-[200px]">{row.name}</Td>
                <Td className="text-right">
                  <PriceDisplay value={row.priceUsd} className="text-sm" />
                </Td>
                <Td className="text-right">
                  <PercentageDisplay value={row.change24hPct} className="text-sm" />
                </Td>
              </Tr>
            ))
          )}
        </Tbody>
      </Table>
    </Card>
  );
}
