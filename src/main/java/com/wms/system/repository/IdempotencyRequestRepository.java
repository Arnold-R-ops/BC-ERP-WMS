package com.wms.system.repository;

import com.wms.system.entity.IdempotencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, Long> {
    Optional<IdempotencyRequest> findByIdempotencyKey(String idempotencyKey);
}
