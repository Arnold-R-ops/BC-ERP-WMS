package com.wms.system.repository;

import com.wms.system.entity.ChannelRawEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 渠道原始报文 Repository（P1 批次1）
 */
@Repository
public interface ChannelRawEventRepository extends JpaRepository<ChannelRawEvent, Long> {

    /**
     * 按渠道+类型+外部标识查最近一条（去重判断用）
     */
    Optional<ChannelRawEvent> findFirstByChannelAndEventTypeAndExternalIdOrderByIdDesc(
        String channel, String eventType, String externalId);

    /**
     * 按渠道与状态分页查询（诊断/重放入口）
     */
    Page<ChannelRawEvent> findByChannelAndStatusOrderByIdDesc(String channel, String status, Pageable pageable);
}
