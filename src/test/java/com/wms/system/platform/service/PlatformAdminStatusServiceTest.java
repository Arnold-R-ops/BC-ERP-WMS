package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminStatusMutationRequest;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAdminInvitationRepository;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import com.wms.system.security.PlatformSecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformAdminStatusServiceTest {
    @Mock PlatformAccessGuard guard;
    @Mock PlatformUserRepository users;
    @Mock PlatformUserRoleRepository userRoles;
    @Mock PlatformAdminInvitationRepository invitations;
    @Mock PlatformMfaChallengeRepository challenges;
    @Mock PlatformMfaCrypto crypto;
    @Mock PlatformAuditService audit;
    @Mock PlatformAdminStatusMutationExecutor executor;

    private PlatformAdminStatusService service;
    private PlatformUser actor;
    private PlatformUser target;

    @BeforeEach
    void setUp() {
        PlatformMfaProperties properties = new PlatformMfaProperties();
        properties.setChallengeMinutes(5);
        properties.setMaxAttempts(5);
        properties.setLockMinutes(15);
        service = new PlatformAdminStatusService(
            guard, users, userRoles, invitations, challenges, crypto, properties, audit, executor);
        actor = PlatformUser.builder()
            .id(7L).normalizedEmail("actor@example.com").displayName("Actor")
            .passwordHash("hash").enabled(true).securityVersion(9L)
            .mfaEnabled(true).mfaSecretEncrypted("encrypted").mfaFailedAttempts(0).build();
        target = PlatformUser.builder()
            .id(8L).normalizedEmail("target@example.com").displayName("Target")
            .passwordHash("hash").enabled(true).securityVersion(5L)
            .mfaEnabled(true).mfaSecretEncrypted("encrypted-target").mfaFailedAttempts(0).build();
        when(guard.requireSuperAdmin()).thenReturn(new PlatformSecurityUser(actor));
        when(users.findById(7L)).thenReturn(Optional.of(actor));
        when(users.findById(8L)).thenReturn(Optional.of(target));
        when(users.existsById(8L)).thenReturn(true);
        when(userRoles.findRoleCodesByPlatformUserId(8L)).thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        when(crypto.newChallengeToken()).thenReturn("raw-challenge");
        when(crypto.hashToken(anyString())).thenAnswer(invocation -> "h:" + invocation.getArgument(0));
        when(challenges.saveAndFlush(any())).thenAnswer(invocation -> {
            PlatformMfaChallenge challenge = invocation.getArgument(0);
            challenge.setId(31L);
            return challenge;
        });
    }

    @Test
    void challengeBindsActorTargetDesiredStateAndSecurityVersionWithoutSecretsInAudit() {
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(2L);
        MockHttpServletRequest http = new MockHttpServletRequest();

        var response = service.startChallenge(8L, false, http);

        assertThat(response.challengeToken()).isEqualTo("raw-challenge");
        assertThat(response.expiresIn()).isEqualTo(300_000L);
        assertThat(response.targetSecurityVersion()).isEqualTo(5L);
        ArgumentCaptor<PlatformMfaChallenge> challengeCaptor = ArgumentCaptor.forClass(PlatformMfaChallenge.class);
        verify(challenges).saveAndFlush(challengeCaptor.capture());
        PlatformMfaChallenge stored = challengeCaptor.getValue();
        assertThat(stored.getTokenHash()).isEqualTo("h:raw-challenge");
        assertThat(stored.getPlatformUserId()).isEqualTo(7L);
        assertThat(stored.getTargetPlatformUserId()).isEqualTo(8L);
        assertThat(stored.getPurpose()).isEqualTo("ADMIN_STATUS_CHANGE");
        assertThat(stored.getActionContextHash()).isEqualTo("h:ADMIN_STATUS_CHANGE\n8\nfalse");
        assertThat(stored.getTargetSecurityVersion()).isEqualTo(5L);
        verify(audit).recordAdmin(eq(7L), eq(8L), eq("ADMIN_DISABLED"),
            eq("platform_admin_status"), eq("REQUESTED"), any(Map.class), eq(http));
        assertThat(stored.toString()).doesNotContain("raw-challenge");
    }

    @Test
    void challengeRefusesLastEnabledSuperAdministratorBeforeIssuingToken() {
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.startChallenge(8L, false, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_LAST_SUPER_ADMIN));

        verify(challenges, never()).save(any());
        verify(crypto, never()).newChallengeToken();
    }

    @Test
    void challengeRefusesReenableForHistoricalWriteRole() {
        target.setEnabled(false);
        when(userRoles.findRoleCodesByPlatformUserId(8L))
            .thenReturn(List.of("PLATFORM_TENANT_READ", "PLATFORM_TENANT_WRITE"));

        assertThatThrownBy(() -> service.startChallenge(8L, true, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_ENABLE_REVIEW_REQUIRED));

        verify(challenges, never()).save(any());
    }

    @Test
    void finalAuthenticationFailureIsAuditedAfterMutationTransactionReturns() {
        PlatformAdminStatusMutationRequest request = new PlatformAdminStatusMutationRequest();
        request.setChallengeToken("raw-challenge");
        request.setPassword("hidden-password");
        request.setCode("123456");
        request.setReason(" Compromised device ");
        when(executor.execute(eq(7L), eq(8L), eq(false), eq("command-key"),
            eq("Compromised device"), eq(request), any()))
            .thenThrow(new BusinessException(ErrorKeys.AUTH_FAILED,
                Map.of("reason", "MFA_VERIFICATION_FAILED")));

        assertThatThrownBy(() -> service.changeStatus(
            8L, false, " command-key ", request, new MockHttpServletRequest()))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.AUTH_FAILED));

        verify(audit).recordAdmin(eq(7L), eq(8L), eq("ADMIN_DISABLED"),
            eq("platform_admin_status"), eq("FAILED"), any(Map.class), any());
        verify(audit).record(eq(7L), eq(null), eq("MFA_FAILED"), eq("platform_identity"),
            eq("FAILED"), any(Map.class), any());
        verify(executor).execute(eq(7L), eq(8L), eq(false), eq("command-key"),
            eq("Compromised device"), eq(request), any());
    }
}
