package com.wms.system.dto.integration;

import com.wms.system.entity.ChannelSkuMapping;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 渠道 SKU 映射响应（P1 批次2）
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSkuMappingResponse {

    private Long id;
    private String channel;
    private String storeIdentifier;
    private String externalSku;
    private String mappingType;
    private Long productId;
    private String productName;
    private String productBarcode;
    private Integer quantityRatio;
    private String status;
    private String source;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ChannelSkuMappingResponse from(ChannelSkuMapping m) {
        return ChannelSkuMappingResponse.builder()
            .id(m.getId())
            .channel(m.getChannel())
            .storeIdentifier(m.getStoreIdentifier())
            .externalSku(m.getExternalSku())
            .mappingType(m.getMappingType())
            .productId(m.getProduct() == null ? null : m.getProduct().getId())
            .productName(m.getProduct() == null ? null : m.getProduct().getName())
            .productBarcode(m.getProduct() == null ? null : m.getProduct().getBarcode())
            .quantityRatio(m.getQuantityRatio())
            .status(m.getStatus())
            .source(m.getSource())
            .remark(m.getRemark())
            .createdAt(m.getCreatedAt())
            .updatedAt(m.getUpdatedAt())
            .build();
    }
}
