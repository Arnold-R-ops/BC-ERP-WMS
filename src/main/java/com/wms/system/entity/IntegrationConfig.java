package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    /**
     * API Key（可选）
     * 某些平台可能需要
     */
    @Column(name = "api_key")
    private String apiKey;

    /**
     * 访问令牌
     * 用于 API 认证
     */
    @Column(name = "access_token", nullable = false, length = 500)
    private String accessToken;

    /**
     * 是否启用
     * true: 启用同步，false: 禁用同步
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 最后同步时间
     * 记录上次成功同步的时间
     */
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;
}
