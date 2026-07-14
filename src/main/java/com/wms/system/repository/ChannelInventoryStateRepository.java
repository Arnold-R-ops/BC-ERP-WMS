package com.wms.system.repository;

import com.wms.system.entity.ChannelInventoryState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 渠道库存推送状态 Repository（P1 批次4）
 */
@Repository
public interface ChannelInventoryStateRepository extends JpaRepository<ChannelInventoryState, Long> {

    Optional<ChannelInventoryState> findByMappingId(Long mappingId);
}
