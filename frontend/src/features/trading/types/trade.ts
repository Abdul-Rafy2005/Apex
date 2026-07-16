export interface ExecuteTradeRequest {
  assetId: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  idempotencyKey: string;
}

export interface TradeResponse {
  id: string;
  portfolioId: string;
  assetId: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  fee: number;
  idempotencyKey: string;
  executedAt: string;
}
