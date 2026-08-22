package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 集成配置实体
 * 用于存储第三方平台（如 Shopify）的集成配置信息
 */
@Entity
@Table(name = "integration_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class IntegrationConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 平台类型
     * 默认值：SHOPIFY
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String platform = "SHOPIFY";

    /**
     * 店铺 URL
     * 例如：mystore.myshopify.com
     */
    @Column(name = "store_url", nullable = false)
    private String storeUrl;

    /** Canonical, globally routable channel endpoint identifier. */
    @Column(name = "canonical_store_identifier", nullable = false, length = 255)
    private String canonicalStoreIdentifier;

    /**
     * API Key（可选）
     * 某些平台可能需要
     */
    @Column(name = "api_key")
    private String apiKey;

    /**
     * OAuth 应用 Client ID（2026 起 Shopify dev dashboard 应用的标准凭据）。
     * 与 clientSecret 搭配，运行时通过 client_credentials 模式换取短效访问令牌。
     */
    @Column(name = "client_id", length = 100)
    private String clientId;

    /**
     * OAuth 应用 Client Secret（shpss_ 开头）。
     * 双重用途：① 换取访问令牌的材料；② Webhook HMAC 验签密钥。
     * 目前明文入库，凭证管理阶梯升级见 ROADMAP P3。
     */
    @Column(name = "client_secret", length = 200)
    private String clientSecret;

    /**
     * 静态访问令牌（旧模式，可选）。
     * 仅当 clientId/clientSecret 缺失时使用；新配置应优先用 client credentials。
     */
    @Column(name = "access_token", length = 500)
    private String accessToken;

    /**
     * 是否启用
     * true: 启用同步，false: 禁用同步
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Allows automatic creation of lightweight retail consumers during
     * automatic channel ingestion. It is deliberately disabled by default.
     */
    @Column(name = "retail_mode", nullable = false)
    @Builder.Default
    private Boolean retailMode = false;

    /**
     * 最后同步时间
     * 记录上次成功同步的时间
     */
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @PrePersist
    @PreUpdate
    void normalizeStoreIdentifier() {
        canonicalStoreIdentifier = canonicalizeStoreIdentifier(storeUrl);
        storeUrl = canonicalStoreIdentifier;
    }

    public static String canonicalizeStoreIdentifier(String value) {
        if (value == null) {
            return null;
        }
        return value.trim()
            .replaceFirst("(?i)^https?://", "")
            .replaceAll("/+$", "")
            .toLowerCase(Locale.ROOT);
    }
}
