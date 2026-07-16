export interface MarketOverviewResponse {
  topGainers: LivePriceResponse[];
  topLosers: LivePriceResponse[];
  trending: LivePriceResponse[];
  totalAssets: number;
}

export interface LivePriceResponse {
  symbol: string;
  priceUsd: number;
  change24hPct: number;
  timestamp: string;
}
