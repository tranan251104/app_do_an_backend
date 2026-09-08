CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id UUID PRIMARY KEY,
  firebase_uid VARCHAR(128) UNIQUE,
  email VARCHAR(320) UNIQUE,
  phone VARCHAR(32) UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(160) NOT NULL,
  date_of_birth DATE,
  gender VARCHAR(32),
  address VARCHAR(500),
  id_card_encrypted TEXT,
  id_card_last4 VARCHAR(4),
  status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','LOCKED','CLOSED')),
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (email IS NOT NULL OR phone IS NOT NULL)
);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  family_id UUID NOT NULL,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  device_id VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_family ON refresh_tokens(family_id);

CREATE TABLE wallets (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE REFERENCES users(id),
  wallet_code VARCHAR(32) NOT NULL UNIQUE,
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  available_balance BIGINT NOT NULL DEFAULT 0 CHECK (available_balance >= 0),
  held_balance BIGINT NOT NULL DEFAULT 0 CHECK (held_balance >= 0),
  status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_wallet_code ON wallets(wallet_code);
CREATE INDEX idx_wallet_status ON wallets(status);

CREATE TABLE ledger_accounts (
  id UUID PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  owner_type VARCHAR(32) NOT NULL CHECK (owner_type IN ('USER','SYSTEM','PARTNER','BANK_CLEARING')),
  owner_id UUID,
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX idx_ledger_owner ON ledger_accounts(owner_type, owner_id);

CREATE TABLE ledger_transactions (
  id UUID PRIMARY KEY,
  reference VARCHAR(64) NOT NULL UNIQUE,
  type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  amount BIGINT NOT NULL CHECK (amount > 0),
  fee BIGINT NOT NULL DEFAULT 0 CHECK (fee >= 0),
  description VARCHAR(500),
  sender_wallet_id UUID REFERENCES wallets(id),
  receiver_wallet_id UUID REFERENCES wallets(id),
  provider VARCHAR(64),
  provider_reference VARCHAR(128),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
CREATE INDEX idx_lt_created ON ledger_transactions(created_at DESC);
CREATE INDEX idx_lt_status ON ledger_transactions(status);
CREATE INDEX idx_lt_sender ON ledger_transactions(sender_wallet_id);
CREATE INDEX idx_lt_receiver ON ledger_transactions(receiver_wallet_id);

CREATE TABLE ledger_entries (
  id UUID PRIMARY KEY,
  ledger_transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
  ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
  delta_amount BIGINT NOT NULL CHECK (delta_amount <> 0),
  balance_after BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_le_tx ON ledger_entries(ledger_transaction_id);
CREATE INDEX idx_le_account ON ledger_entries(ledger_account_id);

CREATE OR REPLACE FUNCTION prevent_completed_ledger_entry_mutation() RETURNS trigger AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM ledger_transactions t WHERE t.id = OLD.ledger_transaction_id AND t.status = 'COMPLETED') THEN
    RAISE EXCEPTION 'completed ledger entries are immutable';
  END IF;
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_no_update_completed_entry BEFORE UPDATE OR DELETE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION prevent_completed_ledger_entry_mutation();

CREATE TABLE otp_challenges (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  purpose VARCHAR(64) NOT NULL,
  context_id UUID,
  code_hash VARCHAR(128) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  resend_available_at TIMESTAMPTZ NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  max_attempts INT NOT NULL DEFAULT 5 CHECK (max_attempts > 0),
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_user_purpose ON otp_challenges(user_id,purpose);

CREATE TABLE transfer_requests (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL UNIQUE REFERENCES ledger_transactions(id),
  recipient_type VARCHAR(32) NOT NULL,
  recipient_reference VARCHAR(128) NOT NULL,
  note VARCHAR(500),
  idempotency_key UUID NOT NULL,
  otp_challenge_id UUID REFERENCES otp_challenges(id),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(transaction_id, idempotency_key)
);
CREATE UNIQUE INDEX uq_transfer_user_idem ON transfer_requests(idempotency_key);

CREATE TABLE payment_intents (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  transaction_id UUID NOT NULL UNIQUE REFERENCES ledger_transactions(id),
  provider VARCHAR(32) NOT NULL,
  order_code BIGINT NOT NULL UNIQUE,
  idempotency_key UUID NOT NULL,
  amount BIGINT NOT NULL CHECK(amount > 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  checkout_url TEXT,
  payment_link_id VARCHAR(128),
  status VARCHAR(32) NOT NULL,
  provider_reference VARCHAR(128),
  expires_at TIMESTAMPTZ,
  paid_at TIMESTAMPTZ,
  raw_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_pi_user_idem ON payment_intents(user_id,idempotency_key);
CREATE INDEX idx_pi_user ON payment_intents(user_id);
CREATE INDEX idx_pi_status ON payment_intents(status);
CREATE INDEX idx_pi_order ON payment_intents(order_code);

CREATE TABLE webhook_events (
  id UUID PRIMARY KEY,
  provider VARCHAR(32) NOT NULL,
  provider_event_key VARCHAR(255) NOT NULL,
  signature_valid BOOLEAN NOT NULL,
  payload_hash VARCHAR(128) NOT NULL,
  processed_at TIMESTAMPTZ,
  processing_result VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(provider, provider_event_key)
);

CREATE TABLE beneficiaries (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  type VARCHAR(16) NOT NULL CHECK(type IN ('ANPAY','BANK')),
  bank_bin VARCHAR(32),
  bank_name VARCHAR(128),
  account_number VARCHAR(64) NOT NULL,
  account_name VARCHAR(160),
  nickname VARCHAR(160),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_beneficiary_user ON beneficiaries(user_id);

CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  related_transaction_id UUID REFERENCES ledger_transactions(id),
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user_created ON notifications(user_id, created_at DESC);

CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id UUID NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  payload JSONB NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_status_available ON outbox_events(status, available_at);

CREATE TABLE audit_logs (
  id UUID PRIMARY KEY,
  actor_user_id UUID REFERENCES users(id),
  action VARCHAR(128) NOT NULL,
  resource_type VARCHAR(128) NOT NULL,
  resource_id VARCHAR(128),
  ip_address VARCHAR(64),
  user_agent TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);

CREATE TABLE catalog_providers (
  id UUID PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  category VARCHAR(64) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE catalog_products (
  id UUID PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  provider_id UUID NOT NULL REFERENCES catalog_providers(id),
  category VARCHAR(64) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description TEXT,
  price BIGINT NOT NULL DEFAULT 0 CHECK(price >= 0),
  discount BIGINT NOT NULL DEFAULT 0 CHECK(discount >= 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ
);
CREATE INDEX idx_catalog_product_provider ON catalog_products(provider_id);
CREATE INDEX idx_catalog_product_category ON catalog_products(category);

CREATE TABLE promotions (
  id UUID PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  category VARCHAR(64),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE partner_links (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  web_url TEXT,
  android_deep_link TEXT,
  ios_deep_link TEXT,
  fallback_url TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE financial_leads (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  type VARCHAR(64) NOT NULL,
  product_code VARCHAR(64),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(32) NOT NULL DEFAULT 'NEW',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
