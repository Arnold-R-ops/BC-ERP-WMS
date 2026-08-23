package com.wms.system.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminRoleChangeMutationRequest;
import com.wms.system.platform.model.PlatformAdminCommand;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.model.PlatformUserRole;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformAdminCommandRepository;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAdminRoleMutationExecutorTest {
    private final PlatformAdminCommandRepository commands = mock(PlatformAdminCommandRepository.class);
    private final PlatformMfaChallengeRepository challenges = mock(PlatformMfaChallengeRepository.class);
    private final PlatformRoleRepository roles = mock(PlatformRoleRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformUserRoleRepository userRoles = mock(PlatformUserRoleRepository.class);
    private final PlatformAccessGrantRepository grants = mock(PlatformAccessGrantRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PlatformMfaCrypto crypto = mock(PlatformMfaCrypto.class);
    private final PlatformMfaProperties properties = mock(PlatformMfaProperties.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private PlatformAdminRoleMutationExecutor executor;
    private PlatformUser target;

    @BeforeEach
    void setUp() {
        executor = new PlatformAdminRoleMutationExecutor(
            commands, challenges, roles, users, userRoles, grants,
            passwordEncoder, crypto, properties, audit, objectMapper);
        when(properties.getMaxAttempts()).thenReturn(5);
        when(commands.insertReservation(eq(7L), eq(9L), eq("key-1"),
            eq("ADMIN_ROLES_CHANGE"), any())).thenReturn(1);
        when(commands.findByActorAndKeyForUpdate(7L, "key-1")).thenReturn(Optional.of(
            PlatformAdminCommand.builder().id(41L).actorPlatformUserId(7L)
                .targetPlatformUserId(9L).idempotencyKey("key-1")
                .action("ADMIN_ROLES_CHANGE").requestFingerprint("fingerprint")
                .status("IN_PROGRESS").build()));
        when(crypto.hashToken(any())).thenAnswer(call -> {
            String value = call.getArgument(0);
            return "challenge-token".equals(value) ? "challenge-hash" : "fingerprint";
        });
        when(challenges.findByTokenHashForUpdate("challenge-hash")).thenReturn(Optional.of(
            PlatformMfaChallenge.builder().id(31L).tokenHash("challenge-hash")
                .platformUserId(7L).targetPlatformUserId(9L)
                .purpose("ADMIN_ROLES_CHANGE")
                .actionContextHash("fingerprint")
                .targetSecurityVersion(4L).attemptCount(0)
                .expiresAt(OffsetDateTime.now().plusMinutes(5)).build()));
        PlatformRole superRole = PlatformRole.builder().id(1L)
            .roleCode("PLATFORM_SUPER_ADMIN").build();
        PlatformRole auditorRole = PlatformRole.builder().id(3L)
            .roleCode("PLATFORM_SECURITY_AUDITOR").build();
        when(roles.findByRoleCodeForUpdate("PLATFORM_SUPER_ADMIN"))
            .thenReturn(Optional.of(superRole));
        when(roles.findByRoleCodeForUpdate("PLATFORM_SECURITY_AUDITOR"))
            .thenReturn(Optional.of(auditorRole));
        target = PlatformUser.builder().id(9L).normalizedEmail("target@example.com")
            .displayName("Target").passwordHash("target-password")
            .enabled(false).securityVersion(4L).build();
        PlatformUser actor = PlatformUser.builder().id(7L).normalizedEmail("owner@example.com")
            .displayName("Owner").passwordHash("owner-hash").enabled(true)
            .securityVersion(8L).mfaEnabled(true).mfaSecretEncrypted("actor-secret")
            .mfaFailedAttempts(0).build();
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(target));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(actor));
        when(userRoles.findRoleCodesByPlatformUserId(7L)).thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        when(userRoles.findRoleCodesByPlatformUserId(9L)).thenReturn(List.of("PLATFORM_OPERATIONS_ADMIN"));
        when(userRoles.findByPlatformUserId(9L)).thenReturn(List.of(
            PlatformUserRole.builder().id(51L).platformUserId(9L).platformRoleId(2L).build()));
        when(passwordEncoder.matches("owner-password", "owner-hash")).thenReturn(true);
        when(crypto.decrypt("actor-secret")).thenReturn("plain-secret");
        when(crypto.verifyTotp(eq("plain-secret"), eq("123456"), any())).thenReturn(true);
    }

    @Test
    void changesDisabledOrdinaryAccountRoleWithoutEnablingIt() {
        var result = executor.execute(
            7L, 9L, "PLATFORM_SECURITY_AUDITOR", "key-1", "separate duties",
            request(), null);

        assertThat(result.changed()).isTrue();
        assertThat(result.enabled()).isFalse();
        assertThat(result.beforeRoles()).containsExactly("PLATFORM_OPERATIONS_ADMIN");
        assertThat(result.afterRoles()).containsExactly("PLATFORM_SECURITY_AUDITOR");
        assertThat(target.getEnabled()).isFalse();
        assertThat(target.getSecurityVersion()).isEqualTo(5L);
        verify(userRoles).deleteAllInBatch(any());
        verify(userRoles).saveAndFlush(org.mockito.ArgumentMatchers.argThat(link ->
            link.getPlatformUserId().equals(9L) && link.getPlatformRoleId().equals(3L)));
        verify(audit).recordAdminInCurrentTransaction(
            eq(7L), eq(9L), eq("ADMIN_ROLES_CHANGED"),
            eq("platform_admin_roles"), eq("SUCCESS"), anyMap(), isNull());
    }

    @Test
    void blocksSecurityAuditorRoleWhileCurrentOrFutureGrantsExist() {
        when(grants.countCurrentOrFutureByPlatformUserId(eq(9L), any())).thenReturn(2L);

        assertThatThrownBy(() -> executor.execute(
            7L, 9L, "PLATFORM_SECURITY_AUDITOR", "key-1", "separate duties",
            request(), null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.PLATFORM_ADMIN_ACTIVE_GRANTS_EXIST));

        verify(userRoles, never()).deleteAllInBatch(any());
        verify(userRoles, never()).saveAndFlush(any());
    }

    @Test
    void locksTargetBeforeRecheckingGrantsForSecurityAuditorRole() {
        executor.execute(
            7L, 9L, "PLATFORM_SECURITY_AUDITOR", "key-1", "separate duties",
            request(), null);

        var lockOrder = inOrder(users, userRoles, grants);
        lockOrder.verify(users).findByIdForUpdate(9L);
        lockOrder.verify(userRoles).findRoleCodesByPlatformUserId(9L);
        lockOrder.verify(grants).countCurrentOrFutureByPlatformUserId(eq(9L), any());
    }

    private PlatformAdminRoleChangeMutationRequest request() {
        PlatformAdminRoleChangeMutationRequest request = new PlatformAdminRoleChangeMutationRequest();
        request.setJobRoleCode("PLATFORM_SECURITY_AUDITOR");
        request.setChallengeToken("challenge-token");
        request.setPassword("owner-password");
        request.setCode("123456");
        request.setReason("separate duties");
        return request;
    }
}
