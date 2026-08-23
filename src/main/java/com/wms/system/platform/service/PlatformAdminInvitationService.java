package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminInvitationCreateRequest;
import com.wms.system.platform.dto.PlatformAdminInvitationResponse;
import com.wms.system.platform.dto.PlatformAdminInvitationRevokeRequest;
import com.wms.system.platform.dto.PlatformAuthResponse;
import com.wms.system.platform.dto.PlatformInvitationActivateRequest;
import com.wms.system.platform.dto.PlatformInvitationStatusResponse;
import com.wms.system.platform.dto.PlatformInvitationTokenRequest;
import com.wms.system.platform.dto.PlatformMfaVerifyRequest;
import com.wms.system.platform.dto.PlatformReauthenticationChallengeResponse;
import com.wms.system.platform.model.PlatformAdminInvitation;
import com.wms.system.platform.model.PlatformAdminInvitationActivation;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.model.PlatformUserRole;
import com.wms.system.platform.repository.PlatformAdminInvitationActivationRepository;
import com.wms.system.platform.repository.PlatformAdminInvitationActiveEmailRepository;
import com.wms.system.platform.repository.PlatformAdminInvitationRepository;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformAdminInvitationService {
    static final String SUPER_ADMIN = "PLATFORM_SUPER_ADMIN";
    static final String OPERATIONS_ADMIN = "PLATFORM_OPERATIONS_ADMIN";
    static final String SECURITY_AUDITOR = "PLATFORM_SECURITY_AUDITOR";
    static final String SUPER_INVITATION = "SUPER_ADMIN";
    static final String ORDINARY_INVITATION = "ORDINARY_ADMIN";
    private static final Set<String> ORDINARY_ROLES = Set.of(OPERATIONS_ADMIN, SECURITY_AUDITOR);

    private final PlatformAdminInvitationRepository invitations;
    private final PlatformAdminInvitationActivationRepository activations;
    private final PlatformAdminInvitationActiveEmailRepository activeEmails;
    private final PlatformUserRepository users;
    private final PlatformRoleRepository roles;
    private final PlatformUserRoleRepository userRoles;
    private final PlatformMfaService mfa;
    private final PlatformMfaCrypto crypto;
    private final PlatformMfaProperties mfaProperties;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAccessGuard guard;
    private final PlatformAuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final PlatformAdminInvitationActivationFailureRecorder activationFailureRecorder;
    private final PlatformAdminInvitationMfaFailureRecorder invitationMfaFailureRecorder;

    public PlatformReauthenticationChallengeResponse startChallenge() {
        guard.requireSuperAdmin();
        return mfa.startAdminInvitation();
    }

    public PlatformAdminInvitationResponse create(
        PlatformAdminInvitationCreateRequest request,
        HttpServletRequest http
    ) {
        try {
            PlatformAdminInvitationResponse response = new TransactionTemplate(transactionManager)
                .execute(status -> createAtomic(request, http));
            if (response == null) {
                throw new IllegalStateException("Administrator invitation creation returned no result");
            }
            return response;
        } catch (PlatformAdminInvitationMfaCredentialException exception) {
            invitationMfaFailureRecorder.record(
                exception.actorId(), exception.challengeTokenHash(), http);
            throw exception;
        }
    }

    private PlatformAdminInvitationResponse createAtomic(
        PlatformAdminInvitationCreateRequest request,
        HttpServletRequest http
    ) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String displayName = request.getDisplayName().trim();
        String reason = request.getReason().trim();
        String invitationType = request.getInvitationType();
        String roleCode = request.getRoleCode();
        validateInvitationRole(invitationType, roleCode);
        if (users.findByNormalizedEmail(email).isPresent()) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_EMAIL_EXISTS);
        }

        releaseExpiredReservation(email);
        if (activeEmails.existsById(email)
            || invitations.hasActiveInvitation(email, OffsetDateTime.now())) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_INVITATION_ACTIVE);
        }

        PlatformRole role = SUPER_ADMIN.equals(roleCode)
            ? requireSecondSuperAdminSlot()
            : roles.findByRoleCodeForUpdate(roleCode)
                .orElseThrow(() -> new IllegalStateException("Required platform role is missing"));
        PlatformUser actor = mfa.verifyAdminInvitation(
            request.getChallengeToken(), request.getPassword(), request.getCode(), http);

        String token = crypto.newChallengeToken();
        PlatformAdminInvitation saved = invitations.saveAndFlush(PlatformAdminInvitation.builder()
            .tokenHash(crypto.hashToken(token))
            .normalizedEmail(email)
            .displayName(displayName)
            .invitationType(invitationType)
            .roleCode(role.getRoleCode())
            .invitedByPlatformUserId(actor.getId())
            .reason(reason)
            .expiresAt(OffsetDateTime.now().plusHours(24))
            .build());
        if (activeEmails.reserve(email, saved.getId()) != 1) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_INVITATION_ACTIVE);
        }
        audit.record(actor.getId(), null, "ADMIN_INVITED", "platform_identity", "SUCCESS",
            Map.of(
                "invitationId", saved.getId(),
                "invitationType", invitationType,
                "roleCode", roleCode,
                "expiresAt", saved.getExpiresAt().toString()
            ), http);
        return response(saved, "/platform.html#/activate?token=" + token);
    }

    @Transactional(readOnly = true)
    public List<PlatformAdminInvitationResponse> list() {
        guard.requireSuperAdmin();
        return invitations.findAllByOrderByCreatedAtDesc().stream()
            .map(invitation -> response(invitation, null))
            .toList();
    }

    @Transactional
    public void revoke(
        Long id,
        PlatformAdminInvitationRevokeRequest request,
        HttpServletRequest http
    ) {
        PlatformUser actor = guard.requireSuperAdmin().getUser();
        PlatformAdminInvitation invitation = invitations.findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PLATFORM_ADMIN_INVITATION_NOT_FOUND));
        if (invitation.getAcceptedAt() != null) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_INVITATION_TERMINAL);
        }
        if (invitation.getRevokedAt() == null) {
            OffsetDateTime now = OffsetDateTime.now();
            invitation.setRevokedAt(now);
            invitation.setRevokedByPlatformUserId(actor.getId());
            invitations.save(invitation);
            consumeOutstandingActivations(invitation.getId(), now);
            activeEmails.release(invitation.getNormalizedEmail(), invitation.getId());
            audit.record(actor.getId(), null, "ADMIN_INVITATION_REVOKED", "platform_identity", "SUCCESS",
                Map.of(
                    "invitationId", invitation.getId(),
                    "invitationType", invitation.getInvitationType(),
                    "roleCode", invitation.getRoleCode(),
                    "reason", request.reason().trim()
                ), http);
        }
    }

    @Transactional(readOnly = true)
    public PlatformInvitationStatusResponse status(PlatformInvitationTokenRequest request) {
        PlatformAdminInvitation invitation = active(request.getToken(), false);
        return new PlatformInvitationStatusResponse(
            invitation.getNormalizedEmail(),
            invitation.getDisplayName(),
            invitation.getInvitationType(),
            invitation.getRoleCode(),
            invitation.getExpiresAt()
        );
    }

    @Transactional
    public PlatformAuthResponse activate(
        PlatformInvitationActivateRequest request,
        HttpServletRequest http
    ) {
        validatePassword(request.getPassword());
        PlatformAdminInvitation invitation = active(request.getToken(), true);
        if (users.findByNormalizedEmail(invitation.getNormalizedEmail()).isPresent()) {
            throw invalidInvitation();
        }
        validateInvitationRole(invitation.getInvitationType(), invitation.getRoleCode());
        if (SUPER_ADMIN.equals(invitation.getRoleCode())) {
            validateSecondSuperAdminSlotAtActivation();
        } else if (roles.findByRoleCode(invitation.getRoleCode()).isEmpty()) {
            throw invalidInvitation();
        }

        OffsetDateTime now = OffsetDateTime.now();
        consumeOutstandingActivations(invitation.getId(), now);
        String activationToken = crypto.newChallengeToken();
        String secret = crypto.newTotpSecret();
        activations.save(PlatformAdminInvitationActivation.builder()
            .invitationId(invitation.getId())
            .tokenHash(crypto.hashToken(activationToken))
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .pendingSecretEncrypted(crypto.encrypt(secret))
            .attemptCount(0)
            .expiresAt(now.plusMinutes(mfaProperties.getChallengeMinutes()))
            .build());
        return mfa.pendingInvitationEnrollment(
            invitation.getNormalizedEmail(),
            List.of(invitation.getRoleCode()),
            activationToken,
            secret
        );
    }

    public PlatformAuthResponse confirmActivation(
        PlatformMfaVerifyRequest request,
        HttpServletRequest http
    ) {
        if (request.getChallengeToken() == null || request.getChallengeToken().isBlank()) {
            throw invalidActivationMfa("CHALLENGE_INVALID", 0);
        }
        String tokenHash = crypto.hashToken(request.getChallengeToken());
        try {
            PlatformAuthResponse response = new TransactionTemplate(transactionManager)
                .execute(status -> confirmActivationAtomic(request, http, tokenHash));
            if (response == null) throw new IllegalStateException("Invitation activation returned no result");
            return response;
        } catch (BusinessException exception) {
            if (ErrorKeys.AUTH_FAILED.equals(exception.getErrorKey())
                && "CODE_INVALID".equals(exception.getParam("mfaReason"))) {
                int attempts = activationFailureRecorder.record(tokenHash);
                throw invalidActivationMfa(
                    attempts >= mfaProperties.getMaxAttempts()
                        ? "TEMPORARILY_LOCKED" : "CODE_INVALID",
                    attempts
                );
            }
            throw exception;
        }
    }

    private PlatformAuthResponse confirmActivationAtomic(
        PlatformMfaVerifyRequest request,
        HttpServletRequest http,
        String tokenHash
    ) {
        PlatformAdminInvitationActivation activation = activations
            .findByTokenHashForUpdate(tokenHash)
            .orElseThrow(() -> invalidActivationMfa("CHALLENGE_INVALID", 0));
        OffsetDateTime now = OffsetDateTime.now();
        if (activation.getConsumedAt() != null) {
            throw invalidActivationMfa("CHALLENGE_INVALID", activation.getAttemptCount());
        }
        if (!now.isBefore(activation.getExpiresAt())) {
            throw invalidActivationMfa("CHALLENGE_EXPIRED", activation.getAttemptCount());
        }
        if (activation.getAttemptCount() >= mfaProperties.getMaxAttempts()) {
            throw invalidActivationMfa("TEMPORARILY_LOCKED", activation.getAttemptCount());
        }

        PlatformAdminInvitation invitation = invitations.findByIdForUpdate(activation.getInvitationId())
            .filter(this::isActive)
            .orElseThrow(this::invalidInvitation);
        if (users.findByNormalizedEmail(invitation.getNormalizedEmail()).isPresent()) {
            throw invalidInvitation();
        }
        String secret = crypto.decrypt(activation.getPendingSecretEncrypted());
        if (request.getCode() == null
            || !request.getCode().matches("\\d{6}")
            || !crypto.verifyTotp(secret, request.getCode(), Instant.now())) {
            throw invalidActivationMfa("CODE_INVALID", activation.getAttemptCount());
        }

        PlatformRole role;
        if (SUPER_ADMIN.equals(invitation.getRoleCode())) {
            role = lockSuperAdminSeat();
            validateSecondSuperAdminSlotAtActivation();
        } else {
            role = roles.findByRoleCodeForUpdate(invitation.getRoleCode())
                .filter(candidate -> ORDINARY_ROLES.contains(candidate.getRoleCode()))
                .orElseThrow(this::invalidInvitation);
        }

        PlatformUser user = users.saveAndFlush(PlatformUser.builder()
            .normalizedEmail(invitation.getNormalizedEmail())
            .passwordHash(activation.getPasswordHash())
            .displayName(invitation.getDisplayName())
            .enabled(true)
            .securityVersion(1L)
            .mfaEnabled(false)
            .mfaFailedAttempts(0)
            .build());
        userRoles.saveAndFlush(PlatformUserRole.builder()
            .platformUserId(user.getId())
            .platformRoleId(role.getId())
            .build());

        PlatformAuthResponse authenticated = mfa.completeInvitedEnrollment(
            user, activation.getPendingSecretEncrypted(), http);
        activation.setConsumedAt(now);
        activations.save(activation);
        invitation.setAcceptedAt(now);
        invitation.setAcceptedPlatformUserId(user.getId());
        invitations.save(invitation);
        activeEmails.release(invitation.getNormalizedEmail(), invitation.getId());
        audit.recordInCurrentTransaction(user.getId(), null,
            "ADMIN_INVITATION_ACCEPTED", "platform_identity", "SUCCESS",
            Map.of(
                "invitationId", invitation.getId(),
                "invitationType", invitation.getInvitationType(),
                "roleCode", invitation.getRoleCode()
            ), http);
        return authenticated;
    }

    private PlatformAdminInvitation active(String token, boolean lock) {
        if (token == null || token.isBlank()) throw invalidInvitation();
        Optional<PlatformAdminInvitation> found = lock
            ? invitations.findByTokenHashForUpdate(crypto.hashToken(token))
            : invitations.findByTokenHash(crypto.hashToken(token));
        return found.filter(this::isActive).orElseThrow(this::invalidInvitation);
    }

    private boolean isActive(PlatformAdminInvitation invitation) {
        return invitation.getAcceptedAt() == null
            && invitation.getRevokedAt() == null
            && OffsetDateTime.now().isBefore(invitation.getExpiresAt());
    }

    private PlatformAdminInvitationResponse response(
        PlatformAdminInvitation invitation,
        String activationPath
    ) {
        String status = invitation.getAcceptedAt() != null ? "ACCEPTED"
            : invitation.getRevokedAt() != null ? "REVOKED"
            : OffsetDateTime.now().isAfter(invitation.getExpiresAt()) ? "EXPIRED"
            : "ACTIVE";
        return new PlatformAdminInvitationResponse(
            invitation.getId(),
            invitation.getNormalizedEmail(),
            invitation.getDisplayName(),
            invitation.getInvitationType(),
            invitation.getRoleCode(),
            status,
            invitation.getCreatedAt(),
            invitation.getExpiresAt(),
            invitation.getAcceptedAt(),
            invitation.getRevokedAt(),
            activationPath
        );
    }

    private void validatePassword(String password) {
        boolean valid = password != null
            && password.length() >= 12
            && password.length() <= 64
            && password.chars().anyMatch(Character::isUpperCase)
            && password.chars().anyMatch(Character::isLowerCase)
            && password.chars().anyMatch(Character::isDigit)
            && password.chars().anyMatch(character ->
                !Character.isLetterOrDigit(character) && !Character.isWhitespace(character));
        if (!valid) {
            throw new BusinessException(ErrorKeys.PASSWORD_TOO_WEAK,
                Map.of("minimumLength", 12));
        }
    }

    private void validateInvitationRole(String invitationType, String roleCode) {
        boolean validSuper = SUPER_INVITATION.equals(invitationType)
            && SUPER_ADMIN.equals(roleCode);
        boolean validOrdinary = ORDINARY_INVITATION.equals(invitationType)
            && ORDINARY_ROLES.contains(roleCode);
        if (!validSuper && !validOrdinary) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "roleCode"));
        }
    }

    private PlatformRole requireSecondSuperAdminSlot() {
        PlatformRole role = lockSuperAdminSeat();
        long enabled = userRoles.countEnabledUsersByRoleCode(SUPER_ADMIN);
        long pending = invitations.countActiveByRoleCode(SUPER_ADMIN, OffsetDateTime.now());
        if (enabled + pending >= 2) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_SUPER_ADMIN_LIMIT,
                Map.of("maximumActiveSeats", 2));
        }
        return role;
    }

    private void validateSecondSuperAdminSlotAtActivation() {
        long enabled = userRoles.countEnabledUsersByRoleCode(SUPER_ADMIN);
        long pending = invitations.countActiveByRoleCode(SUPER_ADMIN, OffsetDateTime.now());
        if (enabled >= 2 || enabled + pending > 2) throw invalidInvitation();
    }

    private PlatformRole lockSuperAdminSeat() {
        return roles.findByRoleCodeForUpdate(SUPER_ADMIN)
            .orElseThrow(() -> new IllegalStateException("Required platform role is missing"));
    }

    private void consumeOutstandingActivations(Long invitationId, OffsetDateTime now) {
        for (PlatformAdminInvitationActivation activation
                : activations.findByInvitationIdAndConsumedAtIsNull(invitationId)) {
            activation.setConsumedAt(now);
            activations.save(activation);
        }
    }

    private void releaseExpiredReservation(String email) {
        activeEmails.findById(email).ifPresent(reservation -> {
            PlatformAdminInvitation invitation = invitations.findById(reservation.getInvitationId())
                .orElse(null);
            if (invitation == null || !isActive(invitation)) {
                activeEmails.release(email, reservation.getInvitationId());
            }
        });
    }

    private BusinessException invalidInvitation() {
        return new BusinessException(ErrorKeys.AUTH_FAILED,
            Map.of("reason", "PLATFORM_INVITATION_INVALID"));
    }

    private BusinessException invalidActivationMfa(String mfaReason, int attempts) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("reason", "MFA_VERIFICATION_FAILED");
        details.put("mfaReason", mfaReason);
        details.put("remainingAttempts",
            Math.max(0, mfaProperties.getMaxAttempts() - attempts));
        if ("TEMPORARILY_LOCKED".equals(mfaReason)) {
            details.put("retryAfterSeconds", mfaProperties.getLockMinutes() * 60);
        }
        return new BusinessException(ErrorKeys.AUTH_FAILED, details);
    }
}
