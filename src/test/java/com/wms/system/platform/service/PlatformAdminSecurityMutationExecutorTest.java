package com.wms.system.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminSecurityMutationResponse;
import com.wms.system.platform.model.PlatformAdminCommand;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformAdminCommandRepository;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformAdminSecurityMutationExecutorTest {
    @Mock PlatformAdminCommandRepository commands;
    @Mock PlatformMfaChallengeRepository challenges;
    @Mock PlatformRoleRepository roles;
    @Mock PlatformUserRepository users;
    @Mock PlatformUserRoleRepository userRoles;
    @Mock PlatformAccessGrantRepository accessGrants;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PlatformMfaCrypto crypto;
    @Mock PlatformAuditService audit;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private PlatformAdminSecurityMutationExecutor executor;
    private PlatformUser actor;
    private PlatformUser target;
    private PlatformMfaChallenge challenge;
    private PlatformAdminCommand command;

    @BeforeEach
    void setUp() {
        PlatformMfaProperties properties = new PlatformMfaProperties();
        properties.setChallengeMinutes(5);
        properties.setMaxAttempts(5);
        properties.setLockMinutes(15);
        executor = new PlatformAdminSecurityMutationExecutor(
            commands, challenges, roles, users, userRoles, accessGrants,
            passwordEncoder, crypto, properties, audit, objectMapper);

        actor = user(7L, 11L);
        target = user(8L, 5L);
        target.setRecoveryCodeHashesJson("[\"recovery-hash\"]");
        target.setMfaEnrolledAt(OffsetDateTime.now().minusDays(20));
        challenge = challenge(PlatformAdminSecurityOperation.SESSION_REVOKE, 5L);
        command = PlatformAdminCommand.builder()
            .id(41L)
            .actorPlatformUserId(7L)
            .targetPlatformUserId(8L)
            .idempotencyKey("security-command-1")
            .action("ADMIN_SESSIONS_REVOKE")
            .status("IN_PROGRESS")
            .build();

        when(crypto.hashToken(anyString())).thenAnswer(invocation -> hash(invocation.getArgument(0)));
        when(commands.insertReservation(anyLong(), anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(1);
        when(commands.findByActorAndKeyForUpdate(7L, "security-command-1"))
            .thenReturn(Optional.of(command));
        when(challenges.findByTokenHashForUpdate(hash("challenge-token")))
            .thenReturn(Optional.of(challenge));
        when(roles.findByRoleCodeForUpdate("PLATFORM_SUPER_ADMIN")).thenReturn(Optional.of(
            PlatformRole.builder().id(3L).roleCode("PLATFORM_SUPER_ADMIN").displayName("Super").build()));
        when(users.findByIdForUpdate(8L)).thenReturn(Optional.of(target));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(actor));
        when(userRoles.findRoleCodesByPlatformUserId(7L)).thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        when(userRoles.findRoleCodesByPlatformUserId(8L))
            .thenReturn(List.of("PLATFORM_TENANT_WRITE", "HISTORICAL_UNKNOWN"));
        when(passwordEncoder.matches("current-password", "password-hash-7")).thenReturn(true);
        when(crypto.decrypt("encrypted-secret-7")).thenReturn("totp-secret");
        when(crypto.verifyTotp(eq("totp-secret"), eq("123456"), any())).thenReturn(true);
        when(accessGrants.countActiveByPlatformUserId(eq(8L), any())).thenReturn(3L);
    }

    @Test
    void revokesAllTargetSessionsWhilePreservingIdentityMfaRolesAndGrants() {
        String passwordHash = target.getPasswordHash();
        String mfaSecret = target.getMfaSecretEncrypted();
        String recoveryHashes = target.getRecoveryCodeHashesJson();
        OffsetDateTime enrolledAt = target.getMfaEnrolledAt();

        PlatformAdminSecurityMutationResponse result = execute(
            PlatformAdminSecurityOperation.SESSION_REVOKE, "Compromised laptop");

        assertThat(result.action()).isEqualTo("ADMIN_SESSIONS_REVOKED");
        assertThat(result.changed()).isTrue();
        assertThat(result.enabled()).isTrue();
        assertThat(result.mfaStatus()).isEqualTo("ENROLLED");
        assertThat(result.roles()).containsExactly("PLATFORM_TENANT_WRITE", "HISTORICAL_UNKNOWN");
        assertThat(result.activeGrantCount()).isEqualTo(3L);
        assertThat(result.securityVersion()).isEqualTo(6L);
        assertThat(target.getEnabled()).isTrue();
        assertThat(target.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(target.getMfaSecretEncrypted()).isEqualTo(mfaSecret);
        assertThat(target.getRecoveryCodeHashesJson()).isEqualTo(recoveryHashes);
        assertThat(target.getMfaEnrolledAt()).isEqualTo(enrolledAt);
        assertThat(challenge.getConsumedAt()).isNotNull();
        assertThat(command.getSafeResponseJson()).doesNotContain(
            "current-password", "123456", "challenge-token", "totp-secret", "security-command-1");
        verify(challenges).deletePendingOwnedByPlatformUserId(8L);
        verify(audit).recordAdminInCurrentTransaction(
            eq(7L), eq(8L), eq("ADMIN_SESSIONS_REVOKED"), eq("platform_admin_sessions"),
            eq("SUCCESS"), any(), eq(null));

        InOrder lockOrder = inOrder(roles, users);
        lockOrder.verify(roles).findByRoleCodeForUpdate("PLATFORM_SUPER_ADMIN");
        lockOrder.verify(users).findByIdForUpdate(8L);
        lockOrder.verify(users).findByIdForUpdate(7L);
    }

    @Test
    void resetMfaClearsOnlyTargetAuthenticatorStateAndRevokesSessions() {
        challenge = challenge(PlatformAdminSecurityOperation.MFA_RESET, 5L);
        command.setAction("ADMIN_MFA_RESET");
        when(challenges.findByTokenHashForUpdate(hash("challenge-token")))
            .thenReturn(Optional.of(challenge));
        when(userRoles.findRoleCodesByPlatformUserId(8L))
            .thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        String passwordHash = target.getPasswordHash();

        PlatformAdminSecurityMutationResponse result = execute(
            PlatformAdminSecurityOperation.MFA_RESET, "Lost authenticator");

        assertThat(result.action()).isEqualTo("MFA_RESET");
        assertThat(result.mfaStatus()).isEqualTo("NOT_ENROLLED");
        assertThat(result.securityVersion()).isEqualTo(6L);
        assertThat(target.getEnabled()).isTrue();
        assertThat(target.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(target.getMfaEnabled()).isFalse();
        assertThat(target.getMfaSecretEncrypted()).isNull();
        assertThat(target.getRecoveryCodeHashesJson()).isNull();
        assertThat(target.getMfaEnrolledAt()).isNull();
        assertThat(target.getMfaFailedAttempts()).isZero();
        assertThat(target.getMfaLockedUntil()).isNull();
        verify(audit).recordAdminInCurrentTransaction(
            eq(7L), eq(8L), eq("MFA_RESET"), eq("platform_admin_mfa"),
            eq("SUCCESS"), any(), eq(null));
    }

    @Test
    void sessionRevocationAllowsAnEnabledTargetWithoutMfaBinding() {
        target.setMfaEnabled(false);
        target.setMfaSecretEncrypted(null);
        target.setRecoveryCodeHashesJson(null);
        target.setMfaEnrolledAt(null);

        PlatformAdminSecurityMutationResponse result = execute(
            PlatformAdminSecurityOperation.SESSION_REVOKE, "Invalidate stale browser tokens");

        assertThat(result.mfaStatus()).isEqualTo("NOT_ENROLLED");
        assertThat(result.securityVersion()).isEqualTo(6L);
        assertThat(target.getMfaEnabled()).isFalse();
        assertThat(target.getMfaSecretEncrypted()).isNull();
    }

    @Test
    void disabledTargetIsStableConflictForBothOperations() {
        target.setEnabled(false);

        assertThatThrownBy(() -> execute(
            PlatformAdminSecurityOperation.SESSION_REVOKE, "Compromised laptop"))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_DISABLED));

        assertThat(target.getSecurityVersion()).isEqualTo(5L);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(commands).delete(command);
    }

    @Test
    void staleTargetVersionFailsBeforeCredentialVerification() {
        target.setSecurityVersion(6L);

        assertThatThrownBy(() -> execute(
            PlatformAdminSecurityOperation.SESSION_REVOKE, "Compromised laptop"))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_CONCURRENT_MODIFICATION));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(commands).delete(command);
    }

    @Test
    void fifthCredentialFailureLocksActorWithoutChangingTarget() {
        actor.setMfaFailedAttempts(4);
        when(passwordEncoder.matches("current-password", "password-hash-7")).thenReturn(false);

        assertThatThrownBy(() -> execute(
            PlatformAdminSecurityOperation.SESSION_REVOKE, "Compromised laptop"))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.AUTH_FAILED);
                assertThat(exception.getParam("mfaReason")).isEqualTo("TEMPORARILY_LOCKED");
            });

        assertThat(actor.getMfaFailedAttempts()).isEqualTo(5);
        assertThat(actor.getMfaLockedUntil()).isAfter(OffsetDateTime.now());
        assertThat(challenge.getAttemptCount()).isEqualTo(1);
        assertThat(target.getSecurityVersion()).isEqualTo(5L);
        verify(commands).delete(command);
    }

    @Test
    void successfulReplayReturnsStoredSafeResponseWithoutSecondVersionAdvance() throws Exception {
        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(2);
        PlatformAdminSecurityMutationResponse stored = new PlatformAdminSecurityMutationResponse(
            8L, "ADMIN_SESSIONS_REVOKED", true, true, "ENROLLED",
            List.of("HISTORICAL_UNKNOWN"), 3L, 6L, completedAt);
        command.setRequestFingerprint(hash("ADMIN_SESSIONS_REVOKE\n8\nCompromised laptop"));
        command.setStatus("SUCCEEDED");
        command.setSafeResponseJson(objectMapper.writeValueAsString(stored));
        command.setCompletedAt(completedAt);
        when(commands.insertReservation(anyLong(), anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(0);

        PlatformAdminSecurityMutationResponse result = execute(
            PlatformAdminSecurityOperation.SESSION_REVOKE, "Compromised laptop");

        assertThat(result).isEqualTo(stored);
        assertThat(target.getSecurityVersion()).isEqualTo(5L);
        verify(challenges, never()).findByTokenHashForUpdate(anyString());
        verify(roles, never()).findByRoleCodeForUpdate(anyString());
        verify(audit, never()).recordAdminInCurrentTransaction(
            anyLong(), anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void sameIdempotencyKeyCannotBeReusedForMfaReset() {
        command.setRequestFingerprint(hash("ADMIN_SESSIONS_REVOKE\n8\nCompromised laptop"));
        when(commands.insertReservation(anyLong(), anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(0);

        assertThatThrownBy(() -> execute(
            PlatformAdminSecurityOperation.MFA_RESET, "Lost authenticator"))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_IDEMPOTENCY_CONFLICT));

        verify(challenges, never()).findByTokenHashForUpdate(anyString());
    }

    private PlatformAdminSecurityMutationResponse execute(
        PlatformAdminSecurityOperation operation,
        String reason
    ) {
        return executor.execute(
            7L,
            8L,
            operation,
            "security-command-1",
            reason,
            "challenge-token",
            "current-password",
            "123456",
            null
        );
    }

    private PlatformMfaChallenge challenge(
        PlatformAdminSecurityOperation operation,
        long targetSecurityVersion
    ) {
        return PlatformMfaChallenge.builder()
            .id(31L)
            .tokenHash(hash("challenge-token"))
            .platformUserId(7L)
            .targetPlatformUserId(8L)
            .purpose(operation.purpose())
            .actionContextHash(hash(operation.commandAction() + "\n8"))
            .targetSecurityVersion(targetSecurityVersion)
            .attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .build();
    }

    private PlatformUser user(Long id, long securityVersion) {
        return PlatformUser.builder()
            .id(id)
            .normalizedEmail("admin-" + id + "@example.com")
            .displayName("Administrator " + id)
            .passwordHash("password-hash-" + id)
            .enabled(true)
            .securityVersion(securityVersion)
            .mfaEnabled(true)
            .mfaSecretEncrypted("encrypted-secret-" + id)
            .mfaFailedAttempts(0)
            .build();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
