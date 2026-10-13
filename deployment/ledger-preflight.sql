-- Read-only inspection. Run against a restored copy first, then the target server
-- using an appropriately authorized read-only operator. Do not repair/merge rows here.
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;

SELECT identity_id, count(*) AS current_memberships
FROM tenant_memberships
WHERE status IN ('INVITED', 'ACTIVE', 'SUSPENDED')
GROUP BY identity_id HAVING count(*) > 1;

-- Every active production tenant must have a current product subscription.
SELECT t.id, t.slug, s.status AS subscription_status, s.trial_ends_at
FROM tenants t LEFT JOIN tenant_subscriptions s ON s.tenant_id = t.id
WHERE t.status = 'ACTIVE'
  AND (s.id IS NULL OR s.status NOT IN ('ACTIVE', 'FREE', 'TRIALING')
       OR (s.status = 'TRIALING' AND (s.trial_ends_at IS NULL OR s.trial_ends_at <= now())));

-- Default launch base; replace this literal if the agreed deployment base changes.
SELECT t.id, t.slug
FROM tenants t
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM tenant_domains d WHERE d.tenant_id=t.id
      AND d.status='ACTIVE' AND d.hostname=t.slug || '.ledger.fongvo.com');

-- Legacy username/password access remains; verified email discovery needs a link.
SELECT u.company_id, count(*) AS users_without_verified_identity_link
FROM users u LEFT JOIN tenant_memberships m
  ON m.tenant_id=u.company_id AND m.tenant_user_id=u.id
WHERE u.enabled=true AND u.is_deleted=false AND m.id IS NULL
GROUP BY u.company_id;

SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname=current_user;

-- Review legacy usernames that collide with another verified email identity.
-- Preserve a usable login before switching; do not auto-link by an unverified email.
SELECT u.company_id, count(*) AS conflicting_email_usernames
FROM users u JOIN user_identities i ON lower(trim(u.username))=i.normalized_email
LEFT JOIN tenant_memberships m ON m.identity_id=i.id AND m.tenant_id=u.company_id
WHERE u.enabled=true AND u.is_deleted=false
  AND (m.id IS NULL OR m.tenant_user_id<>u.id)
GROUP BY u.company_id;
