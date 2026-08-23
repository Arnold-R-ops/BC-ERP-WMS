package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformAdminInvitationMfaFailureRecorderTest {
    private final PlatformMfaChallengeRepository challenges =
        mock(PlatformMfaChallengeRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final PlatformMfaProperties properties = new PlatformMfaProperties();
    private PlatformAdminInvitationMfaFailureRecorder recorder;

    @BeforeEach
    void setUp() {
        properties.setMaxAttempts(5);
        properties.setLockMinutes(15);
        recorder = new PlatformAdminInvitationMfaFailureRecorder(
            challenges, users, properties, audit);
    }

    @Test
    void fifthCredentialFailurePersistsChallengeAndAccountLockWithOneAudit() {
        PlatformMfaChallenge challenge = PlatformMfaChallenge.builder()
            .id(31L)
            .tokenHash("reauth-hash")
            .platformUserId(7L)
            .purpose("ADMIN_INVITE")
            .attemptCount(4)
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .build();
        PlatformUser actor = PlatformUser.builder()
            .id(7L)
            .normalizedEmail("owner@example.com")
            .passwordHash("encoded")
            .displayName("Owner")
            .enabled(true)
            .mfaEnabled(true)
            .mfaFailedAttempts(4)
            .build();
        when(challenges.findByTokenHashForUpdate("reauth-hash"))
            .thenReturn(Optional.of(challenge));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(actor));

        int attempts = recorder.record(7L, "reauth-hash", null);

        assertThat(attempts).isEqualTo(5);
        assertThat(challenge.getAttemptCount()).isEqualTo(5);
        assertThat(actor.getMfaFailedAttempts()).isEqualTo(5);
        assertThat(actor.getMfaLockedUntil())
            .isAfter(OffsetDateTime.now().plusMinutes(14));
        verify(users).save(actor);
        verify(challenges).save(challenge);
        verify(audit).recordInCurrentTransaction(
            eq(7L), isNull(), eq("MFA_FAILED"), eq("platform_identity"),
            eq("FAILED"), anyMap(), isNull());
        verify(audit, never()).record(
            eq(7L), isNull(), eq("MFA_FAILED"), eq("platform_identity"),
            eq("FAILED"), anyMap(), isNull());
    }

    @Test
    void wrongPurposeIsNotCountedOrAudited() {
        PlatformMfaChallenge challenge = PlatformMfaChallenge.builder()
            .id(31L)
            .tokenHash("reauth-hash")
            .platformUserId(7L)
            .purpose("VERIFY")
            .attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .build();
        when(challenges.findByTokenHashForUpdate("reauth-hash"))
            .thenReturn(Optional.of(challenge));

        assertThat(recorder.record(7L, "reauth-hash", null)).isZero();

        verifyNoInteractions(users, audit);
    }

    @Test
    void failureRecorderUsesIndependentTransaction() throws Exception {
        Transactional transaction = PlatformAdminInvitationMfaFailureRecorder.class
            .getMethod("record", Long.class, String.class,
                jakarta.servlet.http.HttpServletRequest.class)
            .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
