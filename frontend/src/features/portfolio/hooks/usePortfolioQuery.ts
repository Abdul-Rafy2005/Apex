import { useQuery } from '@tanstack/react-query';
import { portfolioApi } from '../api/portfolio.api';

export function usePortfolio() {
  return useQuery({
    queryKey: ['portfolio'],
    queryFn: portfolioApi.getPortfolio,
  });
}

export function useTrades(page = 0, size = 20) {
  return useQuery({
    queryKey: ['trades', page, size],
    queryFn: () => portfolioApi.getTrades(page, size),
  });
}
