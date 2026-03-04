package com.wms.system.dto.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批请求 DTO
 *
 * V3.7 架构：销售订单审批
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    /**
     * 审批意见
     */
    @Size(max = 500, message = "审批意见长度不能超过 500 个字符")
    private String comment;

    /**
     * 拒绝原因（仅拒绝时需要）
     */
    @Size(max = 500, message = "拒绝原因长度不能超过 500 个字符")
    private String reason;
}
