package com.wms.system.dto.shopify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Shopify 订单明细 DTO
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyLineItemDto {

    /**
     * Shopify 明细 ID
     */
    private Long id;

    /**
     * 产品 SKU
     */
    private String sku;

    /**
     * 产品名称
     */
    private String name;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 单价（字符串格式）
     */
    private String price;
}
