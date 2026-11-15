-- Only for simulated withdrawals. These accounts are self-declared by users,
-- NOT independently verified with a bank. Never enable real payouts with this table.
CREATE TABLE linked_bank_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    bank_bin VARCHAR(6) NOT NULL,
    bank_name VARCHAR(128) NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    account_name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DEMO_LINKED'
        CHECK (status IN ('DEMO_LINKED', 'REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_linked_bank_user_status ON linked_bank_accounts(user_id, status);
CREATE UNIQUE INDEX uq_linked_bank_active_account
    ON linked_bank_accounts(user_id, bank_bin, account_number)
    WHERE status = 'DEMO_LINKED';
