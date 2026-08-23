package com.wms.system.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.exception.BusinessException;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformLoginRequest;
import com.wms.system.platform.dto.PlatformMfaVerifyRequest;
import com.wms.system.platform.dto.PlatformRecoveryCodeRegenerationRequest;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.security.JwtProperties;
import com.wms.system.security.JwtUtil;
import com.wms.system.security.PlatformUserDetailsService;
import com.wms.system.security.PlatformSecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformMfaServiceTest {
    @Mock PlatformUserRepository userRepository;
    @Mock PlatformMfaChallengeRepository challengeRepository;
    @Mock PlatformUserDetailsService userDetailsService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PlatformMfaCrypto crypto;
    @Mock PlatformAuditService auditService;
    @Mock PlatformAccessGuard accessGuard;
    @Mock JwtUtil jwtUtil;
    PlatformMfaProperties properties;
    JwtProperties jwtProperties;
    PlatformMfaService service;

    @BeforeEach void setUp() {
        properties = new PlatformMfaProperties();
        properties.setChallengeMinutes(5); properties.setMaxAttempts(5); properties.setLockMinutes(15);
        jwtProperties = new JwtProperties(); jwtProperties.setExpiration(86_400_000L); jwtProperties.setPlatformExpiration(43_200_000L);
        service = new PlatformMfaService(userRepository, challengeRepository, userDetailsService,
            passwordEncoder, crypto, properties, auditService, jwtUtil, jwtProperties, new ObjectMapper(), accessGuard);
    }

    @Test void validPasswordCreatesEnrollmentChallengeWithoutIssuingJwt() {
        PlatformUser user = user(false);
        when(userRepository.findByNormalizedEmailAndEnabledTrue("admin@bcwms.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(userDetailsService.loadRoleCodes(7L)).thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        when(crypto.newChallengeToken()).thenReturn("challenge-token");
        when(crypto.hashToken("challenge-token")).thenReturn("challenge-hash");
        when(crypto.newTotpSecret()).thenReturn("TOTPSECRET");
        when(crypto.encrypt("TOTPSECRET")).thenReturn("encrypted-secret");

        var response = service.login(new PlatformLoginRequest(" Admin@BCWMS.com ", "password"));

        assertThat(response.status()).isEqualTo("MFA_ENROLLMENT_REQUIRED");
        assertThat(response.token()).isNull();
        assertThat(response.challengeToken()).isEqualTo("challenge-token");
        verify(challengeRepository).save(argThat(challenge -> "ENROLL".equals(challenge.getPurpose())
            && "challenge-hash".equals(challenge.getTokenHash())
            && "encrypted-secret".equals(challenge.getPendingSecretEncrypted())));
        verifyNoInteractions(jwtUtil);
    }

    @Test void fifthFailedMfaAttemptLocksAccountAndWritesAudit() {
        PlatformUser user = user(true); user.setMfaFailedAttempts(4); user.setMfaSecretEncrypted("encrypted-secret");
        PlatformMfaChallenge challenge = PlatformMfaChallenge.builder().id(2L).platformUserId(7L)
            .tokenHash("challenge-hash").purpose("VERIFY").attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5)).build();
        when(crypto.hashToken("challenge-token")).thenReturn("challenge-hash");
        when(challengeRepository.findByTokenHashForUpdate("challenge-hash")).thenReturn(Optional.of(challenge));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(crypto.decrypt("encrypted-secret")).thenReturn("TOTPSECRET");
        when(crypto.verifyTotp(eq("TOTPSECRET"), eq("000000"), any())).thenReturn(false);
        PlatformMfaVerifyRequest request = request("challenge-token", "000000");

        assertThatThrownBy(() -> service.verify(request, null))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> {
                BusinessException businessException = (BusinessException) error;
                assertThat(businessException.getParam("mfaReason")).isEqualTo("TEMPORARILY_LOCKED");
                assertThat(businessException.getParam("remainingAttempts")).isEqualTo(0);
            });

        assertThat(user.getMfaFailedAttempts()).isEqualTo(5);
        assertThat(user.getMfaLockedUntil()).isAfter(OffsetDateTime.now().plusMinutes(14));
        verify(userRepository).save(user);
        verify(auditService).record(eq(7L), isNull(), eq("MFA_FAILED"),
            eq("platform_identity"), eq("FAILED"), argThat(detail -> Boolean.TRUE.equals(detail.get("locked"))), isNull());
        verifyNoInteractions(jwtUtil);
    }

    @Test void wrongAuthenticatorCodeCanBeRetriedWithCorrectCodeOnSameChallenge() {
        PlatformUser user = user(true);
        user.setMfaSecretEncrypted("encrypted-secret");
        PlatformMfaChallenge challenge = PlatformMfaChallenge.builder().id(3L).platformUserId(7L)
            .tokenHash("challenge-hash").purpose("VERIFY").attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5)).build();
        when(crypto.hashToken("challenge-token")).thenReturn("challenge-hash");
        when(challengeRepository.findByTokenHashForUpdate("challenge-hash")).thenReturn(Optional.of(challenge));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(crypto.decrypt("encrypted-secret")).thenReturn("TOTPSECRET");
        when(crypto.verifyTotp(eq("TOTPSECRET"), anyString(), any()))
            .thenAnswer(invocation -> "123456".equals(invocation.getArgument(1)));
        when(userDetailsService.loadRoleCodes(7L)).thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        when(jwtUtil.generatePlatformToken(7L, "admin@bcwms.com", List.of("PLATFORM_SUPER_ADMIN"), 3L))
            .thenReturn("platform-token");

        assertThatThrownBy(() -> service.verify(request("challenge-token", "000000"), null))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> {
                BusinessException businessException = (BusinessException) error;
                assertThat(businessException.getParam("mfaReason")).isEqualTo("CODE_INVALID");
                assertThat(businessException.getParam("remainingAttempts")).isEqualTo(4);
            });

        var response = service.verify(request("challenge-token", "123456"), null);

        assertThat(response.status()).isEqualTo("AUTHENTICATED");
        assertThat(response.token()).isEqualTo("platform-token");
        assertThat(user.getMfaFailedAttempts()).isZero();
        assertThat(user.getMfaLockedUntil()).isNull();
        assertThat(challenge.getAttemptCount()).isEqualTo(1);
        assertThat(challenge.getConsumedAt()).isNotNull();
        verify(auditService).record(eq(7L), isNull(), eq("MFA_VERIFIED"),
            eq("platform_identity"), eq("SUCCESS"), eq(java.util.Map.of()), isNull());
    }

    @Test void expiredChallengeIsReportedWithoutCountingAnotherFailedCode() {
        PlatformMfaChallenge challenge = PlatformMfaChallenge.builder().id(4L).platformUserId(7L)
            .tokenHash("expired-hash").purpose("VERIFY").attemptCount(1)
            .expiresAt(OffsetDateTime.now().minusSeconds(1)).build();
        when(crypto.hashToken("expired-challenge")).thenReturn("expired-hash");
        when(challengeRepository.findByTokenHashForUpdate("expired-hash")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.verify(request("expired-challenge", "123456"), null))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getParam("mfaReason"))
                .isEqualTo("CHALLENGE_EXPIRED"));

        assertThat(challenge.getAttemptCount()).isEqualTo(1);
        verifyNoInteractions(userRepository);
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test void verifiedAuthenticatorCodeRevokesEveryPlatformSession() {
        PlatformUser user = user(true);
        user.setMfaSecretEncrypted("encrypted-secret");
        PlatformMfaChallenge challenge = PlatformMfaChallenge.builder().id(9L).platformUserId(7L)
            .tokenHash("logout-hash").purpose("LOGOUT_ALL").attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5)).build();
        when(accessGuard.requirePlatformUser()).thenReturn(new PlatformSecurityUser(user));
        when(crypto.hashToken("logout-challenge")).thenReturn("logout-hash");
        when(challengeRepository.findByTokenHashForUpdate("logout-hash")).thenReturn(Optional.of(challenge));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(crypto.decrypt("encrypted-secret")).thenReturn("TOTPSECRET");
        when(crypto.verifyTotp(eq("TOTPSECRET"), eq("123456"), any())).thenReturn(true);

        service.logoutAll(request("logout-challenge", "123456"), null);

        assertThat(user.getSecurityVersion()).isEqualTo(4L);
        assertThat(challenge.getConsumedAt()).isNotNull();
        verify(auditService).record(eq(7L), isNull(), eq("SESSIONS_REVOKED"),
            eq("platform_identity"), eq("SUCCESS"), eq(java.util.Map.of("scope", "ALL_PLATFORM_SESSIONS")), isNull());
    }

    @Test void passwordAndTotpRegenerateRecoveryCodesWithoutRevokingSessions() {
        PlatformUser user = user(true);
        user.setMfaSecretEncrypted("encrypted-secret");
        user.setRecoveryCodeHashesJson("[\"old-hash\"]");
        PlatformMfaChallenge challenge = PlatformMfaChallenge.builder().id(10L).platformUserId(7L)
            .tokenHash("recovery-hash").purpose("RECOVERY_REGEN").attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5)).build();
        when(accessGuard.requirePlatformUser()).thenReturn(new PlatformSecurityUser(user));
        when(crypto.hashToken("recovery-challenge")).thenReturn("recovery-hash");
        when(challengeRepository.findByTokenHashForUpdate("recovery-hash")).thenReturn(Optional.of(challenge));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "encoded")).thenReturn(true);
        when(crypto.decrypt("encrypted-secret")).thenReturn("TOTPSECRET");
        when(crypto.verifyTotp(eq("TOTPSECRET"), eq("654321"), any())).thenReturn(true);
        when(crypto.newRecoveryCode()).thenAnswer(invocation -> "CODE-" + java.util.UUID.randomUUID().toString().substring(0, 5));
        when(crypto.normalizeRecoveryCode(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        PlatformRecoveryCodeRegenerationRequest request = new PlatformRecoveryCodeRegenerationRequest();
        request.setChallengeToken("recovery-challenge");
        request.setPassword("current-password");
        request.setCode("654321");

        var response = service.regenerateRecoveryCodes(request, null);

        assertThat(response.recoveryCodes()).hasSize(10).doesNotContain("old-hash");
        assertThat(response.remaining()).isEqualTo(10);
        assertThat(user.getRecoveryCodeHashesJson()).doesNotContain("old-hash");
        assertThat(user.getSecurityVersion()).isEqualTo(3L);
        assertThat(challenge.getConsumedAt()).isNotNull();
        verify(auditService).record(eq(7L), isNull(), eq("MFA_RECOVERY_REGENERATED"),
            eq("platform_identity"), eq("SUCCESS"), eq(java.util.Map.of("remaining", 10)), isNull());
    }

    @Test void adminInvitationCredentialFailureIsDeferredToRollbackSafeRecorder() {
        PlatformUser user = user(true);
        user.setMfaSecretEncrypted("encrypted-secret");
        PlatformMfaChallenge challenge = PlatformMfaChallenge.builder()
            .id(12L).platformUserId(7L).tokenHash("admin-invite-hash")
            .purpose("ADMIN_INVITE").attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5)).build();
        when(accessGuard.requireSuperAdmin()).thenReturn(new PlatformSecurityUser(user));
        when(crypto.hashToken("admin-invite-token")).thenReturn("admin-invite-hash");
        when(challengeRepository.findByTokenHashForUpdate("admin-invite-hash"))
            .thenReturn(Optional.of(challenge));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyAdminInvitation(
            "admin-invite-token", "wrong-password", "000000", null))
            .isInstanceOf(PlatformAdminInvitationMfaCredentialException.class);

        assertThat(user.getMfaFailedAttempts()).isZero();
        assertThat(challenge.getAttemptCount()).isZero();
        verify(userRepository, never()).save(user);
        verify(challengeRepository, never()).save(challenge);
        verifyNoInteractions(auditService);
    }

    private PlatformUser user(boolean mfaEnabled) {
        return PlatformUser.builder().id(7L).normalizedEmail("admin@bcwms.com")
            .passwordHash("encoded").displayName("Admin").enabled(true).securityVersion(3L)
            .mfaEnabled(mfaEnabled).mfaFailedAttempts(0).build();
    }

    private PlatformMfaVerifyRequest request(String token, String code) {
        PlatformMfaVerifyRequest request = new PlatformMfaVerifyRequest();
        request.setChallengeToken(token); request.setCode(code); return request;
    }

}
