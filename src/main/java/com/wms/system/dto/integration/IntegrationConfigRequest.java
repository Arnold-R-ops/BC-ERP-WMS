package com.wms.system.dto.integration;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店铺集成配置请求（P1 批次1）
 *
 * 更新语义：clientSecret / accessToken 传空或不传 = 保留原值（避免
 * 每次编辑都要重新粘贴密钥）；传非空 = 覆盖。
 *
 * @author WMS Team
 * @since 2026-07-10
 * @version P1-B1 (Channel Integration Foundation)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationConfigRequest {

    /**
     * 平台：SHOPIFY（默认）/ 将来 AMAZON 等
     */
    @Builder.Default
    private String platform = "SHOPIFY";

    /**
     * 店铺内部域名，如 xxx.myshopify.com（不带 https://）
     */
    @NotBlank(message = "storeUrl cannot be blank")
    private String storeUrl;

    /**
     * OAuth Client ID（推荐的认证方式，与 clientSecret 搭配）
     */
    private String clientId;

    /**
     * OAuth Client Secret（shpss_ 开头；更新时留空 = 保留原值）
     */
    private String clientSecret;

    /**
     * 静态访问令牌（旧模式，可选；更新时留空 = 保留原值）
     */
    private String accessToken;

    /**
     * 是否启用同步
     */
    @Builder.Default
    private Boolean isActive = true;
}
