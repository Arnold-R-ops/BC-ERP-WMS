-- Fingerprints are only used to prevent old compromised credentials being trusted again.
ALTER TABLE tenant_security_connections ADD COLUMN original_signing_fingerprint VARCHAR(64);
ALTER TABLE tenant_security_connections ADD COLUMN original_access_fingerprint VARCHAR(64);
ALTER TABLE tenants ADD COLUMN security_resumed_at TIMESTAMPTZ;
