-- =====================================================
-- V4.2 - AI 基建升级 (AI Foundation Patch)
-- =====================================================
-- 功能说明：
-- 1. 逻辑删除 (Soft Delete)：核心业务表新增 is_deleted 字段
-- 2. 历史价格快照 (Price Snapshots)：订单明细固化下单时的商品现价
-- 3. 归因记录 (Reason Codes)：库存流水新增结构化原因码
-- 4. 细化时间戳 (Granular Timestamps)：销售订单补全审批与发货时间点
--
-- 设计原则：
-- - 所有新增字段均有默认值，不影响现有数据
-- - 不删除或重命名任何现有字段，保持向后兼容
-- - 索引仅添加在高频查询场景使用的字段上
--
-- @author WMS Team
-- @since 2026-03-02
-- @version 4.2 (AI Foundation Patch)
-- =====================================================


-- =====================================================
-- Part 1: 逻辑删除 (Soft Delete)
-- =====================================================
-- 业务价值：
-- - 保留历史数据供 BI 分析，避免物理删除导致数据孤岛
-- - 支持数据恢复（误操作保护）
-- - 满足审计合规要求（数据不可删除）
-- =====================================================

-- 1.1 客户表：is_deleted
ALTER TABLE customers
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '逻辑删除标记（true=已删除，false=正常）';

CREATE INDEX idx_customers_not_deleted ON customers (is_deleted);

-- 1.2 商品表：is_deleted
ALTER TABLE products
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '逻辑删除标记（true=已删除，false=正常）';

CREATE INDEX idx_products_not_deleted ON products (is_deleted);

-- 1.3 销售订单表：is_deleted
ALTER TABLE sales_orders
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '逻辑删除标记（true=已删除，false=正常）';

CREATE INDEX idx_sales_orders_not_deleted ON sales_orders (is_deleted);

-- 1.4 采购单表：is_deleted
--     注意：表名为 purchase_order（非复数）
ALTER TABLE purchase_order
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '逻辑删除标记（true=已删除，false=正常）';

CREATE INDEX idx_purchase_order_not_deleted ON purchase_order (is_deleted);


-- =====================================================
-- Part 2: 历史价格快照 (Price Snapshots)
-- =====================================================
-- 业务价值：
-- - 固化下单时刻的商品标准售价，不随 products.unit_price 变动而变化
-- - 支持折扣率分析：discount_rate = (product_price_snapshot - unit_price) / product_price_snapshot
-- - 支持毛利分析：gross_profit = (unit_price - purchase_unit_cost) * quantity
-- =====================================================

-- 2.1 销售订单明细：product_price_snapshot（下单时商品标价快照）
--     区别：unit_price = 成交价（可协商），product_price_snapshot = 商品标准售价（来自 products.unit_price）
ALTER TABLE sales_order_items
    ADD COLUMN product_price_snapshot DECIMAL(10, 2)
        COMMENT '下单时商品标准售价快照（来自 products.unit_price，用于折扣率分析）';

-- 2.2 采购单明细：product_price_snapshot（下单时商品建议售价快照）
--     区别：unit_cost = 实际采购价，product_price_snapshot = 商品标准售价（参考价）
ALTER TABLE purchase_order_item
    ADD COLUMN product_price_snapshot DECIMAL(10, 2)
        COMMENT '下单时商品标准售价快照（来自 products.unit_price，用于毛利分析）';


-- =====================================================
-- Part 3: 归因记录 (Reason Codes)
-- =====================================================
-- 业务价值：
-- - 提供机器可读的结构化原因码，取代纯文本 remark
-- - 支持 BI 按原因分类统计（出入库原因分布）
-- - 支持 AI 异常检测（非常规出库原因预警）
--
-- 标准原因码（与 SourceType 枚举对应）：
-- 入库：PURCHASE_INBOUND, INBOUND_IN, RETURN_INBOUND, PRODUCTION_INBOUND,
--       TRANSFER_INBOUND, INVENTORY_GAIN
-- 出库：SALES_OUTBOUND, PRODUCTION_OUTBOUND, TRANSFER_OUTBOUND,
--       INVENTORY_LOSS, EXPIRY_DAMAGE, GIFT_OUTBOUND, SAMPLE_OUTBOUND
-- 调整：MANUAL_ADJUST, STOCKTAKE_ADJUST
-- =====================================================

-- 3.1 库存流水表：reason_code（结构化原因码）
--     与已有 remark 字段（自由文本）互补，本字段为机器可读的标准化原因
ALTER TABLE stock_transactions
    ADD COLUMN reason_code VARCHAR(50)
        COMMENT '结构化原因码（机器可读，如 SALES_OUTBOUND / PURCHASE_INBOUND / EXPIRY_DAMAGE）';

-- 3.2 库存流水表：remarks（AI 归因短注释）
--     与已有 remark 字段（业务自由备注，1000 字符）互补，本字段为简短的 AI 可解析归因注释
ALTER TABLE stock_transactions
    ADD COLUMN remarks VARCHAR(500)
        COMMENT '归因短注释（AI 可解析，如"临期报废批次 BC2026001"，≤500 字符）';

CREATE INDEX idx_stock_tx_reason_code ON stock_transactions (reason_code);


-- =====================================================
-- Part 4: 细化时间戳 (Granular Timestamps)
-- =====================================================
-- 业务价值：
-- - 精确记录审批节点，支持审批时效分析（从下单到审批的等待时长）
-- - 精确记录发货节点，支持履约时效分析（从下单到发货的交付周期）
-- - 为 BI 漏斗分析提供完整的时间轴数据
--
-- 说明：
-- - reviewed_at 已存在（审批人操作时间，approve 和 reject 均会更新）
-- - approved_at 新增（仅在 status → APPROVED_AWAITING_SHIPMENT 时写入）
-- - shipped_at 新增（仅在 status → SHIPPED 时写入）
-- =====================================================

-- 4.1 销售订单：approved_at（正式审批通过时间，与 reviewed_at 区分）
--     reviewed_at：审批人点击操作的时间（审批通过 或 拒绝 均会写入）
--     approved_at：仅在审批通过（status=APPROVED_AWAITING_SHIPMENT）时写入
ALTER TABLE sales_orders
    ADD COLUMN approved_at DATETIME
        COMMENT '审批通过时间（仅 status=APPROVED_AWAITING_SHIPMENT 时写入，与 reviewed_at 区分）';

-- 4.2 销售订单：shipped_at（发货完成时间）
--     在 status → SHIPPED 时由出库服务写入
ALTER TABLE sales_orders
    ADD COLUMN shipped_at DATETIME
        COMMENT '发货完成时间（status=SHIPPED 时写入）';

CREATE INDEX idx_sales_orders_approved_at ON sales_orders (approved_at);
CREATE INDEX idx_sales_orders_shipped_at ON sales_orders (shipped_at);


-- =====================================================
-- 迁移完成
-- =====================================================
-- 影响表汇总：
-- - customers          +1 字段（is_deleted）
-- - products           +1 字段（is_deleted）
-- - sales_orders       +3 字段（is_deleted, approved_at, shipped_at）
-- - purchase_order     +1 字段（is_deleted）
-- - sales_order_items  +1 字段（product_price_snapshot）
-- - purchase_order_item +1 字段（product_price_snapshot）
-- - stock_transactions +2 字段（reason_code, remarks）
-- =====================================================
