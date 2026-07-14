CREATE TABLE performance_snapshots (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id          UUID NOT NULL REFERENCES portfolios(id),
    snapshot_date         DATE NOT NULL,

    -- Return metrics
    total_return_pct      NUMERIC(10, 4) NOT NULL DEFAULT 0,
    daily_return_pct      NUMERIC(10, 4) NOT NULL DEFAULT 0,

    -- Trade metrics
    total_trades          INTEGER NOT NULL DEFAULT 0,
    winning_trades        INTEGER NOT NULL DEFAULT 0,
    losing_trades         INTEGER NOT NULL DEFAULT 0,
    win_rate              NUMERIC(5, 2) NOT NULL DEFAULT 0,
    avg_win_pct           NUMERIC(10, 4) NOT NULL DEFAULT 0,
    avg_loss_pct          NUMERIC(10, 4) NOT NULL DEFAULT 0,
    largest_gain_pct      NUMERIC(10, 4) NOT NULL DEFAULT 0,
    largest_loss_pct      NUMERIC(10, 4) NOT NULL DEFAULT 0,

    -- Risk metrics
    sharpe_ratio          NUMERIC(10, 4) NOT NULL DEFAULT 0,
    max_drawdown_pct      NUMERIC(10, 4) NOT NULL DEFAULT 0,
    avg_holding_period_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    risk_score            INTEGER NOT NULL DEFAULT 0,

    -- Portfolio snapshot values
    portfolio_value       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    cash_balance          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    invested_value        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    realized_pnl          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    unrealized_pnl        NUMERIC(19, 4) NOT NULL DEFAULT 0,

    -- Best/worst asset
    best_asset_symbol     VARCHAR(20),
    best_asset_return_pct NUMERIC(10, 4) NOT NULL DEFAULT 0,
    worst_asset_symbol    VARCHAR(20),
    worst_asset_return_pct NUMERIC(10, 4) NOT NULL DEFAULT 0,

    -- JSON-serialized allocation breakdown: [{"symbol":"BTC","value":50000,"pct":50.0},...]
    allocation_breakdown  TEXT NOT NULL DEFAULT '[]',

    -- JSON-serialized daily P/L series: [{"date":"2026-07-14","pnl":150.00},...]
    daily_pnl_series      TEXT NOT NULL DEFAULT '[]',

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (portfolio_id, snapshot_date)
);

CREATE INDEX idx_perf_snap_portfolio ON performance_snapshots(portfolio_id);
CREATE INDEX idx_perf_snap_date ON performance_snapshots(snapshot_date);
CREATE INDEX idx_perf_snap_portfolio_date ON performance_snapshots(portfolio_id, snapshot_date DESC);
