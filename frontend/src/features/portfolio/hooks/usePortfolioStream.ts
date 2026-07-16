import { useCallback, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { subscribe, unsubscribe } from '@/lib/websocket';

const PORTFOLIO_DEST = '/user/queue/portfolio';

export function usePortfolioStream() {
  const queryClient = useQueryClient();

  const handleEvent = useCallback(
    (body: string) => {
      try {
        void JSON.parse(body);
        queryClient.invalidateQueries({ queryKey: ['portfolio'] });
        queryClient.invalidateQueries({ queryKey: ['trades'] });
        queryClient.invalidateQueries({ queryKey: ['analytics', 'summary'] });
      } catch {
        // Malformed message — ignore
      }
    },
    [queryClient],
  );

  useEffect(() => {
    const sub = subscribe(PORTFOLIO_DEST, handleEvent);

    return () => {
      sub?.unsubscribe();
      unsubscribe(PORTFOLIO_DEST);
    };
  }, [handleEvent]);
}
