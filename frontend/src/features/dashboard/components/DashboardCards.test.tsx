import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PortfolioSummaryCard } from './PortfolioSummaryCard';
import { DailyPnlCard } from './DailyPnlCard';
import { useAnalyticsSummary } from '@/features/analytics/hooks/useAnalytics';

vi.mock('@/features/analytics/hooks/useAnalytics', () => ({
  useAnalyticsSummary: vi.fn(),
}));

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
}

function renderWithProviders(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      {ui}
    </QueryClientProvider>,
  );
}

const mockSummary = {
  snapshotDate: '2024-01-15',
  portfolioValue: 105230.50,
  dailyReturnPct: 2.35,
  dailyPnlSeries: [
    { date: '2024-01-14', pnl: -500 },
    { date: '2024-01-15', pnl: 2450.75 },
  ],
  totalReturnPct: 5.23,
  totalTrades: 42,
  winningTrades: 28,
  losingTrades: 14,
  winRate: 66.67,
  allocationBreakdown: [
    { symbol: 'BTC', value: 65000, pct: 61.8 },
    { symbol: 'ETH', value: 25000, pct: 23.8 },
    { symbol: 'SOL', value: 15230.5, pct: 14.5 },
  ],
};

describe('PortfolioSummaryCard', () => {
  beforeEach(() => {
    vi.mocked(useAnalyticsSummary).mockReturnValue({
      data: mockSummary,
      isLoading: false,
      error: null,
    } as any);
  });

  it('renders portfolio value', () => {
    renderWithProviders(<PortfolioSummaryCard />);
    expect(screen.getByText('Portfolio Value')).toBeInTheDocument();
    expect(screen.getByText(/\$105,230\.50/)).toBeInTheDocument();
  });

  it('renders daily return percentage', () => {
    renderWithProviders(<PortfolioSummaryCard />);
    expect(screen.getByText(/2\.35%/)).toBeInTheDocument();
    expect(screen.getByText('today')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    vi.mocked(useAnalyticsSummary).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as any);

    renderWithProviders(<PortfolioSummaryCard />);
    expect(screen.queryByText('Portfolio Value')).not.toBeInTheDocument();
  });

  it('shows error state', () => {
    vi.mocked(useAnalyticsSummary).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('API error'),
    } as any);

    renderWithProviders(<PortfolioSummaryCard />);
    expect(screen.getByText('Portfolio value')).toBeInTheDocument();
    expect(screen.getByText('--')).toBeInTheDocument();
  });

  it('handles zero portfolio value', () => {
    vi.mocked(useAnalyticsSummary).mockReturnValue({
      data: { ...mockSummary, portfolioValue: 0, dailyReturnPct: 0 },
      isLoading: false,
      error: null,
    } as any);

    renderWithProviders(<PortfolioSummaryCard />);
    expect(screen.getByText('$0.00')).toBeInTheDocument();
  });
});

describe('DailyPnlCard', () => {
  beforeEach(() => {
    vi.mocked(useAnalyticsSummary).mockReturnValue({
      data: mockSummary,
      isLoading: false,
      error: null,
    } as any);
  });

  it('renders daily P/L', () => {
    renderWithProviders(<DailyPnlCard />);
    expect(screen.getByText('Daily P/L')).toBeInTheDocument();
    expect(screen.getByText(/\$2,450\.75/)).toBeInTheDocument();
  });

  it('renders total return', () => {
    renderWithProviders(<DailyPnlCard />);
    expect(screen.getByText('Total return')).toBeInTheDocument();
    expect(screen.getByText(/5\.23%/)).toBeInTheDocument();
  });

  it('shows loading state', () => {
    vi.mocked(useAnalyticsSummary).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as any);

    renderWithProviders(<DailyPnlCard />);
    expect(screen.queryByText('Daily P/L')).not.toBeInTheDocument();
  });

  it('handles empty dailyPnlSeries', () => {
    vi.mocked(useAnalyticsSummary).mockReturnValue({
      data: { ...mockSummary, dailyPnlSeries: [] },
      isLoading: false,
      error: null,
    } as any);

    renderWithProviders(<DailyPnlCard />);
    expect(screen.getByText('$0.00')).toBeInTheDocument();
  });
});
