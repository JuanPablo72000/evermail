CREATE TABLE IF NOT EXISTS account (
    id_account TEXT PRIMARY KEY,
    provider TEXT NOT NULL CHECK (provider IN ('GOOGLE', 'MICROSOFT')),
    provider_subject TEXT NOT NULL,
    email TEXT NOT NULL,
    display_name TEXT,
    key_ref TEXT NOT NULL UNIQUE,
    access_token_cipher TEXT,
    refresh_token_cipher TEXT,
    token_expires_at INTEGER,
    status TEXT NOT NULL CHECK (status IN ('PROVISIONING', 'ACTIVE', 'REAUTH_REQUIRED', 'DISCONNECTING')),
    UNIQUE (provider, provider_subject),
    CHECK (
        status <> 'ACTIVE'
        OR (
            access_token_cipher IS NOT NULL
            AND refresh_token_cipher IS NOT NULL
            AND token_expires_at IS NOT NULL
        )
    )
);

CREATE TABLE IF NOT EXISTS inbox_state (
    id_account TEXT PRIMARY KEY REFERENCES account(id_account) ON DELETE CASCADE,
    uid_validity INTEGER NOT NULL CHECK (uid_validity > 0),
    oldest_fetched_uid INTEGER,
    newest_fetched_uid INTEGER,
    has_more INTEGER NOT NULL CHECK (has_more IN (0, 1)),
    last_synced_at INTEGER
);

CREATE TABLE IF NOT EXISTS outbox_message (
    id_outbox TEXT PRIMARY KEY,
    id_account TEXT NOT NULL REFERENCES account(id_account) ON DELETE CASCADE,
    message_id TEXT NOT NULL UNIQUE,
    subject TEXT NOT NULL,
    body_cipher TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'SENDING', 'ACCEPTED', 'RECORDED', 'FAILED', 'UNKNOWN')),
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_error_code TEXT
);

CREATE TABLE IF NOT EXISTS mail (
    id_mail TEXT PRIMARY KEY,
    id_account TEXT NOT NULL REFERENCES account(id_account) ON DELETE CASCADE,
    direction TEXT NOT NULL CHECK (direction IN ('INBOX', 'SENT')),
    remote_uid INTEGER,
    uid_validity INTEGER,
    message_id TEXT,
    outbound_id TEXT UNIQUE REFERENCES outbox_message(id_outbox) ON DELETE RESTRICT,
    sender_email TEXT,
    sender_name TEXT,
    subject TEXT NOT NULL,
    occurred_at INTEGER NOT NULL,
    body_cipher TEXT,
    body_format INTEGER NOT NULL DEFAULT 0 CHECK (body_format IN (0, 1)),
    is_read INTEGER NOT NULL CHECK (is_read IN (0, 1)),
    CHECK (
        (direction = 'INBOX' AND remote_uid > 0 AND uid_validity > 0 AND outbound_id IS NULL)
        OR (direction = 'SENT' AND remote_uid IS NULL AND uid_validity IS NULL AND outbound_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mail_remote
    ON mail (id_account, uid_validity, remote_uid)
    WHERE direction = 'INBOX';

CREATE INDEX IF NOT EXISTS idx_mail_page
    ON mail (id_account, direction, remote_uid DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_recovery
    ON outbox_message (id_account, state, created_at);

CREATE TABLE IF NOT EXISTS mail_recipient (
    id_mail TEXT NOT NULL REFERENCES mail(id_mail) ON DELETE CASCADE,
    address_key TEXT NOT NULL,
    email TEXT NOT NULL,
    display_name TEXT,
    recipient_type TEXT NOT NULL CHECK (recipient_type IN ('TO', 'CC', 'BCC')),
    PRIMARY KEY (id_mail, address_key)
);

CREATE TABLE IF NOT EXISTS outbox_recipient (
    id_outbox TEXT NOT NULL REFERENCES outbox_message(id_outbox) ON DELETE CASCADE,
    address_key TEXT NOT NULL,
    email TEXT NOT NULL,
    recipient_type TEXT NOT NULL CHECK (recipient_type IN ('TO', 'CC', 'BCC')),
    PRIMARY KEY (id_outbox, address_key)
);
