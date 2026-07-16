import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { usePriceStream } from '@/features/market/hooks/usePriceStream';
import { usePortfolioStream } from '@/features/portfolio/hooks/usePortfolioStream';

// Mock the websocket module
const mockSubscribe = vi.fn();
const mockUnsubscribe = vi.fn();

vi.mock('@/lib/websocket', () => ({
  subscribe: (dest: string, cb: (body: string) => void) => {
    mockSubscribe(dest, cb);
    return { unsubscribe: () => mockUnsubscribe(dest) };
  },
  unsubscribe: (dest: string) => mockUnsubscribe(dest),
  isConnected: () => false,
}));

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
}

function renderWithQueryClient(hook: () => void) {
  const queryClient = createTestQueryClient();
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return {
    ...renderHook(hook, { wrapper }),
    queryClient,
  };
}

describe('usePriceStream', () => {
  beforeEach(() => {
    mockSubscribe.mockClear();
    mockUnsubscribe.mockClear();
  });

  it('subscribes to price topics for each symbol', () => {
    renderWithQueryClient(() => usePriceStream(['BTC', 'ETH']));

    expect(mockSubscribe).toHaveBeenCalledTimes(2);
    expect(mockSubscribe).toHaveBeenCalledWith('/topic/prices/BTC', expect.any(Function));
    expect(mockSubscribe).toHaveBeenCalledWith('/topic/prices/ETH', expect.any(Function));
  });

  it('feeds price updates into query cache', () => {
    let priceCallback: ((body: string) => void) | undefined;
    mockSubscribe.mockImplementation((_dest: string, cb: (body: string) => void) => {
      priceCallback = cb;
      return { unsubscribe: vi.fn() };
    });

    const { queryClient } = renderWithQueryClient(() => usePriceStream(['BTC']));

    const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData');

    act(() => {
      priceCallback!(
        JSON.stringify({
          symbol: 'BTC',
          priceUsd: 45000,
          change24hPct: 2.5,
          timestamp: '2026-07-14T12:00:00Z',
        }),
      );
    });

    expect(setQueryDataSpy).toHaveBeenCalledWith(
      ['market', 'prices', ['BTC']],
      expect.any(Function),
    );
  });

  it('ignores malformed messages without crashing', () => {
    let priceCallback: ((body: string) => void) | undefined;
    mockSubscribe.mockImplementation((_dest: string, cb: (body: string) => void) => {
      priceCallback = cb;
      return { unsubscribe: vi.fn() };
    });

    renderWithQueryClient(() => usePriceStream(['BTC']));

    act(() => {
      priceCallback!('not json');
    });

    // Should not throw
  });

  it('unsubscribes on unmount', () => {
    const { unmount } = renderWithQueryClient(() => usePriceStream(['BTC']));
    unmount();

    expect(mockUnsubscribe).toHaveBeenCalledWith('/topic/prices/BTC');
  });
});

describe('usePortfolioStream', () => {
  beforeEach(() => {
    mockSubscribe.mockClear();
    mockUnsubscribe.mockClear();
  });

  it('subscribes to portfolio queue', () => {
    renderWithQueryClient(() => usePortfolioStream());

    expect(mockSubscribe).toHaveBeenCalledTimes(1);
    expect(mockSubscribe).toHaveBeenCalledWith('/user/queue/portfolio', expect.any(Function));
  });

  it('invalidates queries when a trade event arrives', () => {
    let eventCallback: ((body: string) => void) | undefined;
    mockSubscribe.mockImplementation((_dest: string, cb: (body: string) => void) => {
      eventCallback = cb;
      return { unsubscribe: vi.fn() };
    });

    const { queryClient } = renderWithQueryClient(() => usePortfolioStream());

    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    act(() => {
      eventCallback!(
        JSON.stringify({
          tradeId: '11111111-1111-1111-1111-111111111111',
          portfolioId: '22222222-2222-2222-2222-222222222222',
          assetId: '33333333-3333-3333-3333-333333333333',
          side: 'BUY',
          quantity: 1,
          price: 42000,
          fee: 42,
          executedAt: '2026-07-14T12:00:00Z',
        }),
      );
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['trades'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['analytics', 'summary'] });
  });

  it('ignores malformed messages without crashing', () => {
    let eventCallback: ((body: string) => void) | undefined;
    mockSubscribe.mockImplementation((_dest: string, cb: (body: string) => void) => {
      eventCallback = cb;
      return { unsubscribe: vi.fn() };
    });

    renderWithQueryClient(() => usePortfolioStream());

    act(() => {
      eventCallback!('invalid json');
    });

    // Should not throw
  });

  it('unsubscribes on unmount', () => {
    const { unmount } = renderWithQueryClient(() => usePortfolioStream());
    unmount();

    expect(mockUnsubscribe).toHaveBeenCalledWith('/user/queue/portfolio');
  });
});
