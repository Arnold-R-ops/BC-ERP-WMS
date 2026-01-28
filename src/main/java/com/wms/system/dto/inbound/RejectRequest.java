package com.wms.system.dto.inbound;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拒绝入库单请求 DTO
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequest {

    /**
     * 拒绝原因
     */
    @Size(max = 500, message = "拒绝原因长度不能超过500")
    private String reason;
}
