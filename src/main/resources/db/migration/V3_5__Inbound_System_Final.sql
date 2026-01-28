-- =====================================================
-- V3.5 智能入库系统 - 完整实现
-- =====================================================
-- 功能：实现全新的入库系统，引入"采购确认"环节
-- 流程：User (Apply) → GM (Approve Plan) → User (Confirm Order) → System (Generate Batch Code) → Warehouse (Receive)
-- 批次码格式：SPU-SKU-DATE (如 SPU001-SKU123-20260125)
-- =====================================================

-- =====================================================
-- 1. 创建 suppliers 表（供应商管理）
-- =====================================================
CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    contact VARCHAR(100),
    address VARCHAR(255),
    email VARCHAR(100),
    phone VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supplier_code ON suppliers(code);
CREATE INDEX IF NOT EXISTS idx_supplier_active ON suppliers(is_active);

COMMENT ON TABLE suppliers IS '供应商信息表';
COMMENT ON COLUMN suppliers.code IS '供应商编码（唯一）';
COMMENT ON COLUMN suppliers.name IS '供应商名称';
COMMENT ON COLUMN suppliers.contact IS '联系人';
COMMENT ON COLUMN suppliers.address IS '地址';
COMMENT ON COLUMN suppliers.email IS '邮箱';
COMMENT ON COLUMN suppliers.phone IS '电话';
COMMENT ON COLUMN suppliers.is_active IS '是否启用';

-- =====================================================
-- 2. 创建 sys_excel_templates 表（Excel 模板管理）
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_excel_templates (
    id BIGSERIAL PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version VARCHAR(20),
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_template_type_active ON sys_excel_templates(template_type, is_active);

COMMENT ON TABLE sys_excel_templates IS 'Excel 模板管理表';
COMMENT ON COLUMN sys_excel_templates.template_name IS '模板名称';
COMMENT ON COLUMN sys_excel_templates.template_type IS '模板类型（如 INBOUND_ORDER）';
COMMENT ON COLUMN sys_excel_templates.file_url IS '模板文件 URL';
COMMENT ON COLUMN sys_excel_templates.is_active IS '是否启用';
COMMENT ON COLUMN sys_excel_templates.version IS '版本号';

-- =====================================================
-- 3. 创建 inbound_orders 主表（入库单）
-- =====================================================
CREATE TABLE IF NOT EXISTS inbound_orders (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,

    -- 三个数量字段
    total_plan_qty INT NOT NULL DEFAULT 0,
    total_confirmed_qty INT DEFAULT NULL,
    total_actual_qty INT NOT NULL DEFAULT 0,

    expected_date DATE,
    remark VARCHAR(500),

    -- 总经理审批字段
    gm_approved_by BIGINT,
    gm_approved_at TIMESTAMP,
    gm_approval_comment VARCHAR(500),

    -- 采购员确认字段
    confirmed_by BIGINT,
    confirmed_at TIMESTAMP,
    confirmation_comment VARCHAR(500),

    -- 仓库收货字段
    received_by BIGINT,
    received_at TIMESTAMP,

    -- 申请人信息
    applicant_id BIGINT NOT NULL,
    applicant_name VARCHAR(100) NOT NULL,

    -- 审计日志
    audit_log TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inbound_supplier FOREIGN KEY (supplier_id)
        REFERENCES suppliers(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_inbound_order_no ON inbound_orders(order_no);
CREATE INDEX IF NOT EXISTS idx_inbound_status ON inbound_orders(status);
CREATE INDEX IF NOT EXISTS idx_inbound_supplier ON inbound_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_inbound_applicant ON inbound_orders(applicant_id);
CREATE INDEX IF NOT EXISTS idx_inbound_created_at ON inbound_orders(created_at);

COMMENT ON TABLE inbound_orders IS '入库单主表';
COMMENT ON COLUMN inbound_orders.order_no IS '入库单号（唯一）';
COMMENT ON COLUMN inbound_orders.supplier_id IS '供应商 ID';
COMMENT ON COLUMN inbound_orders.status IS '状态：PENDING_APPROVAL, APPROVED_PLAN, AWAITING_RECEIVAL, COMPLETED, REJECTED';
COMMENT ON COLUMN inbound_orders.total_plan_qty IS '计划总数量';
COMMENT ON COLUMN inbound_orders.total_confirmed_qty IS '确认总数量';
COMMENT ON COLUMN inbound_orders.total_actual_qty IS '实收总数量';
COMMENT ON COLUMN inbound_orders.expected_date IS '预计到货日期';
COMMENT ON COLUMN inbound_orders.gm_approved_by IS '总经理审批人 ID';
COMMENT ON COLUMN inbound_orders.gm_approved_at IS '总经理审批时间';
COMMENT ON COLUMN inbound_orders.gm_approval_comment IS '总经理审批意见';
COMMENT ON COLUMN inbound_orders.confirmed_by IS '采购员确认人 ID';
COMMENT ON COLUMN inbound_orders.confirmed_at IS '采购员确认时间';
COMMENT ON COLUMN inbound_orders.confirmation_comment IS '采购员确认意见';
COMMENT ON COLUMN inbound_orders.received_by IS '仓库收货人 ID';
COMMENT ON COLUMN inbound_orders.received_at IS '仓库收货时间';
COMMENT ON COLUMN inbound_orders.applicant_id IS '申请人 ID';
COMMENT ON COLUMN inbound_orders.applicant_name IS '申请人姓名';
COMMENT ON COLUMN inbound_orders.audit_log IS '审计日志（JSON 格式）';

-- =====================================================
-- 4. 创建 inbound_order_items 明细表（入库单明细）
-- =====================================================
CREATE TABLE IF NOT EXISTS inbound_order_items (
    id BIGSERIAL PRIMARY KEY,
    inbound_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    -- 三个数量字段
    plan_qty INT NOT NULL,
    confirmed_qty INT DEFAULT NULL,
    actual_qty INT NOT NULL DEFAULT 0,

    -- 批次信息
    batch_code VARCHAR(50),
    expiry_date DATE,
    production_date DATE,
    external_batch_code VARCHAR(100),

    -- 目标仓库和库位
    target_warehouse_id BIGINT,
    target_location_id BIGINT,

    -- 成本信息
    unit_cost DECIMAL(10, 2),

    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inbound_item_order FOREIGN KEY (inbound_order_id)
        REFERENCES inbound_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_inbound_item_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inbound_item_warehouse FOREIGN KEY (target_warehouse_id)
        REFERENCES warehouses(id) ON DELETE SET NULL,
    CONSTRAINT fk_inbound_item_location FOREIGN KEY (target_location_id)
        REFERENCES locations(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_inbound_item_order ON inbound_order_items(inbound_order_id);
CREATE INDEX IF NOT EXISTS idx_inbound_item_product ON inbound_order_items(product_id);
CREATE INDEX IF NOT EXISTS idx_inbound_item_batch ON inbound_order_items(batch_code);
CREATE INDEX IF NOT EXISTS idx_inbound_item_expiry ON inbound_order_items(expiry_date);

COMMENT ON TABLE inbound_order_items IS '入库单明细表';
COMMENT ON COLUMN inbound_order_items.inbound_order_id IS '入库单 ID';
COMMENT ON COLUMN inbound_order_items.product_id IS '产品 ID';
COMMENT ON COLUMN inbound_order_items.plan_qty IS '计划数量';
COMMENT ON COLUMN inbound_order_items.confirmed_qty IS '确认数量';
COMMENT ON COLUMN inbound_order_items.actual_qty IS '实收数量';
COMMENT ON COLUMN inbound_order_items.batch_code IS '批次码（格式：SPU-SKU-DATE）';
COMMENT ON COLUMN inbound_order_items.expiry_date IS '过期日期';
COMMENT ON COLUMN inbound_order_items.production_date IS '生产日期';
COMMENT ON COLUMN inbound_order_items.external_batch_code IS '外部批次码（供应商提供）';
COMMENT ON COLUMN inbound_order_items.target_warehouse_id IS '目标仓库 ID';
COMMENT ON COLUMN inbound_order_items.target_location_id IS '目标库位 ID';
COMMENT ON COLUMN inbound_order_items.unit_cost IS '单位成本';

-- =====================================================
-- 5. 修改 inventory_batch 表的 batch_code 字段长度
-- =====================================================
-- 扩展 batch_code 字段以支持 SPU-SKU-DATE 格式
ALTER TABLE inventory_batch ALTER COLUMN batch_code TYPE VARCHAR(50);

COMMENT ON COLUMN inventory_batch.batch_code IS '批次码（支持 SPU-SKU-DATE 格式，最长 50 字符）';

-- =====================================================
-- 6. 插入权限配置
-- =====================================================

-- 入库单权限
INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, http_method, description)
VALUES
('inbound:create', '创建入库单', 'API', '/api/inbound-orders', 'POST', 'Create inbound order'),
('inbound:approve_plan', '审批入库计划', 'API', '/api/inbound-orders/*/approve-plan', 'POST', 'GM approves inbound plan'),
('inbound:confirm_order', '确认入库订单', 'API', '/api/inbound-orders/*/confirm-order', 'POST', 'Purchaser confirms order'),
('inbound:receive_goods', '仓库收货', 'API', '/api/inbound-orders/*/receive-goods', 'POST', 'Warehouse receives goods'),
('inbound:reject', '拒绝入库单', 'API', '/api/inbound-orders/*/reject', 'POST', 'GM rejects inbound order'),
('inbound:view', '查看入库单', 'API', '/api/inbound-orders/*', 'GET', 'View inbound order details'),
('inbound:list', '查看入库单列表', 'API', '/api/inbound-orders', 'GET', 'List inbound orders')
ON CONFLICT (permission_code) DO NOTHING;

-- 分配权限到角色
-- CHAIRMAN: 审批、拒绝、查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'CHAIRMAN'
AND p.permission_code IN ('inbound:approve_plan', 'inbound:reject', 'inbound:view', 'inbound:list')
ON CONFLICT DO NOTHING;

-- BUYER: 创建、确认、查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'BUYER'
AND p.permission_code IN ('inbound:create', 'inbound:confirm_order', 'inbound:view', 'inbound:list')
ON CONFLICT DO NOTHING;

-- WAREHOUSE_ADMIN: 收货、查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'WAREHOUSE_ADMIN'
AND p.permission_code IN ('inbound:receive_goods', 'inbound:view', 'inbound:list')
ON CONFLICT DO NOTHING;

-- SUPER_ADMIN: 所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code LIKE 'inbound:%'
ON CONFLICT DO NOTHING;

-- =====================================================
-- 7. 插入示例数据（可选）
-- =====================================================

-- 插入示例供应商
INSERT INTO suppliers (code, name, contact, address, email, phone, is_active)
VALUES
('SUP001', '北京供应商A', '张三', '北京市朝阳区', 'zhangsan@example.com', '13800138000', TRUE),
('SUP002', '上海供应商B', '李四', '上海市浦东新区', 'lisi@example.com', '13900139000', TRUE),
('SUP003', '广州供应商C', '王五', '广州市天河区', 'wangwu@example.com', '13700137000', TRUE)
ON CONFLICT (code) DO NOTHING;

-- 插入 Excel 模板
INSERT INTO sys_excel_templates (template_name, template_type, file_url, is_active, version, description)
VALUES
('入库单导入模板', 'INBOUND_ORDER', '/templates/inbound_order_template.xlsx', TRUE, '1.0', '用于批量导入入库单明细')
ON CONFLICT DO NOTHING;

-- =====================================================
-- 8. 创建触发器（自动更新 updated_at）
-- =====================================================

-- suppliers 表触发器
CREATE OR REPLACE FUNCTION update_suppliers_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_suppliers_updated_at ON suppliers;
CREATE TRIGGER trigger_update_suppliers_updated_at
    BEFORE UPDATE ON suppliers
    FOR EACH ROW
    EXECUTE FUNCTION update_suppliers_updated_at();

-- sys_excel_templates 表触发器
CREATE OR REPLACE FUNCTION update_sys_excel_templates_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_sys_excel_templates_updated_at ON sys_excel_templates;
CREATE TRIGGER trigger_update_sys_excel_templates_updated_at
    BEFORE UPDATE ON sys_excel_templates
    FOR EACH ROW
    EXECUTE FUNCTION update_sys_excel_templates_updated_at();

-- inbound_orders 表触发器
CREATE OR REPLACE FUNCTION update_inbound_orders_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_inbound_orders_updated_at ON inbound_orders;
CREATE TRIGGER trigger_update_inbound_orders_updated_at
    BEFORE UPDATE ON inbound_orders
    FOR EACH ROW
    EXECUTE FUNCTION update_inbound_orders_updated_at();

-- inbound_order_items 表触发器
CREATE OR REPLACE FUNCTION update_inbound_order_items_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_inbound_order_items_updated_at ON inbound_order_items;
CREATE TRIGGER trigger_update_inbound_order_items_updated_at
    BEFORE UPDATE ON inbound_order_items
    FOR EACH ROW
    EXECUTE FUNCTION update_inbound_order_items_updated_at();

-- =====================================================
-- 迁移完成
-- =====================================================
