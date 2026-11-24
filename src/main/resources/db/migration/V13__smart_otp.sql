-- Smart OTP demo: per-device Ed25519 PUBLIC keys; never store PIN or private keys in PostgreSQL.
CREATE TABLE smart_otp_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    public_key VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACTIVE','REVOKED')),
    enrollment_otp_id UUID REFERENCES otp_challenges(id),
    enrollment_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_smart_otp_devices_user ON smart_otp_devices(user_id, status);
CREATE UNIQUE INDEX ux_smart_otp_active_user ON smart_otp_devices(user_id) WHERE status='ACTIVE';
CREATE TABLE smart_otp_transaction_challenges (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL UNIQUE REFERENCES ledger_transactions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES smart_otp_devices(id),
    nonce VARCHAR(64) NOT NULL,
    transaction_digest VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_smart_otp_challenges_user ON smart_otp_transaction_challenges(user_id);

-- Previous lucky money flow required legacy email OTP; Smart OTP does not create such a challenge.
ALTER TABLE lucky_money_envelopes ALTER COLUMN otp_challenge_id DROP NOT NULL;
