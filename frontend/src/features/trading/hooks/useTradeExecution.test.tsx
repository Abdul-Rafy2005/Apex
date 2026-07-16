import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useTradeExecution } from './useTradeExecution';
import * as tradingApi from '../api/trading.api';

vi.mock('../api/trading.api', () => ({
  tradingApi: {
    executeTrade: vi.fn(),
  },
}));

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function renderWithQueryClient(hook: () => ReturnType<typeof useTradeExecution>) {
  const queryClient = createTestQueryClient();
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return {
    ...renderHook(hook, { wrapper }),
    queryClient,
  };
}

describe('useTradeExecution', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('generates a fresh idempotency key on mount', () => {
    const { result } = renderWithQueryClient(() => useTradeExecution());
    expect(result.current.idempotencyKey).toBeTruthy();
    expect(typeof result.current.idempotencyKey).toBe('string');
  });

  it('resets key after successful trade', async () => {
    vi.mocked(tradingApi.tradingApi.executeTrade).mockResolvedValueOnce({
      id: 'trade-1',
      portfolioId: 'p1',
      assetId: 'a1',
      side: 'BUY',
      quantity: 1,
      price: 65000,
      fee: 65,
      idempotencyKey: 'key-1',
      executedAt: '2024-01-01T00:00:00Z',
    });

    const { result } = renderWithQueryClient(() => useTradeExecution());
    const keyBefore = result.current.idempotencyKey;

    await act(async () => {
      await result.current.executeTradeAsync({
        assetId: 'a1',
        side: 'BUY',
        quantity: 1,
      });
    });

    expect(result.current.idempotencyKey).toBeTruthy();
    expect(result.current.idempotencyKey).not.toBe(keyBefore);
  });

  it('resets key after failed trade (insufficient funds)', async () => {
    vi.mocked(tradingApi.tradingApi.executeTrade).mockRejectedValueOnce(
      new Error('Insufficient funds: required 130130, available 10000'),
    );

    const { result } = renderWithQueryClient(() => useTradeExecution());
    const keyBefore = result.current.idempotencyKey;

    await act(async () => {
      try {
        await result.current.executeTradeAsync({
          assetId: 'a1',
          side: 'BUY',
          quantity: 2,
        });
      } catch {
        // expected
      }
    });

    expect(result.current.idempotencyKey).not.toBe(keyBefore);
  });

  it('resets key after failed trade (insufficient holdings)', async () => {
    vi.mocked(tradingApi.tradingApi.executeTrade).mockRejectedValueOnce(
      new Error('Insufficient holdings: required 10, available 0.5'),
    );

    const { result } = renderWithQueryClient(() => useTradeExecution());
    const keyBefore = result.current.idempotencyKey;

    await act(async () => {
      try {
        await result.current.executeTradeAsync({
          assetId: 'a1',
          side: 'SELL',
          quantity: 10,
        });
      } catch {
        // expected
      }
    });

    expect(result.current.idempotencyKey).not.toBe(keyBefore);
  });

  it('resets key after 409 conflict', async () => {
    vi.mocked(tradingApi.tradingApi.executeTrade).mockRejectedValueOnce(
      new Error('Trade conflict: portfolio was modified concurrently. Please retry.'),
    );

    const { result } = renderWithQueryClient(() => useTradeExecution());
    const keyBefore = result.current.idempotencyKey;

    await act(async () => {
      try {
        await result.current.executeTradeAsync({
          assetId: 'a1',
          side: 'BUY',
          quantity: 1,
        });
      } catch {
        // expected
      }
    });

    expect(result.current.idempotencyKey).not.toBe(keyBefore);
  });

  it('resets key after network error', async () => {
    vi.mocked(tradingApi.tradingApi.executeTrade).mockRejectedValueOnce(
      new Error('Network request failed'),
    );

    const { result } = renderWithQueryClient(() => useTradeExecution());
    const keyBefore = result.current.idempotencyKey;

    await act(async () => {
      try {
        await result.current.executeTradeAsync({
          assetId: 'a1',
          side: 'BUY',
          quantity: 1,
        });
      } catch {
        // expected
      }
    });

    expect(result.current.idempotencyKey).not.toBe(keyBefore);
  });

  it('rejected trade does not block subsequent trade with same logical intent', async () => {
    const executeTradeMock = vi.mocked(tradingApi.tradingApi.executeTrade);

    // First call: insufficient funds
    executeTradeMock.mockRejectedValueOnce(
      new Error('Insufficient funds: required 130130, available 10000'),
    );

    const { result } = renderWithQueryClient(() => useTradeExecution());

    // Attempt 1: too expensive, fails
    await act(async () => {
      try {
        await result.current.executeTradeAsync({
          assetId: 'a1',
          side: 'BUY',
          quantity: 2,
        });
      } catch {
        // expected
      }
    });

    // Key was reset after failure
    const keyAfterFailure = result.current.idempotencyKey;
    expect(keyAfterFailure).toBeTruthy();

    // Second call: smaller quantity, should succeed
    executeTradeMock.mockResolvedValueOnce({
      id: 'trade-2',
      portfolioId: 'p1',
      assetId: 'a1',
      side: 'BUY',
      quantity: 0.1,
      price: 65000,
      fee: 6.5,
      idempotencyKey: keyAfterFailure,
      executedAt: '2024-01-01T00:00:00Z',
    });

    await act(async () => {
      await result.current.executeTradeAsync({
        assetId: 'a1',
        side: 'BUY',
        quantity: 0.1,
      });
    });

    // Second call succeeded, key reset again
    expect(result.current.idempotencyKey).not.toBe(keyAfterFailure);
    expect(executeTradeMock).toHaveBeenCalledTimes(2);
  });
});
