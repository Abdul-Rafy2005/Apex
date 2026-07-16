export interface PortfolioResponse {
  id: string;
  cashBalance: number;
  holdings: HoldingResponse[];
  totalUnrealizedPnl: number;
}

export interface HoldingResponse {
  assetId: string;
  symbol: string;
  name: string;
  quantity: number;
  avgEntryPrice: number;
  currentPrice: number;
  unrealizedPnl: number;
}
