package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminInvitationCreateRequest;
import com.wms.system.platform.dto.PlatformAdminInvitationResponse;
import com.wms.system.platform.dto.PlatformAuthResponse;
import com.wms.system.platform.dto.PlatformInvitationActivateRequest;
import com.wms.system.platform.dto.PlatformMfaVerifyRequest;
import com.wms.system.platform.model.PlatformAdminInvitation;
import com.wms.system.platform.model.PlatformAdminInvitationActivation;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAdminInvitationActivationRepository;
import com.wms.system.platform.repository.PlatformAdminInvitationActiveEmailRepository;
import com.wms.system.platform.repository.PlatformAdminInvitationRepository;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformAdminInvitationServiceTest {
    private final PlatformAdminInvitationRepository invitations = mock(PlatformAdminInvitationRepository.class);
    private final PlatformAdminInvitationActivationRepository activations = mock(PlatformAdminInvitationActivationRepository.class);
    private final PlatformAdminInvitationActiveEmailRepository activeEmails = mock(PlatformAdminInvitationActiveEmailRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformRoleRepository roles = mock(PlatformRoleRepository.class);
    private final PlatformUserRoleRepository userRoles = mock(PlatformUserRoleRepository.class);
    private final PlatformMfaService mfa = mock(PlatformMfaService.class);
    private final PlatformMfaCrypto crypto = mock(PlatformMfaCrypto.class);
    private final PlatformMfaProperties properties = mock(PlatformMfaProperties.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PlatformAccessGuard guard = mock(PlatformAccessGuard.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PlatformAdminInvitationActivationFailureRecorder activationFailureRecorder =
        mock(PlatformAdminInvitationActivationFailureRecorder.class);
    private final PlatformAdminInvitationMfaFailureRecorder invitationMfaFailureRecorder =
        mock(PlatformAdminInvitationMfaFailureRecorder.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private PlatformAdminInvitationService service;

    @BeforeEach
    void setUp() {
        service = new PlatformAdminInvitationService(
            invitations, activations, activeEmails, users, roles, userRoles,
            mfa, crypto, properties, passwordEncoder, guard, audit,
            transactionManager, activationFailureRecorder, invitationMfaFailureRecorder);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(properties.getChallengeMinutes()).thenReturn(5);
        when(properties.getMaxAttempts()).thenReturn(5);
        when(roles.findByRoleCodeForUpdate("PLATFORM_SUPER_ADMIN")).thenReturn(Optional.of(
            PlatformRole.builder().id(3L).roleCode("PLATFORM_SUPER_ADMIN").displayName("Super").build()));
    }

    @Test
    void invitationStoresOnlyTokenHashAndReturnsRawLinkOnce() {
        PlatformUser actor = PlatformUser.builder().id(7L).normalizedEmail("owner@bcwms.com").build();
        PlatformAdminInvitationCreateRequest request = superInvitationRequest(" Second@Example.com ");
        when(users.findByNormalizedEmail("second@example.com")).thenReturn(Optional.empty());
        when(invitations.hasActiveInvitation(eq("second@example.com"), any())).thenReturn(false);
        when(mfa.verifyAdminInvitation("reauth", "current-password", "123456", null)).thenReturn(actor);
        when(crypto.newChallengeToken()).thenReturn("raw-one-time-token");
        when(crypto.hashToken("raw-one-time-token")).thenReturn("stored-hash");
        when(invitations.saveAndFlush(any())).thenAnswer(call -> {
            PlatformAdminInvitation invitation = call.getArgument(0);
            invitation.setId(11L);
            invitation.setCreatedAt(OffsetDateTime.now());
            return invitation;
        });
        when(activeEmails.reserve("second@example.com", 11L)).thenReturn(1);

        PlatformAdminInvitationResponse result = service.create(request, null);

        assertThat(result.activationPath()).isEqualTo(
            "/platform.html#/activate?token=raw-one-time-token");
        assertThat(result.invitationType()).isEqualTo("SUPER_ADMIN");
        verify(invitations).saveAndFlush(argThat(invitation ->
            "stored-hash".equals(invitation.getTokenHash())
                && "SUPER_ADMIN".equals(invitation.getInvitationType())
                && "PLATFORM_SUPER_ADMIN".equals(invitation.getRoleCode())
                && invitation.getExpiresAt().isBefore(OffsetDateTime.now().plusHours(25))));
        verify(audit).record(eq(7L), isNull(), eq("ADMIN_INVITED"),
            eq("platform_identity"), eq("SUCCESS"), anyMap(), isNull());
    }

    @Test
    void invalidInvitationCredentialsAreRecordedOnlyAfterCreateTransactionRollsBack() {
        PlatformAdminInvitationCreateRequest request = superInvitationRequest("second@example.com");
        when(users.findByNormalizedEmail("second@example.com")).thenReturn(Optional.empty());
        when(invitations.hasActiveInvitation(eq("second@example.com"), any())).thenReturn(false);
        when(mfa.verifyAdminInvitation("reauth", "current-password", "123456", null))
            .thenThrow(new PlatformAdminInvitationMfaCredentialException(7L, "reauth-hash"));
        when(invitationMfaFailureRecorder.record(7L, "reauth-hash", null)).thenReturn(1);

        assertThatThrownBy(() -> service.create(request, null))
            .isInstanceOf(PlatformAdminInvitationMfaCredentialException.class);

        InOrder order = inOrder(transactionManager, invitationMfaFailureRecorder);
        order.verify(transactionManager).rollback(transactionStatus);
        order.verify(invitationMfaFailureRecorder).record(7L, "reauth-hash", null);
        verify(invitations, never()).saveAndFlush(any());
    }

    @Test
    void invalidOrExpiredChallengeDoesNotIncrementCredentialFailures() {
        PlatformAdminInvitationCreateRequest request = superInvitationRequest("second@example.com");
        when(users.findByNormalizedEmail("second@example.com")).thenReturn(Optional.empty());
        when(invitations.hasActiveInvitation(eq("second@example.com"), any())).thenReturn(false);
        when(mfa.verifyAdminInvitation("reauth", "current-password", "123456", null))
            .thenThrow(new BusinessException(ErrorKeys.AUTH_FAILED,
                Map.of("reason", "MFA_VERIFICATION_FAILED", "mfaReason", "CHALLENGE_EXPIRED")));

        assertThatThrownBy(() -> service.create(request, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getParam("mfaReason"))
                    .isEqualTo("CHALLENGE_EXPIRED"));

        verifyNoInteractions(invitationMfaFailureRecorder);
        verify(invitations, never()).saveAndFlush(any());
    }

    @Test
    void passwordStepCreatesOnlyPendingActivationAndNoFormalAccount() {
        PlatformAdminInvitation invitation = activeSuperInvitation();
        PlatformInvitationActivateRequest request = new PlatformInvitationActivateRequest();
        request.setToken("raw-token");
        request.setPassword("Strong-Password-42!");
        when(crypto.hashToken("raw-token")).thenReturn("stored-hash");
        when(invitations.findByTokenHashForUpdate("stored-hash")).thenReturn(Optional.of(invitation));
        when(users.findByNormalizedEmail("second@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Strong-Password-42!")).thenReturn("encoded-password");
        when(crypto.newChallengeToken()).thenReturn("activation-token");
        when(crypto.hashToken("activation-token")).thenReturn("activation-hash");
        when(crypto.newTotpSecret()).thenReturn("totp-secret");
        when(crypto.encrypt("totp-secret")).thenReturn("encrypted-secret");
        when(activations.findByInvitationIdAndConsumedAtIsNull(11L)).thenReturn(List.of());
        PlatformAuthResponse enrollment = new PlatformAuthResponse(
            "MFA_ENROLLMENT_REQUIRED", "activation-token", "totp-secret", "otpauth://setup",
            null, null, "second@example.com", List.of("PLATFORM_SUPER_ADMIN"), 300_000L, null);
        when(mfa.pendingInvitationEnrollment(
            "second@example.com", List.of("PLATFORM_SUPER_ADMIN"),
            "activation-token", "totp-secret")).thenReturn(enrollment);

        PlatformAuthResponse result = service.activate(request, null);

        assertThat(result.status()).isEqualTo("MFA_ENROLLMENT_REQUIRED");
        assertThat(invitation.getAcceptedAt()).isNull();
        assertThat(invitation.getAcceptedPlatformUserId()).isNull();
        verify(activations).save(argThat(activation ->
            activation.getInvitationId().equals(11L)
                && "encoded-password".equals(activation.getPasswordHash())
                && "encrypted-secret".equals(activation.getPendingSecretEncrypted())));
        verify(users, never()).save(any());
        verify(users, never()).saveAndFlush(any());
        verify(userRoles, never()).save(any());
        verify(userRoles, never()).saveAndFlush(any());
    }

    @Test
    void correctMfaAtomicallyCreatesAccountRoleAndAcceptsInvitation() {
        PlatformAdminInvitation invitation = activeSuperInvitation();
        PlatformAdminInvitationActivation activation = PlatformAdminInvitationActivation.builder()
            .id(31L)
            .invitationId(11L)
            .tokenHash("activation-hash")
            .passwordHash("encoded-password")
            .pendingSecretEncrypted("encrypted-secret")
            .attemptCount(0)
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .build();
        PlatformMfaVerifyRequest request = new PlatformMfaVerifyRequest();
        request.setChallengeToken("activation-token");
        request.setCode("123456");
        when(crypto.hashToken("activation-token")).thenReturn("activation-hash");
        when(activations.findByTokenHashForUpdate("activation-hash")).thenReturn(Optional.of(activation));
        when(invitations.findByIdForUpdate(11L)).thenReturn(Optional.of(invitation));
        when(users.findByNormalizedEmail("second@example.com")).thenReturn(Optional.empty());
        when(crypto.decrypt("encrypted-secret")).thenReturn("totp-secret");
        when(crypto.verifyTotp(eq("totp-secret"), eq("123456"), any())).thenReturn(true);
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            PlatformUser user = call.getArgument(0);
            user.setId(22L);
            return user;
        });
        PlatformAuthResponse authenticated = new PlatformAuthResponse(
            "AUTHENTICATED", null, null, null, "jwt", "Bearer",
            "second@example.com", List.of("PLATFORM_SUPER_ADMIN"), 900_000L, List.of("recovery"));
        when(mfa.completeInvitedEnrollment(any(), eq("encrypted-secret"), isNull()))
            .thenReturn(authenticated);

        PlatformAuthResponse result = service.confirmActivation(request, null);

        assertThat(result.status()).isEqualTo("AUTHENTICATED");
        assertThat(invitation.getAcceptedPlatformUserId()).isEqualTo(22L);
        assertThat(invitation.getAcceptedAt()).isNotNull();
        assertThat(activation.getConsumedAt()).isNotNull();
        verify(userRoles).saveAndFlush(argThat(link ->
            link.getPlatformUserId().equals(22L) && link.getPlatformRoleId().equals(3L)));
        verify(activeEmails).release("second@example.com", 11L);
        verify(audit).recordInCurrentTransaction(eq(22L), isNull(),
            eq("ADMIN_INVITATION_ACCEPTED"), eq("platform_identity"),
            eq("SUCCESS"), anyMap(), isNull());
        InOrder creationOrder = inOrder(crypto, users);
        creationOrder.verify(crypto).verifyTotp(eq("totp-secret"), eq("123456"), any());
        creationOrder.verify(users).saveAndFlush(any());
    }

    @Test
    void invalidInvitationMfaIsCountedAfterAtomicTransactionRollsBack() {
        PlatformAdminInvitation invitation = activeSuperInvitation();
        PlatformAdminInvitationActivation activation = PlatformAdminInvitationActivation.builder()
            .id(31L).invitationId(11L).tokenHash("activation-hash")
            .passwordHash("encoded-password").pendingSecretEncrypted("encrypted-secret")
            .attemptCount(0).expiresAt(OffsetDateTime.now().plusMinutes(5)).build();
        PlatformMfaVerifyRequest request = new PlatformMfaVerifyRequest();
        request.setChallengeToken("activation-token");
        request.setCode("000000");
        when(crypto.hashToken("activation-token")).thenReturn("activation-hash");
        when(activations.findByTokenHashForUpdate("activation-hash")).thenReturn(Optional.of(activation));
        when(invitations.findByIdForUpdate(11L)).thenReturn(Optional.of(invitation));
        when(users.findByNormalizedEmail("second@example.com")).thenReturn(Optional.empty());
        when(crypto.decrypt("encrypted-secret")).thenReturn("totp-secret");
        when(crypto.verifyTotp(eq("totp-secret"), eq("000000"), any())).thenReturn(false);
        when(activationFailureRecorder.record("activation-hash")).thenReturn(1);

        assertThatThrownBy(() -> service.confirmActivation(request, null))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getParam("mfaReason")).isEqualTo("CODE_INVALID");
                assertThat(exception.getParam("remainingAttempts")).isEqualTo(4);
            });

        verify(activationFailureRecorder).record("activation-hash");
        verify(users, never()).saveAndFlush(any());
        verify(userRoles, never()).saveAndFlush(any());
    }

    @Test
    void refusesAThirdActiveOrPendingSuperAdministrator() {
        PlatformAdminInvitationCreateRequest request = superInvitationRequest("third@example.com");
        when(users.findByNormalizedEmail("third@example.com")).thenReturn(Optional.empty());
        when(invitations.hasActiveInvitation(eq("third@example.com"), any())).thenReturn(false);
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(2L);

        assertThatThrownBy(() -> service.create(request, null))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey())
                    .isEqualTo("PLATFORM_ADMIN_SUPER_ADMIN_LIMIT"));
        verifyNoInteractions(mfa);
        verify(invitations, never()).saveAndFlush(any());
    }

    private PlatformAdminInvitationCreateRequest superInvitationRequest(String email) {
        PlatformAdminInvitationCreateRequest request = new PlatformAdminInvitationCreateRequest();
        request.setChallengeToken("reauth");
        request.setPassword("current-password");
        request.setCode("123456");
        request.setEmail(email);
        request.setDisplayName(" Second Owner ");
        request.setReason(" Business continuity ");
        request.setInvitationType("SUPER_ADMIN");
        request.setRoleCode("PLATFORM_SUPER_ADMIN");
        return request;
    }

    private PlatformAdminInvitation activeSuperInvitation() {
        return PlatformAdminInvitation.builder()
            .id(11L)
            .tokenHash("stored-hash")
            .normalizedEmail("second@example.com")
            .displayName("Second Owner")
            .invitationType("SUPER_ADMIN")
            .roleCode("PLATFORM_SUPER_ADMIN")
            .invitedByPlatformUserId(7L)
            .reason("Continuity")
            .createdAt(OffsetDateTime.now())
            .expiresAt(OffsetDateTime.now().plusHours(2))
            .build();
    }
}
