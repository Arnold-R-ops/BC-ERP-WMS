package com.wms.system.platform.service;

import com.wms.system.platform.dto.*;
import com.wms.system.platform.model.*;
import com.wms.system.platform.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAdminInvitationServiceTest {
    PlatformAdminInvitationRepository invitations=mock(PlatformAdminInvitationRepository.class);
    PlatformUserRepository users=mock(PlatformUserRepository.class);
    PlatformRoleRepository roles=mock(PlatformRoleRepository.class);
    PlatformUserRoleRepository userRoles=mock(PlatformUserRoleRepository.class);
    PlatformMfaService mfa=mock(PlatformMfaService.class);
    PlatformMfaCrypto crypto=mock(PlatformMfaCrypto.class);
    PasswordEncoder passwordEncoder=mock(PasswordEncoder.class);
    PlatformAccessGuard guard=mock(PlatformAccessGuard.class);
    PlatformAuditService audit=mock(PlatformAuditService.class);
    PlatformAdminInvitationService service;

    @BeforeEach void setUp(){service=new PlatformAdminInvitationService(invitations,users,roles,userRoles,mfa,crypto,passwordEncoder,guard,audit);}

    @Test void invitationStoresOnlyTokenHashAndReturnsRawLinkOnce(){
        PlatformUser actor=PlatformUser.builder().id(7L).normalizedEmail("owner@bcwms.com").build();
        PlatformAdminInvitationCreateRequest request=new PlatformAdminInvitationCreateRequest();
        request.setChallengeToken("reauth"); request.setPassword("current-password"); request.setCode("123456");
        request.setEmail(" Second@Example.com "); request.setDisplayName(" Second Owner "); request.setReason(" Business continuity ");
        when(users.findByNormalizedEmail("second@example.com")).thenReturn(Optional.empty());
        when(invitations.hasActiveInvitation(eq("second@example.com"),any())).thenReturn(false);
        when(mfa.verifyAdminInvitation("reauth","current-password","123456",null)).thenReturn(actor);
        when(crypto.newChallengeToken()).thenReturn("raw-one-time-token");
        when(crypto.hashToken("raw-one-time-token")).thenReturn("stored-hash");
        when(invitations.save(any())).thenAnswer(call->{PlatformAdminInvitation i=call.getArgument(0);i.setId(11L);i.setCreatedAt(OffsetDateTime.now());return i;});

        PlatformAdminInvitationResponse result=service.create(request,null);

        assertThat(result.activationPath()).isEqualTo("/platform.html#/activate?token=raw-one-time-token");
        verify(invitations).save(argThat(i->"stored-hash".equals(i.getTokenHash())&&!i.getTokenHash().contains("raw-one-time-token")
            &&"PLATFORM_SUPER_ADMIN".equals(i.getRoleCode())&&i.getExpiresAt().isBefore(OffsetDateTime.now().plusHours(25))));
        verify(audit).record(eq(7L),isNull(),eq("ADMIN_INVITED"),eq("platform_identity"),eq("SUCCESS"),anyMap(),isNull());
    }

    @Test void activationCreatesOnlySuperAdminThenRequiresMfaEnrollment(){
        PlatformAdminInvitation invitation=PlatformAdminInvitation.builder().id(11L).tokenHash("stored-hash")
            .normalizedEmail("second@example.com").displayName("Second Owner").roleCode("PLATFORM_SUPER_ADMIN")
            .invitedByPlatformUserId(7L).reason("Continuity").createdAt(OffsetDateTime.now())
            .expiresAt(OffsetDateTime.now().plusHours(2)).build();
        PlatformRole role=PlatformRole.builder().id(3L).roleCode("PLATFORM_SUPER_ADMIN").displayName("Super").build();
        PlatformInvitationActivateRequest request=new PlatformInvitationActivateRequest();
        request.setToken("raw-token"); request.setPassword("Strong-Password-42!");
        when(crypto.hashToken("raw-token")).thenReturn("stored-hash");
        when(invitations.findByTokenHashForUpdate("stored-hash")).thenReturn(Optional.of(invitation));
        when(users.findByNormalizedEmail("second@example.com")).thenReturn(Optional.empty());
        when(roles.findByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("Strong-Password-42!")).thenReturn("encoded");
        when(users.save(any())).thenAnswer(call->{PlatformUser u=call.getArgument(0);u.setId(22L);return u;});
        PlatformAuthResponse enrollment=new PlatformAuthResponse("MFA_ENROLLMENT_REQUIRED","challenge","secret","otpauth://setup",null,null,"second@example.com", List.of("PLATFORM_SUPER_ADMIN"),300_000L,null);
        when(mfa.startInvitedEnrollment(any())).thenReturn(enrollment);

        PlatformAuthResponse result=service.activate(request,null);

        assertThat(result.status()).isEqualTo("MFA_ENROLLMENT_REQUIRED");
        assertThat(invitation.getAcceptedPlatformUserId()).isEqualTo(22L);
        assertThat(invitation.getAcceptedAt()).isNotNull();
        verify(userRoles).saveAndFlush(argThat(link->link.getPlatformUserId().equals(22L)&&link.getPlatformRoleId().equals(3L)));
        verify(mfa).startInvitedEnrollment(argThat(user->!user.getMfaEnabled()&&"encoded".equals(user.getPasswordHash())));
        verify(audit).recordInCurrentTransaction(eq(22L),isNull(),eq("ADMIN_INVITATION_ACCEPTED"),eq("platform_identity"),eq("SUCCESS"),anyMap(),isNull());
    }

    @Test void firstReleaseRefusesAThirdActiveOrPendingSuperAdministrator(){
        PlatformAdminInvitationCreateRequest request=new PlatformAdminInvitationCreateRequest();
        request.setChallengeToken("reauth");request.setPassword("current-password");request.setCode("123456");
        request.setEmail("third@example.com");request.setDisplayName("Third Owner");request.setReason("Not in first release");
        when(users.findByNormalizedEmail("third@example.com")).thenReturn(Optional.empty());
        when(invitations.hasActiveInvitation(eq("third@example.com"),any())).thenReturn(false);
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(2L);

        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.create(request,null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most two");
        verifyNoInteractions(mfa);
        verify(invitations,never()).save(any());
    }
}
