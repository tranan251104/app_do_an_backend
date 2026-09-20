-- Metadata for actionable/proactive notifications.
-- Existing rows stay compatible through safe defaults.
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_data TEXT,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_notification_user_type_created
    ON notifications(user_id, type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_expires_at
    ON notifications(expires_at)
    WHERE expires_at IS NOT NULL;
