package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformAdminInvitationActiveEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAdminInvitationActiveEmailRepository
        extends JpaRepository<PlatformAdminInvitationActiveEmail, String> {
    @Modifying
    @Query(value = "insert into platform_admin_invitation_active_emails" +
        "(normalized_email, invitation_id, created_at) values (:email, :invitationId, current_timestamp) " +
        "on conflict (normalized_email) do nothing", nativeQuery = true)
    int reserve(@Param("email") String normalizedEmail, @Param("invitationId") Long invitationId);

    @Modifying
    @Query("delete from PlatformAdminInvitationActiveEmail e where e.normalizedEmail=:email and e.invitationId=:invitationId")
    int release(@Param("email") String normalizedEmail, @Param("invitationId") Long invitationId);
}
