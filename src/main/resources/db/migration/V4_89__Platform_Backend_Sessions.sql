-- Platform only. Existing bearer tokens without a registered session fail closed.
-- No tenant session, audit, approval or business data is changed by this migration.
CREATE TABLE platform_sessions (
    session_id uuid PRIMARY KEY,
    platform_user_id bigint NOT NULL REFERENCES platform_users(id),
    security_version bigint NOT NULL CHECK (security_version > 0),
    started_at timestamptz NOT NULL,
    last_activity_at timestamptz NOT NULL,
    idle_expires_at timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT ck_platform_session_window CHECK (
        last_activity_at >= started_at AND
        absolute_expires_at > started_at AND
        absolute_expires_at <= started_at + interval '12 hours' AND
        idle_expires_at <= absolute_expires_at AND
        idle_expires_at <= last_activity_at + interval '30 minutes'
    )
);
CREATE INDEX ix_platform_sessions_user ON platform_sessions(platform_user_id);
CREATE INDEX ix_platform_sessions_cleanup ON platform_sessions
    (LEAST(idle_expires_at, absolute_expires_at, revoked_at));
COMMENT ON TABLE platform_sessions IS
    'Platform login enforcement only; terminal rows retained 7 days, then bounded cleanup; audit retained independently';
