import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PriceTable } from './PriceTable';
import { useAssets, useAssetPrices } from '../hooks/useMarket';
import { usePriceStream } from '../hooks/usePriceStream';

vi.mock('../hooks/useMarket', () => ({
  useAssets: vi.fn(),
  useAssetPrices: vi.fn(),
  useGlobalSearch: vi.fn().mockReturnValue({ data: [], isLoading: false }),
  useAddAsset: vi.fn().mockReturnValue({ mutate: vi.fn(), isPending: false }),
}));

vi.mock('../hooks/usePriceStream', () => ({
  usePriceStream: vi.fn(),
}));

const mockAssets = [
  { id: '1', symbol: 'BTC', name: 'Bitcoin', precision: 2, providerSource: 'coingecko', tradable: true, createdAt: '2024-01-01' },
  { id: '2', symbol: 'ETH', name: 'Ethereum', precision: 2, providerSource: 'coingecko', tradable: true, createdAt: '2024-01-01' },
  { id: '3', symbol: 'SOL', name: 'Solana', precision: 2, providerSource: 'coingecko', tradable: true, createdAt: '2024-01-01' },
];

const mockPrices = [
  { symbol: 'BTC', priceUsd: 65000, change24hPct: 2.5, timestamp: '2024-01-01T00:00:00Z' },
  { symbol: 'ETH', priceUsd: 3200, change24hPct: -1.2, timestamp: '2024-01-01T00:00:00Z' },
  { symbol: 'SOL', priceUsd: 150, change24hPct: 5.8, timestamp: '2024-01-01T00:00:00Z' },
];

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

describe('PriceTable', () => {
  beforeEach(() => {
    vi.mocked(useAssets).mockReturnValue({
      data: mockAssets,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAssets>);

    vi.mocked(useAssetPrices).mockReturnValue({
      data: mockPrices,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAssetPrices>);

    vi.mocked(usePriceStream).mockReturnValue(undefined);
  });

  it('renders all assets', () => {
    renderWithProviders(<PriceTable />);
    expect(screen.getByText('BTC')).toBeInTheDocument();
    expect(screen.getByText('ETH')).toBeInTheDocument();
    expect(screen.getByText('SOL')).toBeInTheDocument();
  });

  it('displays asset names', () => {
    renderWithProviders(<PriceTable />);
    expect(screen.getByText('Bitcoin')).toBeInTheDocument();
    expect(screen.getByText('Ethereum')).toBeInTheDocument();
    expect(screen.getByText('Solana')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    vi.mocked(useAssets).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useAssets>);

    renderWithProviders(<PriceTable />);
    expect(screen.queryByText('BTC')).not.toBeInTheDocument();
  });

  it('filters assets by search', async () => {
    renderWithProviders(<PriceTable />);

    const input = screen.getByPlaceholderText('Search assets...');
    const { default: userEvent } = await import('@testing-library/user-event');
    await userEvent.type(input, 'BTC');

    expect(screen.getByText('BTC')).toBeInTheDocument();
    expect(screen.queryByText('ETH')).not.toBeInTheDocument();
    expect(screen.queryByText('SOL')).not.toBeInTheDocument();
  });

  it('calls onAssetSelect when row clicked', async () => {
    const onSelect = vi.fn();
    renderWithProviders(<PriceTable onAssetSelect={onSelect} />);

    const { default: userEvent } = await import('@testing-library/user-event');
    await userEvent.click(screen.getByText('BTC'));

    expect(onSelect).toHaveBeenCalledWith('BTC');
  });

  it('highlights selected row', () => {
    renderWithProviders(<PriceTable selectedSymbol="BTC" />);

    const rows = screen.getAllByRole('row');
    const btcRow = rows.find((row) => row.textContent?.includes('BTC'));
    expect(btcRow?.className).toContain('bg-neutral-800/50');
  });
});
