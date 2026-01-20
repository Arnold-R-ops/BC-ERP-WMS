-- V3.3: Multi-Role Migration
-- Purpose: Upgrade single-role system to multi-role RBAC with identity switching
-- Author: BC ERP-WMS Team
-- Date: 2026-01-20

-- ============================================================================
-- STEP 1: Migrate existing users to sys_user_role table
-- ============================================================================

-- Migrate ADMIN users to SUPER_ADMIN role
DO $$
DECLARE
    super_admin_role_id BIGINT;
BEGIN
    -- Get SUPER_ADMIN role ID
    SELECT id INTO super_admin_role_id FROM sys_role WHERE role_code = 'SUPER_ADMIN';

    IF super_admin_role_id IS NULL THEN
        RAISE EXCEPTION 'SUPER_ADMIN role not found in sys_role table';
    END IF;

    -- Migrate users with ADMIN role
    INSERT INTO sys_user_role (user_id, role_id, assigned_by, assigned_at)
    SELECT
        u.id,
        super_admin_role_id,
        u.id, -- Self-assigned for migration
        NOW()
    FROM users u
    WHERE u.role = 'ADMIN'
    ON CONFLICT (user_id, role_id) DO NOTHING;

    RAISE NOTICE 'Migrated ADMIN users to SUPER_ADMIN role';
END $$;

-- Migrate STAFF users to WAREHOUSE_ADMIN role
DO $$
DECLARE
    warehouse_admin_role_id BIGINT;
BEGIN
    -- Get WAREHOUSE_ADMIN role ID
    SELECT id INTO warehouse_admin_role_id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN';

    IF warehouse_admin_role_id IS NULL THEN
        RAISE EXCEPTION 'WAREHOUSE_ADMIN role not found in sys_role table';
    END IF;

    -- Migrate users with STAFF role
    INSERT INTO sys_user_role (user_id, role_id, assigned_by, assigned_at)
    SELECT
        u.id,
        warehouse_admin_role_id,
        (SELECT id FROM users WHERE role = 'ADMIN' LIMIT 1), -- Assigned by first admin
        NOW()
    FROM users u
    WHERE u.role = 'STAFF'
    ON CONFLICT (user_id, role_id) DO NOTHING;

    RAISE NOTICE 'Migrated STAFF users to WAREHOUSE_ADMIN role';
END $$;

-- ============================================================================
-- STEP 2: Create test account with multiple roles
-- ============================================================================

DO $$
DECLARE
    multi_user_id BIGINT;
    warehouse_admin_role_id BIGINT;
    salesperson_role_id BIGINT;
    admin_user_id BIGINT;
BEGIN
    -- Get role IDs
    SELECT id INTO warehouse_admin_role_id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN';
    SELECT id INTO salesperson_role_id FROM sys_role WHERE role_code = 'SALESPERSON';
    SELECT id INTO admin_user_id FROM users WHERE role = 'ADMIN' LIMIT 1;

    IF warehouse_admin_role_id IS NULL OR salesperson_role_id IS NULL THEN
        RAISE EXCEPTION 'Required roles not found in sys_role table';
    END IF;

    -- Create multi_user account (password: password123, BCrypt hash)
    INSERT INTO users (username, password, display_name, enabled, role, created_at, updated_at, remark)
    VALUES (
        'multi_user',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', -- BCrypt hash of 'password123'
        '多角色测试用户',
        true,
        'STAFF', -- Temporary placeholder, will be removed in Step 4
        NOW(),
        NOW(),
        'Multi-role test account for identity switching validation'
    )
    ON CONFLICT (username) DO NOTHING
    RETURNING id INTO multi_user_id;

    -- If user already exists, get their ID
    IF multi_user_id IS NULL THEN
        SELECT id INTO multi_user_id FROM users WHERE username = 'multi_user';
    END IF;

    -- Assign WAREHOUSE_ADMIN role
    INSERT INTO sys_user_role (user_id, role_id, assigned_by, assigned_at)
    VALUES (multi_user_id, warehouse_admin_role_id, COALESCE(admin_user_id, multi_user_id), NOW())
    ON CONFLICT (user_id, role_id) DO NOTHING;

    -- Assign SALESPERSON role
    INSERT INTO sys_user_role (user_id, role_id, assigned_by, assigned_at)
    VALUES (multi_user_id, salesperson_role_id, COALESCE(admin_user_id, multi_user_id), NOW())
    ON CONFLICT (user_id, role_id) DO NOTHING;

    RAISE NOTICE 'Created multi_user test account with WAREHOUSE_ADMIN and SALESPERSON roles';
END $$;

-- ============================================================================
-- STEP 3: Verify all users have at least one role
-- ============================================================================

DO $$
DECLARE
    orphan_user_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_user_count
    FROM users u
    LEFT JOIN sys_user_role sur ON u.id = sur.user_id
    WHERE sur.user_id IS NULL;

    IF orphan_user_count > 0 THEN
        RAISE EXCEPTION 'Found % users without any roles. Migration cannot proceed.', orphan_user_count;
    END IF;

    RAISE NOTICE 'Verification passed: All users have at least one role';
END $$;

-- ============================================================================
-- STEP 4: Add default_role_id column to users table
-- ============================================================================

-- Add column (nullable initially)
ALTER TABLE users ADD COLUMN IF NOT EXISTS default_role_id BIGINT;

-- Add foreign key constraint with SET NULL on delete
ALTER TABLE users
ADD CONSTRAINT fk_users_default_role
FOREIGN KEY (default_role_id)
REFERENCES sys_role(id)
ON DELETE SET NULL;

-- Create index for performance
CREATE INDEX IF NOT EXISTS idx_users_default_role_id ON users(default_role_id);

COMMENT ON COLUMN users.default_role_id IS 'User''s preferred default role for login. If NULL, system selects role with minimum sort_order.';

-- ============================================================================
-- STEP 5: Set default_role_id for all existing users
-- ============================================================================

DO $$
BEGIN
    -- Set default role to the first assigned role (based on sort_order, then assigned_at)
    UPDATE users u
    SET default_role_id = (
        SELECT r.id
        FROM sys_user_role sur
        JOIN sys_role r ON sur.role_id = r.id
        WHERE sur.user_id = u.id
        AND r.is_active = true
        ORDER BY r.sort_order ASC, sur.assigned_at ASC
        LIMIT 1
    )
    WHERE u.default_role_id IS NULL;

    RAISE NOTICE 'Set default_role_id for all existing users';
END $$;

-- ============================================================================
-- STEP 6: Remove old role enum column from users table
-- ============================================================================

-- Drop the old role column (now obsolete)
ALTER TABLE users DROP COLUMN IF EXISTS role;

COMMENT ON TABLE users IS 'System users with multi-role support. Role assignments managed via sys_user_role table.';

-- ============================================================================
-- STEP 7: Final verification
-- ============================================================================

DO $$
DECLARE
    total_users INTEGER;
    users_with_roles INTEGER;
    users_with_default_role INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_users FROM users;

    SELECT COUNT(DISTINCT user_id) INTO users_with_roles FROM sys_user_role;

    SELECT COUNT(*) INTO users_with_default_role FROM users WHERE default_role_id IS NOT NULL;

    RAISE NOTICE '=== Migration Summary ===';
    RAISE NOTICE 'Total users: %', total_users;
    RAISE NOTICE 'Users with roles: %', users_with_roles;
    RAISE NOTICE 'Users with default role: %', users_with_default_role;

    IF total_users != users_with_roles THEN
        RAISE EXCEPTION 'Migration incomplete: % users without roles', (total_users - users_with_roles);
    END IF;

    RAISE NOTICE '✓ Multi-role migration completed successfully';
END $$;

-- ============================================================================
-- Verification Queries (for manual testing)
-- ============================================================================

-- Query 1: Check all users and their roles
-- SELECT
--     u.id,
--     u.username,
--     u.display_name,
--     u.enabled,
--     u.default_role_id,
--     dr.role_code as default_role_code,
--     dr.role_name as default_role_name,
--     STRING_AGG(r.role_code, ', ' ORDER BY r.sort_order) as all_roles
-- FROM users u
-- LEFT JOIN sys_role dr ON u.default_role_id = dr.id
-- LEFT JOIN sys_user_role sur ON u.id = sur.user_id
-- LEFT JOIN sys_role r ON sur.role_id = r.id
-- GROUP BY u.id, u.username, u.display_name, u.enabled, u.default_role_id, dr.role_code, dr.role_name
-- ORDER BY u.id;

-- Query 2: Verify multi_user has two roles
-- SELECT
--     u.username,
--     r.role_code,
--     r.role_name,
--     r.sort_order,
--     sur.assigned_at
-- FROM users u
-- JOIN sys_user_role sur ON u.id = sur.user_id
-- JOIN sys_role r ON sur.role_id = r.id
-- WHERE u.username = 'multi_user'
-- ORDER BY r.sort_order;

-- Query 3: Check for users without roles (should return 0 rows)
-- SELECT u.id, u.username
-- FROM users u
-- LEFT JOIN sys_user_role sur ON u.id = sur.user_id
-- WHERE sur.user_id IS NULL;
