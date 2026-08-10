-- Built-in simple approval template and lifecycle state for custom permission packages.

CREATE TABLE IF NOT EXISTS sys_approval_template (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    template_code VARCHAR(50) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    object_type VARCHAR(50) NOT NULL,
    approval_mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    system_defined BOOLEAN NOT NULL DEFAULT TRUE,
    version_no INTEGER NOT NULL DEFAULT 1,
    description VARCHAR(500),
    config_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_approval_template_company_code UNIQUE (company_id, template_code),
    CONSTRAINT ck_approval_template_mode CHECK (approval_mode IN ('SIMPLE', 'FORMAL')),
    CONSTRAINT ck_approval_template_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_approval_template_version CHECK (version_no > 0)
);

INSERT INTO sys_approval_template (
    company_id,
    template_code,
    template_name,
    object_type,
    approval_mode,
    status,
    system_defined,
    version_no,
    description,
    config_json
)
VALUES (
    1,
    'ROLE_PACKAGE_SIMPLE_APPROVAL',
    '岗位权限包简易审批',
    'ROLE_PACKAGE',
    'SIMPLE',
    'ACTIVE',
    TRUE,
    1,
    '单级复核：普通风险允许提交人复核；高风险必须由另一名超级管理员复核。',
    '{"stages":1,"normalRiskSelfReview":true,"highRiskRequiresDifferentReviewer":true}'
)
ON CONFLICT (company_id, template_code) DO UPDATE
SET template_name = EXCLUDED.template_name,
    object_type = EXCLUDED.object_type,
    approval_mode = EXCLUDED.approval_mode,
    status = EXCLUDED.status,
    system_defined = TRUE,
    version_no = EXCLUDED.version_no,
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE sys_role
    ADD COLUMN IF NOT EXISTS approval_template_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS review_submitted_by BIGINT,
    ADD COLUMN IF NOT EXISTS review_submitted_by_username VARCHAR(100),
    ADD COLUMN IF NOT EXISTS review_submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reviewed_by BIGINT,
    ADD COLUMN IF NOT EXISTS reviewed_by_username VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_comment VARCHAR(500);

UPDATE sys_role
SET approval_template_code = CASE
        WHEN role_type = 'CUSTOM' THEN 'ROLE_PACKAGE_SIMPLE_APPROVAL'
        ELSE NULL
    END,
    review_status = CASE
        WHEN role_type = 'SYSTEM' OR status = 'ACTIVE' THEN 'APPROVED'
        ELSE 'DRAFT'
    END;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_review_status'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_review_status
            CHECK (review_status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_custom_approval_template'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_custom_approval_template
            CHECK (role_type <> 'CUSTOM' OR approval_template_code IS NOT NULL);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_custom_active_approved'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_custom_active_approved
            CHECK (role_type <> 'CUSTOM' OR status <> 'ACTIVE' OR review_status = 'APPROVED');
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_approval_template_lookup
    ON sys_approval_template(company_id, object_type, approval_mode, status);

CREATE INDEX IF NOT EXISTS idx_role_review_queue
    ON sys_role(company_id, role_type, review_status, status);

COMMENT ON TABLE sys_approval_template IS
    'Protected approval templates. FORMAL mode is reserved but not implemented in this release.';
COMMENT ON COLUMN sys_role.review_status IS
    'Permission-package governance state: DRAFT, PENDING_REVIEW, or APPROVED.';
COMMENT ON COLUMN sys_role.approval_template_code IS
    'Approval template bound to a custom permission package.';

