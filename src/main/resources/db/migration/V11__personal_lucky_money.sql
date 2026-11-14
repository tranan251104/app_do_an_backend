CREATE TABLE lucky_money_envelopes (
  id UUID PRIMARY KEY REFERENCES ledger_transactions(id),
  sender_user_id UUID NOT NULL REFERENCES users(id),
  receiver_user_id UUID NOT NULL REFERENCES users(id),
  idempotency_key UUID NOT NULL,
  recipient_wallet_code VARCHAR(32) NOT NULL,
  template VARCHAR(32) NOT NULL CHECK (template IN ('PROSPERITY','PEACE','JOY')),
  greeting VARCHAR(200) NOT NULL,
  otp_challenge_id UUID NOT NULL REFERENCES otp_challenges(id),
  otp_expires_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ,
  opened_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(sender_user_id, idempotency_key),
  CHECK (sender_user_id <> receiver_user_id)
);
CREATE INDEX idx_lucky_sender ON lucky_money_envelopes(sender_user_id, created_at DESC);
CREATE INDEX idx_lucky_receiver ON lucky_money_envelopes(receiver_user_id, created_at DESC);
CREATE INDEX idx_lucky_expiry ON lucky_money_envelopes(expires_at) WHERE opened_at IS NULL;
