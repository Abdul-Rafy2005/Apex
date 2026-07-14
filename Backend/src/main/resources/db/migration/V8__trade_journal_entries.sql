CREATE TABLE trade_journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    snapshot_id UUID NOT NULL REFERENCES performance_snapshots(id),
    entry_date DATE NOT NULL,
    narrative_text TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT trade_journal_entries_user_date_unique UNIQUE (user_id, entry_date)
);

CREATE INDEX idx_trade_journal_entries_user_id ON trade_journal_entries(user_id);
CREATE INDEX idx_trade_journal_entries_entry_date ON trade_journal_entries(entry_date);
