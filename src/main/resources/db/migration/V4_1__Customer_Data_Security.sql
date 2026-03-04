-- =====================================================
-- V4.1 - 销售权限隔离与数据脱敏 (Data Security)
-- =====================================================
-- 功能说明：
-- 1. 为 customers 表添加 owner_id 字段，记录客户归属人
-- 2. 创建索引以优化按归属人查询的性能
-- 3. 支持行级隔离（Row-Level Security）
--
-- 业务场景：
-- - 销售员只能查看和管理自己的客户
-- - 管理员和经理可以查看所有客户
-- - 保护客户资产，防止数据泄露
--
-- @author WMS Team
-- @since 2026-02-12
-- @version 4.1 (Customer Data Security)
-- =====================================================

-- 1. 为 customers 表添加 owner_id 字段
ALTER TABLE customers
ADD COLUMN owner_id BIGINT COMMENT '客户归属人ID（关联 users.id）';

-- 2. 创建索引以优化按归属人查询
CREATE INDEX idx_customer_owner ON customers(owner_id);

-- 3. 添加外键约束（可选，确保数据完整性）
-- 注意：如果需要删除用户，建议使用软删除而不是物理删除
-- 或者在删除用户前将其客户转移给其他用户
ALTER TABLE customers
ADD CONSTRAINT fk_customer_owner
FOREIGN KEY (owner_id) REFERENCES users(id)
ON DELETE SET NULL;  -- 如果用户被删除，将 owner_id 设置为 NULL

-- 4. 为现有客户数据设置默认归属人（可选）
-- 如果需要将现有客户分配给特定用户，可以执行以下 SQL：
-- UPDATE customers SET owner_id = 1 WHERE owner_id IS NULL;
-- 注意：请根据实际业务需求调整默认归属人

-- =====================================================
-- 迁移完成
-- =====================================================
