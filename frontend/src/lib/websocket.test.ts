import { renderHook, act } from '@testing-library/react';
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

describe('usePriceStream', () => {
  beforeEach(() => {
    mockSubscribe.mockClear();
    mockUnsubscribe.mockClear();
  });

  it('subscribes to price topics for each symbol', () => {
    renderHook(() => usePriceStream(['BTC', 'ETH']));

    expect(mockSubscribe).toHaveBeenCalledTimes(2);
    expect(mockSubscribe).toHaveBeenCalledWith('/topic/prices/BTC', expect.any(Function));
    expect(mockSubscribe).toHaveBeenCalledWith('/topic/prices/ETH', expect.any(Function));
  });

  it('updates prices when a message arrives', () => {
    let priceCallback: ((body: string) => void) | undefined;
    mockSubscribe.mockImplementation((_dest: string, cb: (body: string) => void) => {
      priceCallback = cb;
      return { unsubscribe: vi.fn() };
    });

    const { result } = renderHook(() => usePriceStream(['BTC']));

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

    expect(result.current.BTC).toEqual({
      symbol: 'BTC',
      priceUsd: 45000,
      change24hPct: 2.5,
      timestamp: '2026-07-14T12:00:00Z',
    });
  });

  it('ignores malformed messages without crashing', () => {
    let priceCallback: ((body: string) => void) | undefined;
    mockSubscribe.mockImplementation((_dest: string, cb: (body: string) => void) => {
      priceCallback = cb;
      return { unsubscribe: vi.fn() };
    });

    const { result } = renderHook(() => usePriceStream(['BTC']));

    act(() => {
      priceCallback!('not json');
    });

    expect(result.current).toEqual({});
  });

  it('unsubscribes on unmount', () => {
    const { unmount } = renderHook(() => usePriceStream(['BTC']));
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
    renderHook(() => usePortfolioStream());

    expect(mockSubscribe).toHaveBeenCalledTimes(1);
    expect(mockSubscribe).toHaveBeenCalledWith('/user/queue/portfolio', expect.any(Function));
  });

  it('prepends new events when messages arrive', () => {
    let eventCallback: ((body: string) => void) | undefined;
    mockSubscribe.mockImplementation((_dest: string, cb: (body: string) => void) => {
      eventCallback = cb;
      return { unsubscribe: vi.fn() };
    });

    const { result } = renderHook(() => usePortfolioStream());

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

    expect(result.current).toHaveLength(1);
    expect(result.current[0].side).toBe('BUY');
    expect(result.current[0].price).toBe(42000);
  });

  it('ignores malformed messages without crashing', () => {
    let eventCallback: ((body: string) => void) | undefined;
    mockSubscribe.mockImplementation((_dest: string, cb: (body: string) => void) => {
      eventCallback = cb;
      return { unsubscribe: vi.fn() };
    });

    const { result } = renderHook(() => usePortfolioStream());

    act(() => {
      eventCallback!('invalid json');
    });

    expect(result.current).toEqual([]);
  });

  it('unsubscribes on unmount', () => {
    const { unmount } = renderHook(() => usePortfolioStream());
    unmount();

    expect(mockUnsubscribe).toHaveBeenCalledWith('/user/queue/portfolio');
  });
});
