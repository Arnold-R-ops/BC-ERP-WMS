package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminMfaResetRequest;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformAdminSecurityServiceTest {
    @Mock PlatformAccessGuard guard;
    @Mock PlatformUserRepository users;
    @Mock PlatformMfaChallengeRepository challenges;
    @Mock PlatformMfaCrypto crypto;
    @Mock PlatformAuditService audit;
    @Mock PlatformAdminSecurityMutationExecutor executor;

    private PlatformAdminSecurityService service;
    private PlatformUser actor;
    private PlatformUser target;

    @BeforeEach
    void setUp() {
        PlatformMfaProperties properties = new PlatformMfaProperties();
        properties.setChallengeMinutes(5);
        properties.setMaxAttempts(5);
        properties.setLockMinutes(15);
        service = new PlatformAdminSecurityService(
            guard, users, challenges, crypto, properties, audit, executor);
        actor = user(7L, true, true, 9L);
        target = user(8L, true, false, 5L);
        when(guard.requireSuperAdmin()).thenReturn(new PlatformSecurityUser(actor));
        when(users.findById(7L)).thenReturn(Optional.of(actor));
        when(users.findById(8L)).thenReturn(Optional.of(target));
        when(users.existsById(8L)).thenReturn(true);
        when(crypto.newChallengeToken()).thenReturn("raw-challenge");
        when(crypto.hashToken(anyString())).thenAnswer(invocation -> "h:" + invocation.getArgument(0));
        when(challenges.saveAndFlush(any())).thenAnswer(invocation -> {
            PlatformMfaChallenge challenge = invocation.getArgument(0);
            challenge.setId(31L);
            return challenge;
        });
    }

    @Test
    void sessionChallengeBindsActorTargetActionAndVersionWithoutRequiringTargetMfa() {
        MockHttpServletRequest http = new MockHttpServletRequest();

        var response = service.startSessionRevoke(8L, http);

        assertThat(response.challengeToken()).isEqualTo("raw-challenge");
        assertThat(response.expiresIn()).isEqualTo(300_000L);
        assertThat(response.targetSecurityVersion()).isEqualTo(5L);
        ArgumentCaptor<PlatformMfaChallenge> captor = ArgumentCaptor.forClass(PlatformMfaChallenge.class);
        verify(challenges).saveAndFlush(captor.capture());
        PlatformMfaChallenge stored = captor.getValue();
        assertThat(stored.getPlatformUserId()).isEqualTo(7L);
        assertThat(stored.getTargetPlatformUserId()).isEqualTo(8L);
        assertThat(stored.getPurpose()).isEqualTo("ADMIN_SESSIONS_REVOKE");
        assertThat(stored.getActionContextHash()).isEqualTo("h:ADMIN_SESSIONS_REVOKE\n8");
        assertThat(stored.getTargetSecurityVersion()).isEqualTo(5L);
        verify(audit).recordAdmin(
            eq(7L), eq(8L), eq("ADMIN_SESSIONS_REVOKED"), eq("platform_admin_sessions"),
            eq("REQUESTED"), any(Map.class), eq(http));
    }

    @Test
    void mfaResetChallengeRequiresEnabledTargetWithActiveBinding() {
        assertThatThrownBy(() -> service.startMfaReset(8L, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_MFA_NOT_ENROLLED));

        target.setMfaEnabled(true);
        target.setMfaSecretEncrypted("target-secret");
        target.setEnabled(false);
        assertThatThrownBy(() -> service.startMfaReset(8L, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_DISABLED));

        verify(challenges, never()).saveAndFlush(any());
    }

    @Test
    void targetSecurityEndpointsCannotBeUsedOnTheCurrentAdministrator() {
        assertThatThrownBy(() -> service.startSessionRevoke(7L, null))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(challenges, never()).saveAndFlush(any());
        verify(audit).recordAdmin(
            eq(7L), eq(7L), eq("ADMIN_SESSIONS_REVOKED"), eq("platform_admin_sessions"),
            eq("FAILED"), any(Map.class), eq(null));
    }

    @Test
    void finalMfaFailureIsAuditedWithoutExposingCredentials() {
        PlatformAdminMfaResetRequest request = new PlatformAdminMfaResetRequest();
        request.setChallengeToken("raw-challenge");
        request.setPassword("hidden-password");
        request.setCode("123456");
        request.setReason(" Lost authenticator ");
        when(crypto.hashToken("command-key")).thenReturn("a".repeat(64));
        when(executor.execute(
            eq(7L), eq(8L), eq(PlatformAdminSecurityOperation.MFA_RESET), eq("command-key"),
            eq("Lost authenticator"), eq("raw-challenge"), eq("hidden-password"), eq("123456"), any()))
            .thenThrow(new BusinessException(
                ErrorKeys.AUTH_FAILED,
                Map.of("reason", "MFA_VERIFICATION_FAILED")
            ));

        assertThatThrownBy(() -> service.resetMfa(
            8L, " command-key ", request, new MockHttpServletRequest()))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.AUTH_FAILED));

        ArgumentCaptor<Map<String, ?>> detail = mapCaptor();
        verify(audit).recordAdmin(
            eq(7L), eq(8L), eq("MFA_RESET"), eq("platform_admin_mfa"),
            eq("FAILED"), detail.capture(), any());
        assertThat(detail.getValue().toString())
            .doesNotContain("hidden-password", "123456", "raw-challenge", "command-key")
            .contains("Lost authenticator", "idempotencyKeyHash");
        verify(audit).record(
            eq(7L), eq(null), eq("MFA_FAILED"), eq("platform_identity"),
            eq("FAILED"), any(Map.class), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, ?>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private PlatformUser user(Long id, boolean enabled, boolean mfaEnabled, long securityVersion) {
        return PlatformUser.builder()
            .id(id)
            .normalizedEmail("admin-" + id + "@example.com")
            .displayName("Administrator " + id)
            .passwordHash("password-hash-" + id)
            .enabled(enabled)
            .securityVersion(securityVersion)
            .mfaEnabled(mfaEnabled)
            .mfaSecretEncrypted(mfaEnabled ? "encrypted-secret-" + id : null)
            .mfaFailedAttempts(0)
            .build();
    }
}
