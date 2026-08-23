package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformAdminInvitationActivation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformAdminInvitationActivationRepository
        extends JpaRepository<PlatformAdminInvitationActivation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlatformAdminInvitationActivation a where a.tokenHash=:tokenHash")
    Optional<PlatformAdminInvitationActivation> findByTokenHashForUpdate(
        @Param("tokenHash") String tokenHash
    );

    List<PlatformAdminInvitationActivation> findByInvitationIdAndConsumedAtIsNull(Long invitationId);
}
