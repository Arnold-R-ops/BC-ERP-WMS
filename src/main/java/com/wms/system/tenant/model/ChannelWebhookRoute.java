package com.wms.system.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Minimal control-plane route used before a webhook company is established. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "channel_webhook_routes")
public class ChannelWebhookRoute {

    @Id
    @Column(name = "integration_config_id", nullable = false)
    private Long integrationConfigId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "platform", nullable = false, length = 50)
    private String platform;

    @Column(name = "canonical_store_identifier", nullable = false, length = 255)
    private String canonicalStoreIdentifier;

    @Column(name = "signing_secret", nullable = false, length = 200)
    private String signingSecret;

    @Column(name = "active", nullable = false)
    private boolean active;
}
