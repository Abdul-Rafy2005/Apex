import { apiFetch } from '@/lib/api';
import type { AnalyticsSummaryResponse, AnalyticsHistoryResponse } from '../types/analytics';

export const analyticsApi = {
  getSummary: () =>
    apiFetch<AnalyticsSummaryResponse>('/api/v1/analytics/summary'),

  getHistory: () =>
    apiFetch<AnalyticsHistoryResponse>('/api/v1/analytics/history'),
};
