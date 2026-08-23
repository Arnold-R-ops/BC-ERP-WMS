package com.wms.system.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "platform_admin_invitations",
    uniqueConstraints = @UniqueConstraint(name = "uk_platform_admin_invitation_token", columnNames = "token_hash"))
public class PlatformAdminInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @Column(name="normalized_email",nullable=false,length=254) private String normalizedEmail;
    @Column(name="display_name",nullable=false,length=100) private String displayName;
    @Column(name="invitation_type",nullable=false,length=20) private String invitationType;
    @Column(name="role_code",nullable=false,length=50) private String roleCode;
    @Column(name="invited_by_platform_user_id",nullable=false) private Long invitedByPlatformUserId;
    @Column(nullable=false,length=500) private String reason;
    @Column(name="expires_at",nullable=false) private OffsetDateTime expiresAt;
    @Column(name="accepted_at") private OffsetDateTime acceptedAt;
    @Column(name="accepted_platform_user_id") private Long acceptedPlatformUserId;
    @Column(name="revoked_at") private OffsetDateTime revokedAt;
    @Column(name="revoked_by_platform_user_id") private Long revokedByPlatformUserId;
    @Column(name="created_at",nullable=false,updatable=false) private OffsetDateTime createdAt;
    @PrePersist void initialize(){if(createdAt==null)createdAt=OffsetDateTime.now();}
}
