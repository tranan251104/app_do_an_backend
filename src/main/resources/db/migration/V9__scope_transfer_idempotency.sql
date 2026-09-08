-- Preserve existing requests and their keys while scoping retries to the sender
-- and operation. The persisted transaction/recipient/note are the request payload.
ALTER TABLE transfer_requests ADD COLUMN user_id UUID REFERENCES users(id);

UPDATE transfer_requests tr
SET user_id = w.user_id
FROM ledger_transactions tx
JOIN wallets w ON w.id = tx.sender_wallet_id
WHERE tr.transaction_id = tx.id;

ALTER TABLE transfer_requests ALTER COLUMN user_id SET NOT NULL;
DROP INDEX uq_transfer_user_idem;
CREATE UNIQUE INDEX uq_transfer_user_type_idem
    ON transfer_requests(user_id, recipient_type, idempotency_key);
