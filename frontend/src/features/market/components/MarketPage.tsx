import { useState } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@/lib/queryClient';
import { PriceTable } from './PriceTable';
import { MarketOverview } from './MarketOverviewPanel';
import { CandlestickChart } from './CandlestickChart';

function MarketContent() {
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-neutral-100">Market</h1>
        <p className="text-sm text-neutral-400 mt-1">Live prices and market data</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Main content: price table + chart */}
        <div className="lg:col-span-3 space-y-6">
          <PriceTable
            onAssetSelect={setSelectedSymbol}
            selectedSymbol={selectedSymbol ?? undefined}
          />
          <CandlestickChart symbol={selectedSymbol} />
        </div>

        {/* Sidebar: gainers/losers */}
        <div className="lg:col-span-1">
          <MarketOverview onAssetSelect={setSelectedSymbol} />
        </div>
      </div>
    </div>
  );
}

export function MarketPage() {
  return (
    <QueryClientProvider client={queryClient}>
      <MarketContent />
    </QueryClientProvider>
  );
}
