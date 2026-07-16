import { useCallback, useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { subscribe, unsubscribe } from '@/lib/websocket';
import type { LivePrice } from '../types/price';
import type { LivePriceResponse } from '../types/overview';
import type { AssetResponse } from '../types/asset';

const PRICE_TOPICS: Record<string, string> = {};

function topicFor(symbol: string): string {
  if (!PRICE_TOPICS[symbol]) {
    PRICE_TOPICS[symbol] = `/topic/prices/${symbol}`;
  }
  return PRICE_TOPICS[symbol];
}

export function usePriceStream(symbols: string[]) {
  const queryClient = useQueryClient();
  const symbolsRef = useRef(symbols);
  symbolsRef.current = symbols;

  const symbolsKey = symbols.join(',');

  const handlePrice = useCallback(
    (body: string) => {
      try {
        const price: LivePrice = JSON.parse(body);

        queryClient.setQueryData(
          ['market', 'prices', symbolsRef.current],
          (old: LivePriceResponse[] | undefined) => {
            if (!old) return old;
            return old.map((p) =>
              p.symbol === price.symbol
                ? { ...p, priceUsd: price.priceUsd, change24hPct: price.change24hPct, timestamp: price.timestamp }
                : p,
            );
          },
        );

        queryClient.setQueryData(['market', 'assets'], (old: AssetResponse[] | undefined) => {
          if (!old) return old;
          return old;
        });
      } catch {
        // Malformed message — ignore
      }
    },
    [queryClient],
  );

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
}
