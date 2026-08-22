-- Tenant-safe channel routing and webhook idempotency.

ALTER TABLE integration_configs
    ADD COLUMN IF NOT EXISTS canonical_store_identifier VARCHAR(255);

UPDATE integration_configs
SET canonical_store_identifier = LOWER(
    TRIM(TRAILING '/' FROM REGEXP_REPLACE(BTRIM(store_url), '^https?://', '', 'i'))
)
WHERE canonical_store_identifier IS NULL;

ALTER TABLE integration_configs
    ALTER COLUMN canonical_store_identifier SET NOT NULL;

ALTER TABLE integration_configs
    DROP CONSTRAINT IF EXISTS ck_integration_configs_store_canonical;

ALTER TABLE integration_configs
    ADD CONSTRAINT ck_integration_configs_store_canonical CHECK (
        canonical_store_identifier = LOWER(BTRIM(canonical_store_identifier))
        AND canonical_store_identifier !~ '^https?://'
        AND canonical_store_identifier !~ '/$'
        AND canonical_store_identifier <> ''
    );

-- A channel endpoint may route to exactly one active company. Ambiguous
-- historical active data is disabled and must be reviewed rather than being
-- routed to an arbitrary company.
WITH duplicate_routes AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY platform, canonical_store_identifier
               ORDER BY id
           ) AS route_rank
    FROM integration_configs
    WHERE is_active = TRUE
)
UPDATE integration_configs config
SET is_active = FALSE,
    updated_at = CURRENT_TIMESTAMP
FROM duplicate_routes duplicate
WHERE config.id = duplicate.id
  AND duplicate.route_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_integration_configs_active_store_route
    ON integration_configs (platform, canonical_store_identifier)
    WHERE is_active = TRUE;

-- Minimal control-plane route used before a tenant RLS context exists. It is
-- deliberately separate from tenant-owned integration_configs: webhook code
-- may read only this routing secret globally, then must enter tenant context
-- before loading any integration/business data.
CREATE TABLE IF NOT EXISTS channel_webhook_routes (
    integration_config_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    platform VARCHAR(50) NOT NULL,
    canonical_store_identifier VARCHAR(255) NOT NULL,
    signing_secret VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_channel_webhook_routes_config
        FOREIGN KEY (integration_config_id) REFERENCES integration_configs(id) ON DELETE CASCADE,
    CONSTRAINT fk_channel_webhook_routes_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_channel_webhook_routes_tenant_positive CHECK (tenant_id > 0),
    CONSTRAINT ck_channel_webhook_routes_store_normalized CHECK (
        canonical_store_identifier = LOWER(BTRIM(canonical_store_identifier))
        AND canonical_store_identifier !~ '^https?://'
        AND canonical_store_identifier !~ '/$'
        AND canonical_store_identifier <> ''
    )
);

INSERT INTO channel_webhook_routes (
    integration_config_id, tenant_id, platform,
    canonical_store_identifier, signing_secret, active
)
SELECT id, company_id, platform, canonical_store_identifier, client_secret, is_active
FROM integration_configs
WHERE client_secret IS NOT NULL
  AND BTRIM(client_secret) <> ''
ON CONFLICT (integration_config_id)
DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    platform = EXCLUDED.platform,
    canonical_store_identifier = EXCLUDED.canonical_store_identifier,
    signing_secret = EXCLUDED.signing_secret,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_channel_webhook_routes_active_store
    ON channel_webhook_routes (platform, canonical_store_identifier)
    WHERE active = TRUE;

-- Preserve the newest row as the dedup authority when historical redeliveries
-- were stored more than once before the unique guard existed.
WITH duplicate_webhooks AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY company_id, webhook_event_id
               ORDER BY id DESC
           ) AS duplicate_rank
    FROM channel_raw_events
    WHERE webhook_event_id IS NOT NULL
)
UPDATE channel_raw_events event
SET webhook_event_id = NULL
FROM duplicate_webhooks duplicate
WHERE event.id = duplicate.id
  AND duplicate.duplicate_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_channel_raw_events_company_webhook_id
    ON channel_raw_events (company_id, webhook_event_id)
    WHERE webhook_event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_integration_configs_company_active
    ON integration_configs (company_id, platform, is_active);
