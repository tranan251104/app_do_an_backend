-- Indexes used by paginated transaction history / filtering / notification badge.
CREATE INDEX IF NOT EXISTS idx_lt_type_created
    ON ledger_transactions(type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lt_status_created
    ON ledger_transactions(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lt_sender_created
    ON ledger_transactions(sender_wallet_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lt_receiver_created
    ON ledger_transactions(receiver_wallet_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pi_user_created
    ON payment_intents(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_user_unread
    ON notifications(user_id, created_at DESC)
    WHERE read_at IS NULL;
