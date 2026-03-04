package com.wms.system.dto.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 取消订单请求 DTO
 *
 * V3.7 架构：销售订单取消
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderRequest {

    /**
     * 取消原因
     */
    @NotBlank(message = "取消原因不能为空")
    @Size(max = 500, message = "取消原因长度不能超过 500 个字符")
    private String reason;
}
