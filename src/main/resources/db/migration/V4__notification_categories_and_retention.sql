-- Notification UI support: MB-style tabs + balance-change snapshots.
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS category VARCHAR(32),
    ADD COLUMN IF NOT EXISTS amount BIGINT,
    ADD COLUMN IF NOT EXISTS direction VARCHAR(8),
    ADD COLUMN IF NOT EXISTS balance_after BIGINT;

UPDATE notifications
SET category = CASE
    WHEN type IN ('TOPUP_SUCCESS', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'EXTERNAL_TRANSFER_SENT')
        THEN 'BALANCE_CHANGE'
    WHEN type IN ('NEWS', 'ANNOUNCEMENT', 'SYSTEM_NEWS')
        THEN 'NEWS'
    ELSE 'MY'
END
WHERE category IS NULL;

-- Backfill amount/direction for old transaction-linked balance notifications when possible.
UPDATE notifications n
SET amount = lt.amount
FROM ledger_transactions lt
WHERE n.related_transaction_id = lt.id
  AND n.category = 'BALANCE_CHANGE'
  AND n.amount IS NULL;

UPDATE notifications
SET direction = CASE
    WHEN type IN ('TOPUP_SUCCESS', 'TRANSFER_RECEIVED') THEN 'IN'
    WHEN type IN ('TRANSFER_SENT', 'EXTERNAL_TRANSFER_SENT') THEN 'OUT'
    ELSE direction
END
WHERE category = 'BALANCE_CHANGE'
  AND direction IS NULL;

ALTER TABLE notifications
    ALTER COLUMN category SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notification_user_category_created
    ON notifications(user_id, category, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_created_retention
    ON notifications(created_at);
