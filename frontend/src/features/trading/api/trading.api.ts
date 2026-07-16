import { apiFetch } from '@/lib/api';
import type { ExecuteTradeRequest, TradeResponse } from '../types/trade';

export const tradingApi = {
  executeTrade: (request: ExecuteTradeRequest) =>
    apiFetch<TradeResponse>('/api/v1/trading/execute', {
      method: 'POST',
      body: JSON.stringify(request),
      headers: {
        'Idempotency-Key': request.idempotencyKey,
      },
    }),
};
