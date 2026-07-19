-- P1.5 category foundation. The database and service support arbitrary depth;
-- the current management UI intentionally exposes only two levels.

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    parent_id BIGINT,
    category_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT uk_categories_company_code UNIQUE (company_id, category_code),
    CONSTRAINT chk_categories_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT chk_categories_sort_order CHECK (sort_order >= 0)
);

CREATE UNIQUE INDEX uk_categories_company_parent_name
    ON categories (company_id, COALESCE(parent_id, 0), LOWER(category_name));
CREATE INDEX idx_categories_company_parent_sort
    ON categories (company_id, parent_id, sort_order, id);
CREATE INDEX idx_categories_company_enabled
    ON categories (company_id, enabled);

COMMENT ON TABLE categories IS 'Product category tree; current management UI exposes two visible levels';
COMMENT ON COLUMN categories.parent_id IS 'Self reference supporting arbitrary category depth';

INSERT INTO categories (
    company_id, parent_id, category_code, category_name, sort_order, enabled, description
)
SELECT 1, NULL, 'UNCATEGORIZED', '未分类', 9999, TRUE, 'Migration fallback root category'
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE company_id = 1 AND category_code = 'UNCATEGORIZED'
);

INSERT INTO categories (
    company_id, parent_id, category_code, category_name, sort_order, enabled, description
)
SELECT 1, root.id, 'PENDING_CLASSIFICATION', '待整理', 9999, TRUE, 'Products awaiting category mapping'
FROM categories root
WHERE root.company_id = 1
  AND root.category_code = 'UNCATEGORIZED'
  AND NOT EXISTS (
      SELECT 1 FROM categories WHERE company_id = 1 AND category_code = 'PENDING_CLASSIFICATION'
  );

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    menu_url, menu_icon, sort_order, status, description, created_at, updated_at
)
SELECT 1, 'menu:product-catalog', '商品管理运营台', 'MENU',
       '/products', 'AppstoreOutlined', 20, 'ACTIVE', 'Product catalog management menu',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE company_id = 1 AND permission_code = 'menu:product-catalog'
);

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    resource_path, http_method, parent_id, sort_order, status, description, created_at, updated_at
)
SELECT 1, permission_code, permission_name, 'API', resource_path, http_method,
       (SELECT id FROM sys_permission WHERE company_id = 1 AND permission_code = 'menu:product-catalog'),
       sort_order, 'ACTIVE', description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('category:view', '查看产品类别', '/api/categories/**', 'GET', 1, 'View category tree and details'),
    ('category:create', '创建产品类别', '/api/categories', 'POST', 2, 'Create categories'),
    ('category:update', '维护产品类别', '/api/categories/**', 'PUT', 3, 'Update, move and enable categories'),
    ('category:delete', '删除产品类别', '/api/categories/*', 'DELETE', 4, 'Delete unused leaf categories')
) AS permissions(permission_code, permission_name, resource_path, http_method, sort_order, description)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.company_id = 1 AND existing.permission_code = permissions.permission_code
);

INSERT INTO sys_role_permission (company_id, role_id, permission_id, granted_at)
SELECT 1, role.id, permission.id, CURRENT_TIMESTAMP
FROM sys_role role
JOIN sys_permission permission ON permission.company_id = 1
WHERE role.company_id = 1
  AND role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code IN (
      'menu:product-catalog', 'category:view', 'category:create', 'category:update', 'category:delete'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.company_id = 1
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );
