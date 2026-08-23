package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformAdminCommand;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformAdminCommandRepository extends JpaRepository<PlatformAdminCommand, Long> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO platform_admin_commands (
            actor_platform_user_id, target_platform_user_id, idempotency_key,
            action, request_fingerprint, status, created_at
        ) VALUES (
            :actorId, :targetId, :idempotencyKey,
            :action, :requestFingerprint, 'IN_PROGRESS', CURRENT_TIMESTAMP
        )
        ON CONFLICT (actor_platform_user_id, idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int insertReservation(
        @Param("actorId") Long actorId,
        @Param("targetId") Long targetId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("action") String action,
        @Param("requestFingerprint") String requestFingerprint
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PlatformAdminCommand c " +
        "where c.actorPlatformUserId=:actorId and c.idempotencyKey=:idempotencyKey")
    Optional<PlatformAdminCommand> findByActorAndKeyForUpdate(
        @Param("actorId") Long actorId,
        @Param("idempotencyKey") String idempotencyKey
    );
}
