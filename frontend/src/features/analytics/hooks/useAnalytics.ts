import { useQuery } from '@tanstack/react-query';
import { analyticsApi } from '../api/analytics.api';

export function useAnalyticsSummary() {
  return useQuery({
    queryKey: ['analytics', 'summary'],
    queryFn: analyticsApi.getSummary,
  });
}

export function useAnalyticsHistory() {
  return useQuery({
    queryKey: ['analytics', 'history'],
    queryFn: analyticsApi.getHistory,
  });
}
