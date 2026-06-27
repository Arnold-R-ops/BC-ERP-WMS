package com.wms.system.repository;

import com.wms.system.entity.DomainOutbox;
import com.wms.system.entity.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DomainOutboxRepository extends JpaRepository<DomainOutbox, Long> {
    List<DomainOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
