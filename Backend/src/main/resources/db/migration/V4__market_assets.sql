CREATE TABLE assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol          VARCHAR(20) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    precision       INTEGER NOT NULL DEFAULT 8,
    provider_source VARCHAR(50) NOT NULL,
    tradable        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_assets_symbol ON assets(symbol);
CREATE INDEX idx_assets_tradable ON assets(tradable);
