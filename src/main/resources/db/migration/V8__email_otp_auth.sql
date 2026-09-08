ALTER TABLE otp_challenges
  ADD COLUMN email_normalized VARCHAR(160);

ALTER TABLE otp_challenges
  DROP CONSTRAINT chk_otp_subject_present,
  ADD CONSTRAINT chk_otp_subject_present
    CHECK (user_id IS NOT NULL OR phone_normalized IS NOT NULL OR email_normalized IS NOT NULL),
  ADD CONSTRAINT chk_otp_email_normalized
    CHECK (email_normalized IS NULL OR email_normalized = lower(btrim(email_normalized)));

CREATE INDEX idx_otp_email_purpose_created
  ON otp_challenges(email_normalized, purpose, created_at DESC)
  WHERE email_normalized IS NOT NULL;
