import { useState, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@/lib/queryClient';
import { Card } from '@/components/ui/Card';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { PercentageDisplay } from '@/components/shared/PercentageDisplay';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { useAssets, useAssetPrices } from '@/features/market/hooks/useMarket';
import { usePriceStream } from '@/features/market/hooks/usePriceStream';
import { usePortfolio } from '@/features/portfolio/hooks/usePortfolioQuery';
import { AssetDetail } from './AssetDetail';
import { OrderPanel } from './OrderPanel';

function TradeContent() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedSymbol = searchParams.get('symbol');

  const [search, setSearch] = useState('');

  const { data: assets, isLoading: assetsLoading } = useAssets();
  const symbols = useMemo(() => assets?.map((a) => a.symbol) ?? [], [assets]);
  const { data: prices, isLoading: pricesLoading } = useAssetPrices(symbols);
  const { data: portfolio } = usePortfolio();

  usePriceStream(symbols);

  const isLoading = assetsLoading || pricesLoading;

  const priceMap = useMemo(() => {
    if (!prices) return {};
    return Object.fromEntries(prices.map((p) => [p.symbol, p]));
  }, [prices]);

  const selectedAsset = useMemo(
    () => assets?.find((a) => a.symbol === selectedSymbol),
    [assets, selectedSymbol],
  );

  const selectedPrice = selectedSymbol ? (priceMap[selectedSymbol]?.priceUsd ?? 0) : 0;
  const selectedChange = selectedSymbol ? (priceMap[selectedSymbol]?.change24hPct ?? 0) : 0;

  const filteredAssets = useMemo(() => {
    if (!assets) return [];
    return assets.filter((a) => {
      if (!search) return true;
      const q = search.toLowerCase();
      return a.symbol.toLowerCase().includes(q) || a.name.toLowerCase().includes(q);
    });
  }, [assets, search]);

  function handleSelect(symbol: string) {
    setSearchParams({ symbol });
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-neutral-100">Trade</h1>
        <p className="text-sm text-neutral-400 mt-1">Execute market trades with live prices</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: Asset search + detail */}
        <div className="lg:col-span-2 space-y-4">
          {/* Asset search */}
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
                  <Th>Symbol</Th>
                  <Th>Name</Th>
                  <Th className="text-right">Price</Th>
                  <Th className="text-right">24h</Th>
                </Tr>
              </Thead>
              <Tbody>
                {isLoading ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <Tr key={i}>
                      <Td><Skeleton className="h-4 w-12" /></Td>
                      <Td><Skeleton className="h-4 w-24" /></Td>
                      <Td className="text-right"><Skeleton className="h-4 w-20 ml-auto" /></Td>
                      <Td className="text-right"><Skeleton className="h-4 w-16 ml-auto" /></Td>
                    </Tr>
                  ))
                ) : filteredAssets.length === 0 ? (
                  <Tr>
                    <Td colSpan={4}>
                      <EmptyState title="No assets found" />
                    </Td>
                  </Tr>
                ) : (
                  filteredAssets.map((a) => {
                    const p = priceMap[a.symbol];
                    return (
                      <Tr
                        key={a.id}
                        className={`cursor-pointer ${selectedSymbol === a.symbol ? 'bg-neutral-800/50' : ''}`}
                        onClick={() => handleSelect(a.symbol)}
                      >
                        <Td className="font-medium text-neutral-100">{a.symbol}</Td>
                        <Td className="text-neutral-400 truncate max-w-[200px]">{a.name}</Td>
                        <Td className="text-right">
                          <PriceDisplay value={p?.priceUsd ?? 0} className="text-sm" />
                        </Td>
                        <Td className="text-right">
                          <PercentageDisplay value={p?.change24hPct ?? 0} className="text-sm" />
                        </Td>
                      </Tr>
                    );
                  })
                )}
              </Tbody>
            </Table>
          </Card>

          {/* Asset detail */}
          {selectedAsset && (
            <AssetDetail
              asset={selectedAsset}
              currentPrice={selectedPrice}
              change24hPct={selectedChange}
            />
          )}
        </div>

        {/* Right: Order panel */}
        <div className="lg:col-span-1">
          {selectedAsset ? (
            <OrderPanel
              asset={selectedAsset}
              currentPrice={selectedPrice}
              portfolio={portfolio}
            />
          ) : (
            <Card padding="md">
              <EmptyState
                title="Select an asset"
                description="Click on an asset to start trading"
              />
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

export function TradePage() {
  return (
    <QueryClientProvider client={queryClient}>
      <TradeContent />
    </QueryClientProvider>
  );
}
