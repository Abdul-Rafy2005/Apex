-- Replace native PostgreSQL enum types with VARCHAR to match Hibernate @Enumerated(EnumType.STRING)
-- Same pattern as V3 for user_role

-- orders.side
ALTER TABLE orders ALTER COLUMN side DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN side TYPE VARCHAR(20) USING side::text;
ALTER TABLE orders ALTER COLUMN side SET DEFAULT 'MARKET';

-- orders.type
ALTER TABLE orders ALTER COLUMN type DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN type TYPE VARCHAR(20) USING type::text;
ALTER TABLE orders ALTER COLUMN type SET DEFAULT 'MARKET';

-- orders.status
ALTER TABLE orders ALTER COLUMN status DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN status TYPE VARCHAR(20) USING status::text;
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING';

-- trades.side
ALTER TABLE trades ALTER COLUMN side TYPE VARCHAR(10) USING side::text;

-- Drop enum types
DROP TYPE IF EXISTS order_side;
DROP TYPE IF EXISTS order_type;
DROP TYPE IF EXISTS order_status;
