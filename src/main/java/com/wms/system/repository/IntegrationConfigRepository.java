package com.wms.system.repository;

import com.wms.system.entity.IntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 集成配置 Repository
 */
@Repository
public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, Long> {

    /**
     * 查询指定平台的所有启用配置
     *
     * @param platform 平台类型（如 SHOPIFY）
     * @return 启用的配置列表
     */
    List<IntegrationConfig> findByPlatformAndIsActiveTrue(String platform);

    /**
     * 按店铺域名查启用配置（P1-B3：Webhook 按 X-Shopify-Shop-Domain 定位店铺）
     */
}
