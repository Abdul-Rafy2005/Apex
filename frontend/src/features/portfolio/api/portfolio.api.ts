import { apiFetch } from '@/lib/api';
import type { PortfolioResponse } from '../types/portfolio';
import type { TradeResponse } from '../types/trade';

export const portfolioApi = {
  getPortfolio: () =>
    apiFetch<PortfolioResponse>('/api/v1/trading/portfolio'),

  getTrades: (page = 0, size = 20) =>
    apiFetch<{ content: TradeResponse[]; totalElements: number; totalPages: number }>(
      `/api/v1/trading/trades?page=${page}&size=${size}`,
    ),
};
