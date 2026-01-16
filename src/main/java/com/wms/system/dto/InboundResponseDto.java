package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Inbound Response DTO (入库响应 DTO)
 *
 * V3.3 Feature: Split Putaway Support
 *
 * 用途：
 * - 返回入库操作的执行结果
 * - 包含成功/失败统计和详细信息
 *
 * @author WMS Team
 * @since V3.3
 * @version 3.3 (Split Putaway Support)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundResponseDto {

    /**
     * 操作是否成功
     */
    private Boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 总处理数量（条目数）
     */
    private Integer totalItems;

    /**
     * 成功处理数量
     */
    private Integer successCount;

    /**
     * 失败处理数量
     */
    private Integer failureCount;

    /**
     * 总入库数量（最小单位）
     */
    private Integer totalQuantity;

    /**
     * 处理时间戳
     */
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();

    /**
     * 详细结果列表
     */
    private java.util.List<InboundItemResultDto> details;

    /**
     * 单个入库项结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundItemResultDto {
        /**
         * SKU ID
         */
        private Long skuId;

        /**
         * SKU 名称
         */
        private String skuName;

        /**
         * 批次码
         */
        private String batchCode;

        /**
         * 库位编号
         */
        private String locationCode;

        /**
         * 入库数量
         */
        private Integer quantity;

        /**
         * 格式化数量（如 "60 Box"）
         */
        private String formattedQuantity;

        /**
         * 是否成功
         */
        private Boolean success;

        /**
         * 操作类型（"CREATED" 或 "UPDATED"）
         */
        private String operationType;

        /**
         * 库存流水 ID
         */
        private Long transactionId;

        /**
         * 错误消息（如果失败）
         */
        private String errorMessage;
    }
}
