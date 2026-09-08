-- Firebase Cloud Messaging device registrations.
-- One user can own multiple app installations; one FCM token belongs to one installation.
CREATE TABLE push_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fcm_token VARCHAR(2048) NOT NULL UNIQUE,
    platform VARCHAR(16) NOT NULL CHECK (platform IN ('ANDROID','IOS','WEB')),
    device_id VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_push_devices_user_enabled
    ON push_devices(user_id, enabled);

CREATE INDEX idx_push_devices_last_seen
    ON push_devices(last_seen_at DESC);
