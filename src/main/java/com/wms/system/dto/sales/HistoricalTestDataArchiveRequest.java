package com.wms.system.dto.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalTestDataArchiveRequest {
    @NotBlank(message = "归档原因不能为空")
    @Size(max = 500, message = "归档原因长度不能超过 500 个字符")
    private String reason;

    @NotBlank(message = "确认订单号不能为空")
    @Size(max = 30, message = "确认订单号长度不能超过 30 个字符")
    private String confirmationOrderNo;

    @NotBlank(message = "预览指纹不能为空")
    @Size(min = 64, max = 64, message = "预览指纹格式不正确")
    private String expectedFingerprint;
}
