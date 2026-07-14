package com.wms.system.dto.integration;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待映射 SKU 人工处理请求（P1 批次2）
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvePendingSkuRequest {

    /**
     * MAP（映射到商品）/ VIRTUAL（非库存行）/ IGNORE（忽略）
     */
    @NotBlank(message = "action cannot be blank")
    private String action;

    /**
     * 内部商品 ID（action=MAP 时必填）
     */
    private Long productId;

    /**
     * 数量换算：1 外部单位 = N 内部单位（默认 1）
     */
    private Integer quantityRatio;
}
