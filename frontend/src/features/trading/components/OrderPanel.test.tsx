import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { OrderPanel } from './OrderPanel';
import { ToastProvider } from '@/components/ui/Toast';
import type { AssetResponse } from '@/features/market/types/asset';
import type { PortfolioResponse } from '@/features/portfolio/types/portfolio';

vi.mock('@/features/trading/hooks/useTradeExecution', () => ({
  useTradeExecution: vi.fn(() => ({
    executeTradeAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
    error: null,
    reset: vi.fn(),
    idempotencyKey: 'test-key',
  })),
}));

const mockAsset: AssetResponse = {
  id: 'asset-btc-001',
  symbol: 'BTC',
  name: 'Bitcoin',
  precision: 2,
  providerSource: 'coingecko',
  tradable: true,
  createdAt: '2024-01-01',
};

const mockPortfolio: PortfolioResponse = {
  id: 'portfolio-001',
  cashBalance: 10000,
  holdings: [
    {
      assetId: 'asset-btc-001',
      symbol: 'BTC',
      name: 'Bitcoin',
      quantity: 0.5,
      avgEntryPrice: 60000,
      currentPrice: 65000,
      unrealizedPnl: 2500,
    },
  ],
  totalUnrealizedPnl: 2500,
};

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function renderWithProviders(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>,
  );
}

describe('OrderPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders buy and sell buttons', () => {
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    expect(screen.getByRole('button', { name: 'Buy' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sell' })).toBeInTheDocument();
  });

  it('defaults to BUY side', () => {
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    const buyBtn = screen.getByRole('button', { name: 'Buy' });
    expect(buyBtn.className).toContain('bg-gain');
  });

  it('switches to SELL side', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    await user.click(screen.getByRole('button', { name: 'Sell' }));
    const sellBtn = screen.getByRole('button', { name: 'Sell' });
    expect(sellBtn.className).toContain('bg-loss');
  });

  it('shows available cash for BUY', () => {
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    expect(screen.getByText('Available cash')).toBeInTheDocument();
    expect(screen.getByText('$10000.00')).toBeInTheDocument();
  });

  it('shows available holdings for SELL', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    await user.click(screen.getByRole('button', { name: 'Sell' }));
    expect(screen.getByText('Available BTC')).toBeInTheDocument();
    expect(screen.getByText('0.50000000 BTC')).toBeInTheDocument();
  });

  it('shows insufficient funds error when BUY cost exceeds cash', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    const input = screen.getByPlaceholderText('0.00000000');
    await user.type(input, '0.2');
    expect(screen.getByText(/Insufficient funds/)).toBeInTheDocument();
  });

  it('shows insufficient holdings error when SELL quantity exceeds holdings', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    await user.click(screen.getByRole('button', { name: 'Sell' }));
    const input = screen.getByPlaceholderText('0.00000000');
    await user.type(input, '1');
    expect(screen.getByText(/Insufficient holdings/)).toBeInTheDocument();
  });

  it('shows order summary when quantity is entered', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    const input = screen.getByPlaceholderText('0.00000000');
    await user.type(input, '0.01');
    expect(screen.getByText('Subtotal')).toBeInTheDocument();
    expect(screen.getByText('Fee (0.1%)')).toBeInTheDocument();
    expect(screen.getByText('Total cost')).toBeInTheDocument();
  });

  it('disables submit when validation fails', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    const input = screen.getByPlaceholderText('0.00000000');
    await user.type(input, '0.2');
    const submitBtn = screen.getByRole('button', { name: /BUY BTC/ });
    expect(submitBtn).toBeDisabled();
  });

  it('enables submit when validation passes', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    const input = screen.getByPlaceholderText('0.00000000');
    await user.type(input, '0.01');
    const submitBtn = screen.getByRole('button', { name: /BUY BTC/ });
    expect(submitBtn).not.toBeDisabled();
  });

  it('Max button fills available amount for BUY', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    await user.click(screen.getByText('Max'));
    const input = screen.getByPlaceholderText('0.00000000') as HTMLInputElement;
    expect(parseFloat(input.value)).toBeGreaterThan(0);
    expect(screen.queryByText(/Insufficient funds/)).not.toBeInTheDocument();
  });

  it('Max button fills holdings for SELL', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={mockPortfolio} />,
    );
    await user.click(screen.getByRole('button', { name: 'Sell' }));
    await user.click(screen.getByText('Max'));
    const input = screen.getByPlaceholderText('0.00000000') as HTMLInputElement;
    expect(input.value).toBe('0.50000000');
  });

  it('handles zero portfolio gracefully', () => {
    const emptyPortfolio: PortfolioResponse = {
      id: 'portfolio-002',
      cashBalance: 0,
      holdings: [],
      totalUnrealizedPnl: 0,
    };
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={emptyPortfolio} />,
    );
    expect(screen.getByText('$0.00')).toBeInTheDocument();
  });

  it('handles undefined portfolio gracefully', () => {
    renderWithProviders(
      <OrderPanel asset={mockAsset} currentPrice={65000} portfolio={undefined} />,
    );
    expect(screen.getByText('$0.00')).toBeInTheDocument();
  });
});
