import { useQuery } from '@tanstack/react-query';
import { marketApi } from '../api/market.api';

export function useAssets() {
  return useQuery({
    queryKey: ['market', 'assets'],
    queryFn: marketApi.listAssets,
  });
}

export function useAssetPrices(symbols: string[]) {
  return useQuery({
    queryKey: ['market', 'prices', symbols],
    queryFn: () => marketApi.getPrices(symbols),
    enabled: symbols.length > 0,
    refetchInterval: 60_000,
  });
}

export function useMarketOverview() {
  return useQuery({
    queryKey: ['market', 'overview'],
    queryFn: marketApi.getOverview,
    refetchInterval: 60_000,
  });
}

export function usePriceHistory(symbol: string, days = 30) {
  return useQuery({
    queryKey: ['market', 'history', symbol, days],
    queryFn: () => marketApi.getHistory(symbol, days),
    enabled: !!symbol,
  });
}
