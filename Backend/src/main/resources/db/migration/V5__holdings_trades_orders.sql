CREATE TABLE holdings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID NOT NULL REFERENCES portfolios(id),
    asset_id        UUID NOT NULL REFERENCES assets(id),
    quantity        NUMERIC(19, 8) NOT NULL DEFAULT 0,
    avg_entry_price NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (portfolio_id, asset_id)
);

CREATE INDEX idx_holdings_portfolio ON holdings(portfolio_id);

CREATE TYPE order_side AS ENUM ('BUY', 'SELL');
CREATE TYPE order_type AS ENUM ('MARKET', 'LIMIT', 'STOP_LOSS', 'TAKE_PROFIT');
CREATE TYPE order_status AS ENUM ('PENDING', 'FILLED', 'CANCELLED', 'REJECTED');

CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID NOT NULL REFERENCES portfolios(id),
    asset_id        UUID NOT NULL REFERENCES assets(id),
    side            order_side NOT NULL,
    type            order_type NOT NULL DEFAULT 'MARKET',
    status          order_status NOT NULL DEFAULT 'PENDING',
    quantity        NUMERIC(19, 8) NOT NULL,
    limit_price     NUMERIC(19, 4),
    trigger_price   NUMERIC(19, 4),
    params          JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_portfolio ON orders(portfolio_id);

CREATE TABLE trades (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID NOT NULL REFERENCES portfolios(id),
    asset_id        UUID NOT NULL REFERENCES assets(id),
    side            order_side NOT NULL,
    quantity        NUMERIC(19, 8) NOT NULL,
    price           NUMERIC(19, 4) NOT NULL,
    fee             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    executed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trades_portfolio ON trades(portfolio_id);
CREATE INDEX idx_trades_asset ON trades(asset_id);
CREATE INDEX idx_trades_executed_at ON trades(executed_at);
