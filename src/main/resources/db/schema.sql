CREATE TABLE IF NOT EXISTS app_profile (
    id_profile INTEGER PRIMARY KEY AUTOINCREMENT,
    theme TEXT NOT NULL,
    sync_interval_minutes INTEGER NOT NULL,
    language TEXT NOT NULL,
    notifications_enabled INTEGER NOT NULL CHECK (notifications_enabled IN (0, 1))
);

CREATE TABLE IF NOT EXISTS email_address (
    id_address INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL UNIQUE,
    is_internal INTEGER NOT NULL CHECK (is_internal IN (0, 1))
);

CREATE TABLE IF NOT EXISTS account (
    id_account INTEGER PRIMARY KEY AUTOINCREMENT,
    id_profile INTEGER NOT NULL,
    id_address INTEGER NOT NULL UNIQUE,
    provider TEXT NOT NULL,
    signature TEXT,
    account_name TEXT NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    token_expires_at TEXT NOT NULL,
    FOREIGN KEY (id_profile) REFERENCES app_profile (id_profile) ON DELETE RESTRICT,
    FOREIGN KEY (id_address) REFERENCES email_address (id_address) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS mail (
    id_mail INTEGER PRIMARY KEY AUTOINCREMENT,
    id_account INTEGER NOT NULL,
    id_sender_address INTEGER NOT NULL,
    id_reply_to_mail INTEGER,
    server_message_id TEXT NOT NULL,
    subject TEXT,
    body_plain_text TEXT,
    body_html TEXT,
    date_received TEXT NOT NULL,
    FOREIGN KEY (id_account) REFERENCES account (id_account) ON DELETE CASCADE,
    FOREIGN KEY (id_sender_address) REFERENCES email_address (id_address) ON DELETE RESTRICT,
    FOREIGN KEY (id_reply_to_mail) REFERENCES mail (id_mail) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS draft (
    id_draft INTEGER PRIMARY KEY AUTOINCREMENT,
    id_account INTEGER NOT NULL,
    subject TEXT,
    body_plain_text TEXT,
    last_edited TEXT NOT NULL,
    FOREIGN KEY (id_account) REFERENCES account (id_account) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS attachment (
    id_attachment INTEGER PRIMARY KEY AUTOINCREMENT,
    id_mail INTEGER NOT NULL,
    file_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    file_path TEXT,
    FOREIGN KEY (id_mail) REFERENCES mail (id_mail) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS label (
    id_label INTEGER PRIMARY KEY AUTOINCREMENT,
    id_account INTEGER NOT NULL,
    name TEXT NOT NULL,
    FOREIGN KEY (id_account) REFERENCES account (id_account) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mail_label (
    id_mail INTEGER NOT NULL,
    id_label INTEGER NOT NULL,
    is_read INTEGER NOT NULL CHECK (is_read IN (0, 1)),
    PRIMARY KEY (id_mail, id_label),
    FOREIGN KEY (id_mail) REFERENCES mail (id_mail) ON DELETE CASCADE,
    FOREIGN KEY (id_label) REFERENCES label (id_label) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mail_address (
    id_mail INTEGER NOT NULL,
    id_address INTEGER NOT NULL,
    recipient_type TEXT NOT NULL,
    PRIMARY KEY (id_mail, id_address),
    FOREIGN KEY (id_mail) REFERENCES mail (id_mail) ON DELETE CASCADE,
    FOREIGN KEY (id_address) REFERENCES email_address (id_address) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS draft_address (
    id_draft INTEGER NOT NULL,
    id_address INTEGER NOT NULL,
    recipient_type TEXT NOT NULL,
    PRIMARY KEY (id_draft, id_address),
    FOREIGN KEY (id_draft) REFERENCES draft (id_draft) ON DELETE CASCADE,
    FOREIGN KEY (id_address) REFERENCES email_address (id_address) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mail_id_account ON mail (id_account);
CREATE INDEX IF NOT EXISTS idx_draft_id_account ON draft (id_account);
CREATE INDEX IF NOT EXISTS idx_attachment_id_mail ON attachment (id_mail);
CREATE INDEX IF NOT EXISTS idx_label_id_account ON label (id_account);

