package com.wms.system.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 出库任务响应 DTO
 *
 * V3.7 架构：出库任务管理
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundTaskResponse {

    /**
     * 任务ID
     */
    private Long id;

    /**
     * 销售订单ID
     */
    private Long salesOrderId;

    /**
     * 销售订单编号
     */
    private String salesOrderNo;

    /**
     * 销售订单明细ID
     */
    private Long salesOrderItemId;

    /**
     * 分配的批次ID
     */
    private Long assignedBatchId;

    /**
     * V4.5 reservation ledger ID consumed by this task.
     */
    private Long reservationId;

    /**
     * 批次编码
     */
    private String batchCode;

    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    private String locationCode;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 产品条形码
     */
    private String productBarcode;

    /**
     * 计划出库数量
     */
    private Integer planQty;

    /**
     * 实际出库数量
     */
    private Integer actualQty;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 任务状态描述
     */
    private String statusDescription;

    /**
     * 拣货人ID
     */
    private Long pickedBy;

    /**
     * 拣货人姓名
     */
    private String pickedByName;

    /**
     * 拣货时间
     */
    private LocalDateTime pickedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
