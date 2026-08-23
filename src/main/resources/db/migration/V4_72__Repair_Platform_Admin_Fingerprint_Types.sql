-- Align the two platform security fingerprints with their JPA String contract.
--
-- V4.69 used fixed-width CHAR(64), while the entities require VARCHAR(64).
-- Existing values are lowercase 64-character SHA-256 hex strings; btrim only
-- removes PostgreSQL's fixed-width padding and does not change the fingerprint.

ALTER TABLE platform_admin_commands
    ALTER COLUMN request_fingerprint TYPE VARCHAR(64)
    USING btrim(request_fingerprint);

ALTER TABLE platform_mfa_challenges
    ALTER COLUMN action_context_hash TYPE VARCHAR(64)
    USING btrim(action_context_hash);
