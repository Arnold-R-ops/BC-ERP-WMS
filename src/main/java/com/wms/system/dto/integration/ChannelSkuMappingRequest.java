package com.wms.system.dto.integration;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道 SKU 映射请求（P1 批次2）
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSkuMappingRequest {

    /**
     * 渠道，默认 SHOPIFY
     */
    private String channel;

    private String storeIdentifier;

    /**
     * 渠道侧 SKU 原文（创建时必填；更新时忽略）
     */
    @NotBlank(message = "externalSku cannot be blank")
    private String externalSku;

    /**
     * PRODUCT（默认）/ VIRTUAL
     */
    private String mappingType;

    /**
     * 内部商品 ID（PRODUCT 类型必填）
     */
    private Long productId;

    /**
     * 数量换算：1 外部单位 = N 内部单位（默认 1）
     */
    private Integer quantityRatio;

    /**
     * ACTIVE / DISABLED（仅更新时使用）
     */
    private String status;

    private String remark;
}
