import { useCallback, useEffect, useRef, useState } from 'react';
import { subscribe, unsubscribe } from '@/lib/websocket';
import type { LivePrice } from '../types/price';

const PRICE_TOPICS: Record<string, string> = {};

function topicFor(symbol: string): string {
  if (!PRICE_TOPICS[symbol]) {
    PRICE_TOPICS[symbol] = `/topic/prices/${symbol}`;
  }
  return PRICE_TOPICS[symbol];
}

export function usePriceStream(symbols: string[]) {
  const [prices, setPrices] = useState<Record<string, LivePrice>>({});
  const symbolsRef = useRef(symbols);
  symbolsRef.current = symbols;

  const symbolsKey = symbols.join(',');

  const handlePrice = useCallback((body: string) => {
    try {
      const price: LivePrice = JSON.parse(body);
      setPrices((prev) => ({
        ...prev,
        [price.symbol]: {
          symbol: price.symbol,
          priceUsd: Number(price.priceUsd),
          change24hPct: Number(price.change24hPct),
          timestamp: price.timestamp,
        },
      }));
    } catch {
      // Malformed message — ignore
    }
  }, []);

  useEffect(() => {
    const subs: Array<{ dest: string; unsub: () => void }> = [];

    for (const symbol of symbolsRef.current) {
      const dest = topicFor(symbol);
      const sub = subscribe(dest, handlePrice);
      if (sub) {
        subs.push({ dest, unsub: () => sub.unsubscribe() });
      }
    }

    return () => {
      for (const { dest, unsub } of subs) {
        unsub();
        unsubscribe(dest);
      }
    };
  }, [symbolsKey, handlePrice]);

  return prices;
}
