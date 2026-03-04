package com.wms.system.dto.shopify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Shopify 客户 DTO
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyCustomerDto {

    /**
     * Shopify 客户 ID
     */
    private Long id;

    /**
     * 客户邮箱
     */
    private String email;

    /**
     * 客户名字
     */
    @JsonProperty("first_name")
    private String firstName;

    /**
     * 客户姓氏
     */
    @JsonProperty("last_name")
    private String lastName;

    /**
     * 客户电话
     */
    private String phone;
}
