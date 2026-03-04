-- V3.7 智能销售与出库系统
-- 创建日期: 2026-01-28
-- 描述: 实现从销售订单到出库的完整闭环系统

-- ============================================================
-- 1. 系统配置表 (system_config)
-- ============================================================
CREATE TABLE IF NOT EXISTS system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    config_type VARCHAR(50) NOT NULL,  -- DECIMAL, INTEGER, STRING, BOOLEAN
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_config_key ON system_config(config_key);

COMMENT ON TABLE system_config IS '系统配置表';
COMMENT ON COLUMN system_config.config_key IS '配置键（唯一）';
COMMENT ON COLUMN system_config.config_value IS '配置值';
COMMENT ON COLUMN system_config.description IS '配置描述';
COMMENT ON COLUMN system_config.config_type IS '配置类型：DECIMAL, INTEGER, STRING, BOOLEAN';

-- 插入默认配置
INSERT INTO system_config (config_key, config_value, description, config_type)
VALUES ('sales.approval.amount_threshold', '50000.00', '销售订单审批金额阈值', 'DECIMAL')
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- 2. 客户管理表 (customers)
-- ============================================================
CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    contact VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(100),
    address VARCHAR(255),
    credit_limit DECIMAL(15,2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customer_code ON customers(code);
CREATE INDEX IF NOT EXISTS idx_customer_active ON customers(is_active);

COMMENT ON TABLE customers IS '客户管理表';
COMMENT ON COLUMN customers.code IS '客户编码（唯一）';
COMMENT ON COLUMN customers.name IS '客户名称';
COMMENT ON COLUMN customers.contact IS '联系人';
COMMENT ON COLUMN customers.phone IS '联系电话';
COMMENT ON COLUMN customers.email IS '电子邮箱';
COMMENT ON COLUMN customers.address IS '客户地址';
COMMENT ON COLUMN customers.credit_limit IS '信用额度';
COMMENT ON COLUMN customers.is_active IS '是否激活';

-- ============================================================
-- 3. 销售订单主表 (sales_orders)
-- ============================================================
CREATE TABLE IF NOT EXISTS sales_orders (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(30) UNIQUE NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    total_amount DECIMAL(15,2) DEFAULT 0.00,
    status VARCHAR(30) NOT NULL,
    review_reason VARCHAR(500),

    -- 审批信息
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    review_comment VARCHAR(500),

    -- 申请人信息
    applicant_id BIGINT NOT NULL,
    applicant_name VARCHAR(100) NOT NULL,

    -- 审计日志
    audit_log TEXT,

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_order_no ON sales_orders(order_no);
CREATE INDEX IF NOT EXISTS idx_sales_status ON sales_orders(status);
CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales_orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_applicant ON sales_orders(applicant_id);
CREATE INDEX IF NOT EXISTS idx_sales_created_at ON sales_orders(created_at);

COMMENT ON TABLE sales_orders IS '销售订单主表';
COMMENT ON COLUMN sales_orders.order_no IS '订单编号（唯一）';
COMMENT ON COLUMN sales_orders.customer_id IS '客户ID';
COMMENT ON COLUMN sales_orders.total_amount IS '订单总金额';
COMMENT ON COLUMN sales_orders.status IS '订单状态：DRAFT, PENDING_APPROVAL, APPROVED_AWAITING_SHIPMENT, SHIPPED, REJECTED, CANCELLED';
COMMENT ON COLUMN sales_orders.review_reason IS '审批原因（低价或高额）';
COMMENT ON COLUMN sales_orders.reviewed_by IS '审批人ID';
COMMENT ON COLUMN sales_orders.reviewed_at IS '审批时间';
COMMENT ON COLUMN sales_orders.review_comment IS '审批意见';
COMMENT ON COLUMN sales_orders.applicant_id IS '申请人ID';
COMMENT ON COLUMN sales_orders.applicant_name IS '申请人姓名';
COMMENT ON COLUMN sales_orders.audit_log IS '审计日志（JSON格式）';

-- ============================================================
-- 4. 销售订单明细表 (sales_order_items)
-- ============================================================
CREATE TABLE IF NOT EXISTS sales_order_items (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL REFERENCES sales_orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10,2) NOT NULL CHECK (unit_price >= 0),
    subtotal DECIMAL(15,2) NOT NULL,

    -- 客户偏好设置
    reject_near_expiry BOOLEAN DEFAULT FALSE,

    -- 销售员手动指定批次（JSON 数组）
    specified_batch_ids TEXT,

    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_item_order ON sales_order_items(sales_order_id);
CREATE INDEX IF NOT EXISTS idx_sales_item_product ON sales_order_items(product_id);

COMMENT ON TABLE sales_order_items IS '销售订单明细表';
COMMENT ON COLUMN sales_order_items.sales_order_id IS '销售订单ID';
COMMENT ON COLUMN sales_order_items.product_id IS '产品ID';
COMMENT ON COLUMN sales_order_items.quantity IS '销售数量';
COMMENT ON COLUMN sales_order_items.unit_price IS '单价';
COMMENT ON COLUMN sales_order_items.subtotal IS '小计金额';
COMMENT ON COLUMN sales_order_items.reject_near_expiry IS '是否拒收临期品';
COMMENT ON COLUMN sales_order_items.specified_batch_ids IS '指定批次ID（JSON数组）';
COMMENT ON COLUMN sales_order_items.remark IS '备注';

-- ============================================================
-- 5. 出库任务表 (outbound_tasks)
-- ============================================================
CREATE TABLE IF NOT EXISTS outbound_tasks (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL REFERENCES sales_orders(id),
    sales_order_item_id BIGINT NOT NULL REFERENCES sales_order_items(id),
    assigned_batch_id BIGINT NOT NULL REFERENCES inventory_batch(id),
    location_id BIGINT NOT NULL REFERENCES locations(id),
    plan_qty INTEGER NOT NULL CHECK (plan_qty > 0),
    actual_qty INTEGER DEFAULT 0,
    status VARCHAR(30) NOT NULL,

    -- 仓库员信息
    picked_by BIGINT,
    picked_at TIMESTAMP,

    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbound_task_order ON outbound_tasks(sales_order_id);
CREATE INDEX IF NOT EXISTS idx_outbound_task_batch ON outbound_tasks(assigned_batch_id);
CREATE INDEX IF NOT EXISTS idx_outbound_task_status ON outbound_tasks(status);
CREATE INDEX IF NOT EXISTS idx_outbound_task_item ON outbound_tasks(sales_order_item_id);

COMMENT ON TABLE outbound_tasks IS '出库任务表';
COMMENT ON COLUMN outbound_tasks.sales_order_id IS '销售订单ID';
COMMENT ON COLUMN outbound_tasks.sales_order_item_id IS '销售订单明细ID';
COMMENT ON COLUMN outbound_tasks.assigned_batch_id IS '分配的批次ID';
COMMENT ON COLUMN outbound_tasks.location_id IS '库位ID';
COMMENT ON COLUMN outbound_tasks.plan_qty IS '计划出库数量';
COMMENT ON COLUMN outbound_tasks.actual_qty IS '实际出库数量';
COMMENT ON COLUMN outbound_tasks.status IS '任务状态：PENDING, PICKING, COMPLETED';
COMMENT ON COLUMN outbound_tasks.picked_by IS '拣货人ID';
COMMENT ON COLUMN outbound_tasks.picked_at IS '拣货时间';
COMMENT ON COLUMN outbound_tasks.remark IS '备注';

-- ============================================================
-- 6. 更新 products 表（新增字段）
-- ============================================================
ALTER TABLE products ADD COLUMN IF NOT EXISTS min_sales_price DECIMAL(10,2) DEFAULT 0.00;
ALTER TABLE products ADD COLUMN IF NOT EXISTS near_expiry_days INTEGER DEFAULT 90;
ALTER TABLE products ADD COLUMN IF NOT EXISTS per_pack_qty INTEGER DEFAULT 1;

COMMENT ON COLUMN products.min_sales_price IS '最低限价';
COMMENT ON COLUMN products.near_expiry_days IS '临期天数阈值';
COMMENT ON COLUMN products.per_pack_qty IS '箱规（每箱数量）';

CREATE INDEX IF NOT EXISTS idx_product_min_price ON products(min_sales_price);

-- ============================================================
-- 7. 更新 updated_at 触发器
-- ============================================================

-- system_config 表触发器
CREATE OR REPLACE FUNCTION update_system_config_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_system_config_updated_at ON system_config;
CREATE TRIGGER trigger_update_system_config_updated_at
    BEFORE UPDATE ON system_config
    FOR EACH ROW
    EXECUTE FUNCTION update_system_config_updated_at();

-- customers 表触发器
CREATE OR REPLACE FUNCTION update_customers_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_customers_updated_at ON customers;
CREATE TRIGGER trigger_update_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW
    EXECUTE FUNCTION update_customers_updated_at();

-- sales_orders 表触发器
CREATE OR REPLACE FUNCTION update_sales_orders_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_sales_orders_updated_at ON sales_orders;
CREATE TRIGGER trigger_update_sales_orders_updated_at
    BEFORE UPDATE ON sales_orders
    FOR EACH ROW
    EXECUTE FUNCTION update_sales_orders_updated_at();

-- sales_order_items 表触发器
CREATE OR REPLACE FUNCTION update_sales_order_items_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_sales_order_items_updated_at ON sales_order_items;
CREATE TRIGGER trigger_update_sales_order_items_updated_at
    BEFORE UPDATE ON sales_order_items
    FOR EACH ROW
    EXECUTE FUNCTION update_sales_order_items_updated_at();

-- outbound_tasks 表触发器
CREATE OR REPLACE FUNCTION update_outbound_tasks_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_outbound_tasks_updated_at ON outbound_tasks;
CREATE TRIGGER trigger_update_outbound_tasks_updated_at
    BEFORE UPDATE ON outbound_tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_outbound_tasks_updated_at();
