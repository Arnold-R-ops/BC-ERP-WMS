package com.wms.system.entity.enums;

/**
 * 库位区域枚举
 * 按照仓库功能分区，便于库位管理和优化拣货路径
 *
 * @author WMS Team
 * @since 2025-01-09
 */
public enum Zone {
    /**
     * A区：高频商品区（快速拣货区，靠近出货口）
     */
    ZONE_A("A区-高频商品"),

    /**
     * B区：常规商品区（中频商品存储）
     */
    ZONE_B("B区-常规商品"),

    /**
     * C区：低频商品区（慢速流转区）
     */
    ZONE_C("C区-低频商品"),

    /**
     * D区：冷藏区（需要温度控制的商品，预留 ERP 扩展）
     */
    ZONE_D("D区-冷藏区"),

    /**
     * E区：危险品区（易燃易爆、化学品等，需特殊监管）
     */
    ZONE_E("E区-危险品"),

    /**
     * Q区：质检区（入库待检、退货待处理）
     */
    ZONE_Q("Q区-质检区"),

    /**
     * R区：退货区（需退回供应商或销毁的商品）
     */
    ZONE_R("R区-退货区");

    private final String description;

    Zone(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
