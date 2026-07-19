package com.wms.system.dto.shopify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Shopify 订单 DTO
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyOrderDto {

    /**
     * Shopify 订单 ID
     */
    private Long id;

    /**
     * 订单号（如 #1001）
     */
    private String name;

    /**
     * 客户邮箱
     */
    private String email;

    @JsonProperty("contact_email")
    private String contactEmail;

    /**
     * 支付状态（paid, pending, refunded 等）
     */
    @JsonProperty("financial_status")
    private String financialStatus;

    /**
     * 发货状态（fulfilled, partial, null 等）
     */
    @JsonProperty("fulfillment_status")
    private String fulfillmentStatus;

    /**
     * 订单总额（字符串格式）
     */
    @JsonProperty("total_price")
    private String totalPrice;

    /**
     * 客户信息
     */
    private ShopifyCustomerDto customer;

    @JsonProperty("shipping_address")
    private ShopifyAddressDto shippingAddress;

    /**
     * 订单明细列表
     */
    @JsonProperty("line_items")
    private List<ShopifyLineItemDto> lineItems;

    /**
     * 创建时间（ISO 8601 格式）
     */
    @JsonProperty("created_at")
    private String createdAt;
}
