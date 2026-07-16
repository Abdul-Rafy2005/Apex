-- V9: Notifications table + leaderboard visibility preference on users

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT NOT NULL,
    reference_id    UUID,
    reference_type  VARCHAR(50),
    read_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id_created_at ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_user_id_read_at ON notifications(user_id, read_at);

ALTER TABLE users ADD COLUMN leaderboard_visible BOOLEAN NOT NULL DEFAULT true;
