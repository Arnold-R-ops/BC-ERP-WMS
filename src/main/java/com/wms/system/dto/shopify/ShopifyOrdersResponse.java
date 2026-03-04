package com.wms.system.dto.shopify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Shopify 订单响应 DTO
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyOrdersResponse {

    /**
     * 订单列表
     */
    private List<ShopifyOrderDto> orders;
}
