import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { tradingApi } from '../api/trading.api';
import type { ExecuteTradeRequest, TradeResponse } from '../types/trade';

export function useTradeExecution() {
  const queryClient = useQueryClient();
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());

  const mutation = useMutation({
    mutationFn: (request: Omit<ExecuteTradeRequest, 'idempotencyKey'>) =>
      tradingApi.executeTrade({ ...request, idempotencyKey }),
    onSuccess: (_data: TradeResponse) => {
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      queryClient.invalidateQueries({ queryKey: ['trades'] });
      queryClient.invalidateQueries({ queryKey: ['analytics', 'summary'] });
      setIdempotencyKey(crypto.randomUUID());
    },
    onError: () => {
      setIdempotencyKey(crypto.randomUUID());
    },
  });

  return {
    executeTrade: mutation.mutate,
    executeTradeAsync: mutation.mutateAsync,
    isPending: mutation.isPending,
    error: mutation.error,
    reset: mutation.reset,
    idempotencyKey,
  };
}
