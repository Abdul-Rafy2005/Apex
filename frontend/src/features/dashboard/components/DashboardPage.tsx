import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@/lib/queryClient';
import { usePortfolioStream } from '@/features/portfolio/hooks/usePortfolioStream';
import { PortfolioSummaryCard } from './PortfolioSummaryCard';
import { DailyPnlCard } from './DailyPnlCard';
import { AllocationChart } from './AllocationChart';
import { WatchlistTicker } from './WatchlistTicker';
import { RecentTradesTable } from './RecentTradesTable';

function DashboardContent() {
  usePortfolioStream();

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-neutral-100">Dashboard</h1>
        <p className="text-sm text-neutral-400 mt-1">Your portfolio at a glance</p>
      </div>

      {/* Summary cards row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <PortfolioSummaryCard />
        <DailyPnlCard />
        <AllocationChart />
        <WatchlistTicker />
      </div>

      {/* Recent trades */}
      <RecentTradesTable />
    </div>
  );
}

export function DashboardPage() {
  return (
    <QueryClientProvider client={queryClient}>
      <DashboardContent />
    </QueryClientProvider>
  );
}
