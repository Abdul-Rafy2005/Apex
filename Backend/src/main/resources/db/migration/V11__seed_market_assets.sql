INSERT INTO assets (symbol, name, precision, provider_source, tradable)
VALUES
    ('BTC', 'Bitcoin', 8, 'bitcoin', true),
    ('ETH', 'Ethereum', 8, 'ethereum', true)
ON CONFLICT (symbol) DO NOTHING;
