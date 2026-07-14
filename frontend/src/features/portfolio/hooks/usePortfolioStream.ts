import { useCallback, useEffect, useState } from 'react';
import { subscribe, unsubscribe } from '@/lib/websocket';
import type { TradeExecutedEvent } from '../types/event';

const PORTFOLIO_DEST = '/user/queue/portfolio';

export function usePortfolioStream() {
  const [events, setEvents] = useState<TradeExecutedEvent[]>([]);

  const handleEvent = useCallback((body: string) => {
    try {
      const event: TradeExecutedEvent = JSON.parse(body);
      setEvents((prev) => [event, ...prev]);
    } catch {
      // Malformed message — ignore
    }
  }, []);

  useEffect(() => {
    const sub = subscribe(PORTFOLIO_DEST, handleEvent);

    return () => {
      sub?.unsubscribe();
      unsubscribe(PORTFOLIO_DEST);
    };
  }, [handleEvent]);

  return events;
}
