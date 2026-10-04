-- Only P3 receipt-derived cache copies; historical poll/reconciliation records are untouched.
ALTER TABLE channel_raw_events ADD COLUMN source_inbox_id BIGINT;
CREATE INDEX idx_raw_event_p3_receipt ON channel_raw_events(source_inbox_id) WHERE source_inbox_id IS NOT NULL;
CREATE FUNCTION bcwms_expire_webhook_copy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.source_inbox_id IS NOT NULL AND EXISTS (
    SELECT 1 FROM tenant_webhook_inbox i WHERE i.id=NEW.source_inbox_id
      AND i.tenant_id=NEW.company_id AND i.expires_at<=CURRENT_TIMESTAMP AND NOT i.legal_hold
  ) THEN
    NEW.payload='{}'::jsonb;
    NEW.error_message='WEBHOOK_RETENTION_EXPIRED';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_p3_webhook_copy_retention BEFORE INSERT OR UPDATE ON channel_raw_events
FOR EACH ROW EXECUTE FUNCTION bcwms_expire_webhook_copy();
ALTER TABLE tenant_security_connections ADD COLUMN reconciliation_evidence TEXT;
ALTER TABLE tenant_security_connections ADD COLUMN reconciliation_report_id BIGINT;
ALTER TABLE tenant_security_connections ADD COLUMN reconciled_at TIMESTAMPTZ;
ALTER TABLE tenant_security_alerts ADD COLUMN severity INTEGER NOT NULL DEFAULT 1 CHECK(severity BETWEEN 1 AND 3);
ALTER TABLE tenant_security_alerts ADD COLUMN resolution_evidence TEXT;
ALTER TABLE tenant_security_alerts ADD COLUMN resolved_by BIGINT REFERENCES platform_users(id);
