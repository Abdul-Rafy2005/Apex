export interface TradeExecutedEvent {
  tradeId: string;
  portfolioId: string;
  assetId: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  fee: number;
  executedAt: string;
}
