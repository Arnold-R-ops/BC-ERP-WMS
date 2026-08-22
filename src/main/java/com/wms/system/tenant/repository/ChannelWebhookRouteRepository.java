package com.wms.system.tenant.repository;

import com.wms.system.tenant.model.ChannelWebhookRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelWebhookRouteRepository
        extends JpaRepository<ChannelWebhookRoute, Long> {

    Optional<ChannelWebhookRoute> findByPlatformAndCanonicalStoreIdentifierAndActiveTrue(
        String platform,
        String canonicalStoreIdentifier
    );
}
