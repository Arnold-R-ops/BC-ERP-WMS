package com.wms.system.repository;

import com.wms.system.entity.PendingSkuMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 待映射 SKU 队列 Repository（P1 批次2）
 */
@Repository
public interface PendingSkuMappingRepository extends JpaRepository<PendingSkuMapping, Long> {

    Optional<PendingSkuMapping> findByChannelAndExternalSku(String channel, String externalSku);

    List<PendingSkuMapping> findByStatusOrderByOccurrenceCountDesc(String status);

    List<PendingSkuMapping> findByChannelAndStatusOrderByOccurrenceCountDesc(String channel, String status);
}
