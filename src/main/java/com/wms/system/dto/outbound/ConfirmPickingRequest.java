package com.wms.system.dto.outbound;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认拣货请求 DTO
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
public class ConfirmPickingRequest {

    /**
     * 实际出库数量
     */
    @NotNull(message = "实际出库数量不能为空")
    @Min(value = 0, message = "实际出库数量不能为负数")
    private Integer actualQty;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
