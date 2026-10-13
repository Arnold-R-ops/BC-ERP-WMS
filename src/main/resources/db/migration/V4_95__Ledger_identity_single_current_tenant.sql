-- Ledger-local identity membership: one current tenant per verified identity.
-- Historical revoked memberships remain available for audit. Existing conflicting
-- memberships must be reviewed before migration; never silently merge accounts.
CREATE UNIQUE INDEX uk_ledger_identity_current_membership
    ON tenant_memberships(identity_id)
    WHERE status IN ('INVITED', 'ACTIVE', 'SUSPENDED');
