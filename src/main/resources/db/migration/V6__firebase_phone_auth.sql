ALTER TABLE users
  ADD COLUMN phone_normalized VARCHAR(32),
  ADD COLUMN firebase_phone_uid VARCHAR(128),
  ADD COLUMN phone_verified_at TIMESTAMPTZ;

WITH cleaned AS (
  SELECT id, regexp_replace(phone, '[-[:space:]().]', '', 'g') AS value
  FROM users
  WHERE phone IS NOT NULL
)
UPDATE users u
SET phone_normalized = CASE
  WHEN c.value ~ '^\+84[1-9][0-9]{7,9}$' THEN c.value
  WHEN c.value ~ '^84[1-9][0-9]{7,9}$' THEN '+' || c.value
  WHEN c.value ~ '^0[1-9][0-9]{7,9}$' THEN '+84' || substring(c.value FROM 2)
  ELSE NULL
END
FROM cleaned c
WHERE c.id = u.id;

UPDATE users
SET phone_verified_at = updated_at
WHERE phone_verified = TRUE
  AND phone_verified_at IS NULL;

CREATE UNIQUE INDEX ux_users_phone_normalized
  ON users(phone_normalized)
  WHERE phone_normalized IS NOT NULL;

CREATE UNIQUE INDEX ux_users_firebase_phone_uid
  ON users(firebase_phone_uid)
  WHERE firebase_phone_uid IS NOT NULL;

ALTER TABLE users
  ADD CONSTRAINT chk_users_phone_normalized_e164
  CHECK (phone_normalized IS NULL OR phone_normalized ~ '^\+84[1-9][0-9]{7,9}$');
