package com.wms.system.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminMutationResponse;
import com.wms.system.platform.dto.PlatformAdminStatusMutationRequest;
import com.wms.system.platform.model.PlatformAdminCommand;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformAdminCommandRepository;
import com.wms.system.platform.repository.PlatformAdminInvitationRepository;
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
class PlatformAdminStatusMutationExecutorTest {
    @Mock PlatformAdminCommandRepository commands;
    @Mock PlatformMfaChallengeRepository challenges;
    @Mock PlatformRoleRepository roles;
    @Mock PlatformUserRepository users;
    @Mock PlatformUserRoleRepository userRoles;
    @Mock PlatformAdminInvitationRepository invitations;
    @Mock PlatformAccessGrantRepository accessGrants;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PlatformMfaCrypto crypto;
    @Mock PlatformAuditService audit;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private PlatformMfaProperties properties;
    private PlatformAdminStatusMutationExecutor executor;
    private PlatformUser actor;
    private PlatformUser target;
    private PlatformMfaChallenge challenge;
    private PlatformAdminCommand command;
    private PlatformAdminStatusMutationRequest request;

    @BeforeEach
    void setUp() {
        properties = new PlatformMfaProperties();
        properties.setChallengeMinutes(5);
        properties.setMaxAttempts(5);
        properties.setLockMinutes(15);
        executor = new PlatformAdminStatusMutationExecutor(
            commands, challenges, roles, users, userRoles, invitations, accessGrants,
            passwordEncoder, crypto, properties, audit, objectMapper);

        actor = user(7L, true, 11L);
        target = user(8L, true, 5L);
        challenge = PlatformMfaChallenge.builder()
            .id(31L)
            .tokenHash(hash("challenge-token"))
            .platformUserId(7L)
            .targetPlatformUserId(8L)
            .purpose("ADMIN_STATUS_CHANGE")
            .actionContextHash(hash("ADMIN_STATUS_CHANGE\n8\nfalse"))
            .targetSecurityVersion(5L)
            .attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .build();
        command = PlatformAdminCommand.builder()
            .id(41L)
            .actorPlatformUserId(7L)
            .targetPlatformUserId(8L)
            .idempotencyKey("status-command-1")
            .action("ADMIN_DISABLE")
            .status("IN_PROGRESS")
            .build();
        request = request("Security incident");

        when(crypto.hashToken(anyString())).thenAnswer(invocation -> hash(invocation.getArgument(0)));
        when(commands.insertReservation(anyLong(), anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(commands.findByActorAndKeyForUpdate(7L, "status-command-1")).thenReturn(Optional.of(command));
        when(challenges.findByTokenHashForUpdate(hash("challenge-token"))).thenReturn(Optional.of(challenge));
        when(roles.findByRoleCodeForUpdate("PLATFORM_SUPER_ADMIN")).thenReturn(Optional.of(
            PlatformRole.builder().id(3L).roleCode("PLATFORM_SUPER_ADMIN").displayName("Super").build()));
        when(users.findByIdForUpdate(8L)).thenReturn(Optional.of(target));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(actor));
        when(userRoles.findRoleCodesByPlatformUserId(7L)).thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        when(userRoles.findRoleCodesByPlatformUserId(8L)).thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        when(passwordEncoder.matches("current-password", "password-hash-7")).thenReturn(true);
        when(crypto.decrypt("encrypted-secret-7")).thenReturn("totp-secret");
        when(crypto.verifyTotp(eq("totp-secret"), eq("123456"), any())).thenReturn(true);
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(2L);
        when(accessGrants.countActiveByPlatformUserId(eq(8L), any())).thenReturn(3L);
    }

    @Test
    void disablesTargetAtomicallyAfterTakingSeatThenTargetThenActorLocks() {
        PlatformAdminMutationResponse result = executor.execute(
            7L, 8L, false, "status-command-1", "Security incident", request, null);

        assertThat(result.changed()).isTrue();
        assertThat(result.enabled()).isFalse();
        assertThat(result.action()).isEqualTo("ADMIN_DISABLED");
        assertThat(result.securityVersion()).isEqualTo(6L);
        assertThat(result.activeGrantCount()).isEqualTo(3L);
        assertThat(target.getEnabled()).isFalse();
        assertThat(target.getSecurityVersion()).isEqualTo(6L);
        assertThat(challenge.getConsumedAt()).isNotNull();
        assertThat(command.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(command.getSafeResponseJson()).doesNotContain(
            "current-password", "123456", "challenge-token", "totp-secret");
        verify(challenges).deletePendingOwnedByPlatformUserId(8L);
        verify(audit).recordAdminInCurrentTransaction(
            eq(7L), eq(8L), eq("ADMIN_DISABLED"), eq("platform_admin_status"),
            eq("SUCCESS"), any(), eq(null));

        InOrder lockOrder = inOrder(roles, users);
        lockOrder.verify(roles).findByRoleCodeForUpdate("PLATFORM_SUPER_ADMIN");
        lockOrder.verify(users).findByIdForUpdate(8L);
        lockOrder.verify(users).findByIdForUpdate(7L);
    }

    @Test
    void enablesKnownReadAccountButAdvancesVersionSoOldSessionsCannotReturn() {
        target.setEnabled(false);
        target.setMfaEnrolledAt(OffsetDateTime.now().minusDays(30));
        String encryptedMfa = target.getMfaSecretEncrypted();
        challenge.setActionContextHash(hash("ADMIN_STATUS_CHANGE\n8\ntrue"));
        command.setAction("ADMIN_ENABLE");
        when(userRoles.findRoleCodesByPlatformUserId(8L)).thenReturn(List.of("PLATFORM_TENANT_READ"));

        PlatformAdminMutationResponse result = executor.execute(
            7L, 8L, true, "status-command-1", "Return from leave", request, null);

        assertThat(result.changed()).isTrue();
        assertThat(result.enabled()).isTrue();
        assertThat(result.action()).isEqualTo("ADMIN_ENABLED");
        assertThat(result.securityVersion()).isEqualTo(6L);
        assertThat(target.getEnabled()).isTrue();
        assertThat(target.getSecurityVersion()).isEqualTo(6L);
        assertThat(target.getMfaSecretEncrypted()).isEqualTo(encryptedMfa);
        assertThat(target.getMfaEnrolledAt()).isNotNull();
        verify(challenges).deletePendingOwnedByPlatformUserId(8L);
    }

    @Test
    void refusesToEnableSuperAdministratorWhenAccountAndInvitationSeatsAreFull() {
        target.setEnabled(false);
        challenge.setActionContextHash(hash("ADMIN_STATUS_CHANGE\n8\ntrue"));
        command.setAction("ADMIN_ENABLE");
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(1L);
        when(invitations.countActiveByRoleCode(eq("PLATFORM_SUPER_ADMIN"), any())).thenReturn(1L);

        assertThatThrownBy(() -> executor.execute(
            7L, 8L, true, "status-command-1", "Return from leave", request, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_SUPER_ADMIN_LIMIT));

        assertThat(target.getEnabled()).isFalse();
        assertThat(target.getSecurityVersion()).isEqualTo(5L);
        verify(commands).delete(command);
    }

    @Test
    void sameTargetStateReturnsChangedFalseWithoutAdvancingVersion() {
        challenge.setActionContextHash(hash("ADMIN_STATUS_CHANGE\n8\ntrue"));
        command.setAction("ADMIN_ENABLE");

        PlatformAdminMutationResponse result = executor.execute(
            7L, 8L, true, "status-command-1", "Confirm current state", request, null);

        assertThat(result.changed()).isFalse();
        assertThat(result.enabled()).isTrue();
        assertThat(result.securityVersion()).isEqualTo(5L);
        assertThat(target.getSecurityVersion()).isEqualTo(5L);
        assertThat(challenge.getConsumedAt()).isNotNull();
        verify(challenges, never()).deletePendingOwnedByPlatformUserId(anyLong());
    }

    @Test
    void refusesToDisableLastEnabledSuperAdministratorAtFinalLock() {
        actor.setMfaFailedAttempts(2);
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> executor.execute(
            7L, 8L, false, "status-command-1", "Security incident", request, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_LAST_SUPER_ADMIN));

        assertThat(target.getEnabled()).isTrue();
        assertThat(target.getSecurityVersion()).isEqualTo(5L);
        assertThat(actor.getMfaFailedAttempts()).isZero();
        verify(commands).delete(command);
        verify(audit, never()).recordAdminInCurrentTransaction(anyLong(), anyLong(), anyString(),
            anyString(), anyString(), any(), any());
    }

    @Test
    void refusesToEnableHistoricalWriteOrUnknownRoles() {
        target.setEnabled(false);
        challenge.setActionContextHash(hash("ADMIN_STATUS_CHANGE\n8\ntrue"));
        command.setAction("ADMIN_ENABLE");
        when(userRoles.findRoleCodesByPlatformUserId(8L))
            .thenReturn(List.of("PLATFORM_TENANT_READ", "PLATFORM_TENANT_WRITE"));

        assertThatThrownBy(() -> executor.execute(
            7L, 8L, true, "status-command-1", "Return from leave", request, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_ENABLE_REVIEW_REQUIRED));

        assertThat(target.getEnabled()).isFalse();
        assertThat(target.getSecurityVersion()).isEqualTo(5L);
        verify(commands).delete(command);
    }

    @Test
    void rejectsChallengeWhenTargetSecurityVersionChanged() {
        target.setSecurityVersion(6L);

        assertThatThrownBy(() -> executor.execute(
            7L, 8L, false, "status-command-1", "Security incident", request, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_STATUS_CONFLICT));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(commands).delete(command);
    }

    @Test
    void fifthCredentialFailureLocksActorWithoutChangingTarget() {
        actor.setMfaFailedAttempts(4);
        when(passwordEncoder.matches("current-password", "password-hash-7")).thenReturn(false);

        assertThatThrownBy(() -> executor.execute(
            7L, 8L, false, "status-command-1", "Security incident", request, null))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.AUTH_FAILED);
                assertThat(exception.getParam("mfaReason")).isEqualTo("TEMPORARILY_LOCKED");
            });

        assertThat(actor.getMfaFailedAttempts()).isEqualTo(5);
        assertThat(actor.getMfaLockedUntil()).isAfter(OffsetDateTime.now());
        assertThat(challenge.getAttemptCount()).isEqualTo(1);
        assertThat(target.getEnabled()).isTrue();
        verify(commands).delete(command);
    }

    @Test
    void successfulIdempotentReplayReturnsStoredSafeResponseWithoutReusingChallenge() throws Exception {
        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(2);
        PlatformAdminMutationResponse stored = new PlatformAdminMutationResponse(
            8L, "ADMIN_DISABLED", true, false, List.of("PLATFORM_SUPER_ADMIN"), 3L, 6L, completedAt);
        command.setRequestFingerprint(hash("ADMIN_STATUS_COMMAND\n8\nfalse\nSecurity incident"));
        command.setStatus("SUCCEEDED");
        command.setSafeResponseJson(objectMapper.writeValueAsString(stored));
        command.setCompletedAt(completedAt);
        when(commands.insertReservation(anyLong(), anyLong(), anyString(), anyString(), anyString())).thenReturn(0);

        PlatformAdminMutationResponse result = executor.execute(
            7L, 8L, false, "status-command-1", "Security incident", request, null);

        assertThat(result).isEqualTo(stored);
        verify(challenges, never()).findByTokenHashForUpdate(anyString());
        verify(roles, never()).findByRoleCodeForUpdate(anyString());
    }

    @Test
    void sameIdempotencyKeyWithDifferentIntentReturnsConflict() {
        command.setRequestFingerprint(hash("different-intent"));
        when(commands.insertReservation(anyLong(), anyLong(), anyString(), anyString(), anyString())).thenReturn(0);

        assertThatThrownBy(() -> executor.execute(
            7L, 8L, false, "status-command-1", "Security incident", request, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_IDEMPOTENCY_CONFLICT));

        verify(challenges, never()).findByTokenHashForUpdate(anyString());
    }

    private PlatformUser user(Long id, boolean enabled, long securityVersion) {
        return PlatformUser.builder()
            .id(id)
            .normalizedEmail("admin-" + id + "@example.com")
            .displayName("Administrator " + id)
            .passwordHash("password-hash-" + id)
            .enabled(enabled)
            .securityVersion(securityVersion)
            .mfaEnabled(true)
            .mfaSecretEncrypted("encrypted-secret-" + id)
            .mfaFailedAttempts(0)
            .build();
    }

    private PlatformAdminStatusMutationRequest request(String reason) {
        PlatformAdminStatusMutationRequest value = new PlatformAdminStatusMutationRequest();
        value.setChallengeToken("challenge-token");
        value.setPassword("current-password");
        value.setCode("123456");
        value.setReason(reason);
        return value;
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
