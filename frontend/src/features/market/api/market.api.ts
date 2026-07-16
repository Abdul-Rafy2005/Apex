import { apiFetch } from '@/lib/api';
import type { AssetResponse } from '../types/asset';
import type { MarketOverviewResponse } from '../types/overview';
import type { HistoricalPriceResponse } from '../types/history';

export const marketApi = {
  listAssets: () =>
    apiFetch<AssetResponse[]>('/api/v1/market/assets'),

  getPrices: (symbols: string[]) =>
    apiFetch<{ symbol: string; priceUsd: number; change24hPct: number; timestamp: string }[]>(
      `/api/v1/market/prices?symbols=${symbols.join(',')}`,
    ),

  getOverview: () =>
    apiFetch<MarketOverviewResponse>('/api/v1/market/overview'),

  getHistory: (symbol: string, days = 30) =>
    apiFetch<HistoricalPriceResponse[]>(
      `/api/v1/market/${encodeURIComponent(symbol)}/history?days=${days}`,
    ),

  searchGlobalAssets: (query: string) =>
    apiFetch<{ id: string; symbol: string; name: string; thumb: string }[]>(
      `/api/v1/market/search?query=${encodeURIComponent(query)}`,
    ),

  addAsset: (payload: { symbol: string; name: string; providerSource: string }) =>
    apiFetch<AssetResponse>('/api/v1/market/assets', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
};
