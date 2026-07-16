import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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

export function useGlobalSearch(query: string) {
  return useQuery({
    queryKey: ['market', 'search', query],
    queryFn: () => marketApi.searchGlobalAssets(query),
    enabled: query.length > 1,
  });
}

export function useAddAsset() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: marketApi.addAsset,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['market', 'assets'] });
      queryClient.invalidateQueries({ queryKey: ['market', 'overview'] });
    },
  });
}
