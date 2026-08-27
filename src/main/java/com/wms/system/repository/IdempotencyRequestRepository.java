package com.wms.system.repository;

import com.wms.system.entity.IdempotencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, Long> {
    Optional<IdempotencyRequest> findByIdempotencyKey(String idempotencyKey);

    /**
     * Atomically reserves a company-scoped key. A concurrent insert for the
     * same company and key waits for the winner and then reports zero rows.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO idempotency_request (
            company_id, idempotency_key, operation, request_hash, status,
            locked_until, lease_token, created_at, updated_at
        ) VALUES (
            :companyId, :idempotencyKey, :operation, :requestHash, 'PROCESSING',
            :lockedUntil, :leaseToken, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        )
        ON CONFLICT (company_id, idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int insertReservation(
        @Param("companyId") Long companyId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("operation") String operation,
        @Param("requestHash") String requestHash,
        @Param("lockedUntil") LocalDateTime lockedUntil,
        @Param("leaseToken") String leaseToken
    );

    @Query(value = """
        SELECT *
        FROM idempotency_request
        WHERE company_id = :companyId
          AND idempotency_key = :idempotencyKey
        FOR UPDATE
        """, nativeQuery = true)
    Optional<IdempotencyRequest> findByCompanyAndKeyForUpdate(
        @Param("companyId") Long companyId,
        @Param("idempotencyKey") String idempotencyKey
    );

    @Query(value = """
        SELECT *
        FROM idempotency_request
        WHERE company_id = :companyId
          AND id = :id
        FOR UPDATE
        """, nativeQuery = true)
    Optional<IdempotencyRequest> findByCompanyAndIdForUpdate(
        @Param("companyId") Long companyId,
        @Param("id") Long id
    );
}
