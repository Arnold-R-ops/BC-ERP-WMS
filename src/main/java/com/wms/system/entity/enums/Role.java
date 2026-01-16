package com.wms.system.entity.enums;

/**
 * 用户角色枚举
 *
 * @author WMS Team
 * @since 2025-01-09
 */
public enum Role {
    /**
     * 管理员：拥有系统全部权限（用户管理、配置管理、数据导出等）
     */
    ADMIN("管理员"),

    /**
     * 普通员工：仅拥有基础操作权限（入库、出库、库存查询等）
     */
    STAFF("员工");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
