package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformAdminInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.*;

public interface PlatformAdminInvitationRepository extends JpaRepository<PlatformAdminInvitation,Long>{
    Optional<PlatformAdminInvitation> findByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PlatformAdminInvitation i where i.tokenHash=:tokenHash")
    Optional<PlatformAdminInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
    @Query("select count(i)>0 from PlatformAdminInvitation i where i.normalizedEmail=:email and i.acceptedAt is null and i.revokedAt is null and i.expiresAt>:now")
    boolean hasActiveInvitation(@Param("email") String email,@Param("now") OffsetDateTime now);
    @Query("select count(i) from PlatformAdminInvitation i where i.roleCode=:roleCode and i.acceptedAt is null and i.revokedAt is null and i.expiresAt>:now")
    long countActiveByRoleCode(@Param("roleCode") String roleCode,@Param("now") OffsetDateTime now);
    List<PlatformAdminInvitation> findAllByOrderByCreatedAtDesc();
}
