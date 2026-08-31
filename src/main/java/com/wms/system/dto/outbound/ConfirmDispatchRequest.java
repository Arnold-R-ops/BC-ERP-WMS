package com.wms.system.dto.outbound;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Confirm physical dispatch and deduct the shipped inventory atomically. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmDispatchRequest {
    @NotNull(message = "实际出库数量不能为空")
    @Min(value = 1, message = "实际出库数量必须大于零")
    private Integer actualQty;

    private Long locationId;

    @Size(max = 100)
    private String skuCode;

    @Size(max = 100)
    private String batchCode;

    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
