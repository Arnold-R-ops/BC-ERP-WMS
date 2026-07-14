package com.wms.system.repository;

import com.wms.system.entity.ChannelSkuMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 渠道 SKU 映射 Repository（P1 批次2）
 */
@Repository
public interface ChannelSkuMappingRepository extends JpaRepository<ChannelSkuMapping, Long> {

    /**
     * 第一层：原文精确命中
     */
    Optional<ChannelSkuMapping> findByChannelAndExternalSkuAndStatus(String channel, String externalSku, String status);

    /**
     * 第二层：归一化命中（大写去空白后相等）
     */
    Optional<ChannelSkuMapping> findFirstByChannelAndNormalizedSkuAndStatus(String channel, String normalizedSku, String status);

    List<ChannelSkuMapping> findByChannelOrderByIdDesc(String channel);

    boolean existsByChannelAndExternalSku(String channel, String externalSku);

    /**
     * 库存回写扫描对象（P1-B4）：某渠道全部生效的商品映射
     */
    List<ChannelSkuMapping> findByChannelAndMappingTypeAndStatus(String channel, String mappingType, String status);
}
