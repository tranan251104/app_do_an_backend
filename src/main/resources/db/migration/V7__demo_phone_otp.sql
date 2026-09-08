-- DEV/DEMO ONLY: anonymous OTP challenges used before an AnPay user exists.
-- These challenges prove access to the configured delivery channel, not ownership of a SIM.
ALTER TABLE otp_challenges
  ALTER COLUMN user_id DROP NOT NULL,
  ADD COLUMN phone_normalized VARCHAR(32),
  ADD COLUMN resend_count INT NOT NULL DEFAULT 0;

ALTER TABLE otp_challenges
  ADD CONSTRAINT chk_otp_subject_present
    CHECK (user_id IS NOT NULL OR phone_normalized IS NOT NULL),
  ADD CONSTRAINT chk_otp_phone_normalized_e164
    CHECK (phone_normalized IS NULL OR phone_normalized ~ '^\+84[1-9][0-9]{7,9}$'),
  ADD CONSTRAINT chk_otp_resend_count
    CHECK (resend_count >= 0);

CREATE INDEX idx_otp_phone_purpose_created
  ON otp_challenges(phone_normalized, purpose, created_at DESC)
  WHERE phone_normalized IS NOT NULL;
