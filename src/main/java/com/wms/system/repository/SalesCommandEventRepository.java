package com.wms.system.repository;

import com.wms.system.entity.SalesCommandEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesCommandEventRepository extends JpaRepository<SalesCommandEvent, Long> {
    Optional<SalesCommandEvent> findByIdempotencyKey(String idempotencyKey);
}
