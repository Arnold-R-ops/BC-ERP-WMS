package com.wms.system.signup.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.identity.repository.UserIdentityRepository;
import com.wms.system.signup.config.SignupProperties;
import com.wms.system.signup.dto.BeginSignupRequest;
import com.wms.system.signup.dto.SignupResponse;
import com.wms.system.signup.dto.VerifyEmailRequest;
import com.wms.system.signup.mail.VerificationMailSender;
import com.wms.system.signup.model.EmailVerificationChallenge;
import com.wms.system.signup.model.SignupRequest;
import com.wms.system.signup.model.SignupStatus;
import com.wms.system.signup.repository.EmailVerificationChallengeRepository;
import com.wms.system.signup.repository.SignupRequestRepository;
import com.wms.system.signup.security.SignupTokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignupVerificationService {
    private static final List<SignupStatus> LIVE_STATUSES = List.of(
        SignupStatus.EMAIL_PENDING, SignupStatus.EMAIL_VERIFIED,
        SignupStatus.DETAILS_COMPLETED, SignupStatus.PROVISIONING,
        SignupStatus.ACTIVE, SignupStatus.PROVISIONING_FAILED);

    private final SignupRequestRepository signupRepository;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final UserIdentityRepository identityRepository;
    private final SignupTokenHasher tokenHasher;
    private final VerificationMailSender mailSender;
    private final SignupProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public SignupResponse begin(BeginSignupRequest command, String idempotencyKey) {
        String email = normalizeEmail(command.email());
        String key = requireIdempotencyKey(idempotencyKey);
        acquireSignupLocks(email, key);
        String fingerprint = tokenHasher.hash("signup-request", email + ":" + command.planCode());

        SignupRequest existingByKey = signupRepository.findByIdempotencyKey(key).orElse(null);
        if (existingByKey != null) {
            if (!fingerprint.equals(existingByKey.getRequestFingerprint())) {
                throw new BusinessException(ErrorKeys.SIGNUP_IDEMPOTENCY_CONFLICT);
            }
            return response(existingByKey, null, null);
        }
        rejectUnavailableEmail(email);

        SignupRequest signup = SignupRequest.builder()
            .idempotencyKey(key)
            .normalizedEmail(email)
            .requestedPlanCode(command.planCode().name())
            .requestFingerprint(fingerprint)
            .status(SignupStatus.EMAIL_PENDING)
            .expiresAt(now().plus(properties.signupTtl()))
            .build();
        try {
            signup = signupRepository.saveAndFlush(signup);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorKeys.SIGNUP_EMAIL_UNAVAILABLE);
        }
        issueChallenge(signup, false);
        return response(signup, null, null);
    }

    @Transactional
    public SignupResponse resend(String publicId) {
        SignupRequest signup = requireLockedSignup(publicId);
        requireNotExpired(signup);
        if (signup.getStatus() != SignupStatus.EMAIL_PENDING) {
            return response(signup, null, null);
        }
        issueChallenge(signup, true);
        return response(signup, null, null);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public SignupResponse verify(VerifyEmailRequest command) {
        SignupRequest signup = requireLockedSignup(command.signupId());
        requireNotExpired(signup);
        if (signup.getStatus() != SignupStatus.EMAIL_PENDING) {
            return response(signup, null, null);
        }
        EmailVerificationChallenge latest = challengeRepository
            .findFirstBySignupRequestIdOrderByCreatedAtDesc(signup.getId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_VERIFICATION_CODE_INVALID));
        EmailVerificationChallenge challenge = challengeRepository.findByIdForUpdate(latest.getId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_VERIFICATION_CODE_INVALID));
        OffsetDateTime now = now();
        if (challenge.getConsumedAt() != null || !challenge.getExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorKeys.SIGNUP_VERIFICATION_CODE_EXPIRED);
        }
        if (challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            throw new BusinessException(ErrorKeys.SIGNUP_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!tokenHasher.matches("email-code:" + signup.getPublicId(), command.code(), challenge.getCodeHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            if (challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
                throw new BusinessException(ErrorKeys.SIGNUP_VERIFICATION_ATTEMPTS_EXCEEDED);
            }
            throw new BusinessException(ErrorKeys.SIGNUP_VERIFICATION_CODE_INVALID,
                Map.of("attemptsRemaining", challenge.getMaxAttempts() - challenge.getAttemptCount()));
        }
        challenge.setConsumedAt(now);
        challengeRepository.save(challenge);
        signup.setStatus(SignupStatus.EMAIL_VERIFIED);
        signupRepository.save(signup);
        return response(signup, null, null);
    }

    private void issueChallenge(SignupRequest signup, boolean enforceCooldown) {
        OffsetDateTime now = now();
        EmailVerificationChallenge latest = challengeRepository
            .findFirstBySignupRequestIdOrderByCreatedAtDesc(signup.getId()).orElse(null);
        if (enforceCooldown && latest != null
                && latest.getCreatedAt().plus(properties.resendDelay()).isAfter(now)) {
            throw new BusinessException(ErrorKeys.SIGNUP_RESEND_TOO_SOON,
                Map.of("resendAfterSeconds", Math.max(1,
                    java.time.Duration.between(now,
                        latest.getCreatedAt().plus(properties.resendDelay())).toSeconds())));
        }
        long sentToday = challengeRepository.countRecentByEmail(
            signup.getNormalizedEmail(), now.minusDays(1));
        if (sentToday >= properties.getDailySendLimit()) {
            throw new BusinessException(ErrorKeys.SIGNUP_SEND_LIMIT_EXCEEDED);
        }
        String code = tokenHasher.verificationCode();
        challengeRepository.save(EmailVerificationChallenge.builder()
            .signupRequestId(signup.getId())
            .codeHash(tokenHasher.hash("email-code:" + signup.getPublicId(), code))
            .maxAttempts(properties.getMaxAttempts())
            .expiresAt(now.plus(properties.verificationTtl()))
            .build());
        mailSender.sendVerificationCode(signup.getNormalizedEmail(), code,
            properties.getVerificationCodeMinutes());
    }

    private void rejectUnavailableEmail(String email) {
        if (identityRepository.existsByNormalizedEmail(email)
                || !signupRepository.findByNormalizedEmailAndStatusInOrderByCreatedAtDesc(
                    email, LIVE_STATUSES).isEmpty()) {
            throw new BusinessException(ErrorKeys.SIGNUP_EMAIL_UNAVAILABLE);
        }
    }

    private SignupRequest requireLockedSignup(String publicId) {
        return signupRepository.findByPublicIdForUpdate(publicId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_REQUEST_NOT_FOUND));
    }

    private void requireNotExpired(SignupRequest signup) {
        if (!signup.getExpiresAt().isAfter(now())) {
            signup.setStatus(SignupStatus.EXPIRED);
            signupRepository.save(signup);
            throw new BusinessException(ErrorKeys.SIGNUP_REQUEST_EXPIRED);
        }
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 80) {
            throw new BusinessException(ErrorKeys.SIGNUP_INVALID_REQUEST);
        }
        return value.trim();
    }

    private void acquireSignupLocks(String email, String key) {
        java.util.stream.Stream.of("email:" + email, "key:" + key)
            .sorted()
            .forEach(lockValue -> jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                Object.class, lockValue));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    static SignupResponse response(SignupRequest signup, String redirect, String handoff) {
        return new SignupResponse(signup.getPublicId(), signup.getStatus(), mask(signup.getNormalizedEmail()),
            60, signup.getSlug(), redirect, handoff, handoff == null ? null : 60);
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
