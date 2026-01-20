package com.wms.system.entity.enums;

/**
 * 用户角色枚举
 *
 * @author WMS Team
 * @since 2025-01-09
 * @deprecated 自 v3.3 起废弃，请使用 sys_role 表和多角色系统
 *             {@link com.wms.system.entity.SysRole}
 *             {@link com.wms.system.entity.SysUserRole}
 *
 * 迁移说明:
 * - ADMIN 已迁移至 SUPER_ADMIN 角色
 * - STAFF 已迁移至 WAREHOUSE_ADMIN 角色
 * - 新系统支持一个用户拥有多个角色
 * - 角色权限通过 sys_role 和 sys_user_role 表动态管理
 *
 * 保留原因:
 * - 向后兼容性：避免旧代码编译错误
 * - 过渡期支持：允许团队逐步迁移到新系统
 * - 将在未来版本完全移除
 */
@Deprecated(since = "v3.3", forRemoval = true)
public enum Role {
    /**
     * 管理员：拥有系统全部权限（用户管理、配置管理、数据导出等）
     * @deprecated 使用 SysRole.SUPER_ADMIN 替代
     */
    @Deprecated
    ADMIN("管理员"),

    /**
     * 普通员工：仅拥有基础操作权限（入库、出库、库存查询等）
     * @deprecated 使用 SysRole.WAREHOUSE_ADMIN 替代
     */
    @Deprecated
    STAFF("员工");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
