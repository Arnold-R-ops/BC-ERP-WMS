-- Platform only. Never guess a browser for pre-existing sessions: those rows fail closed
-- in the new application and require a new MFA login. Audit/history remains untouched.
ALTER TABLE platform_sessions ADD COLUMN browser_binding_hash varchar(64);
ALTER TABLE platform_sessions ADD CONSTRAINT ck_platform_session_browser_hash
    CHECK (browser_binding_hash IS NULL OR browser_binding_hash ~ '^[0-9a-f]{64}$');
CREATE INDEX ix_platform_sessions_browser_scope
    ON platform_sessions(platform_user_id, browser_binding_hash) WHERE revoked_at IS NULL;
COMMENT ON COLUMN platform_sessions.browser_binding_hash IS
    'SHA-256 of server-signed browser grouping cookie; not a device fingerprint or authentication credential';
