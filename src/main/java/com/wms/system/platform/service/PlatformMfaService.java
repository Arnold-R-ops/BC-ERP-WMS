package com.wms.system.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAuthResponse;
import com.wms.system.platform.dto.PlatformAdminMfaResetRequest;
import com.wms.system.platform.dto.PlatformLoginRequest;
import com.wms.system.platform.dto.PlatformMfaVerifyRequest;
import com.wms.system.platform.dto.PlatformReauthenticationChallengeResponse;
import com.wms.system.platform.dto.PlatformRecoveryCodeRegenerationRequest;
import com.wms.system.platform.dto.PlatformRecoveryCodeStatusResponse;
import com.wms.system.platform.dto.PlatformRecoveryCodesResponse;
import com.wms.system.platform.dto.PlatformSessionResponse;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.security.JwtProperties;
import com.wms.system.security.JwtUtil;
import com.wms.system.security.PlatformUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformMfaService {
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final String MFA_CODE_INVALID = "CODE_INVALID";
    private static final String MFA_CHALLENGE_INVALID = "CHALLENGE_INVALID";
    private static final String MFA_CHALLENGE_EXPIRED = "CHALLENGE_EXPIRED";
    private static final String MFA_TEMPORARILY_LOCKED = "TEMPORARILY_LOCKED";
    private final PlatformUserRepository userRepository;
    private final PlatformMfaChallengeRepository challengeRepository;
    private final PlatformUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformMfaCrypto crypto;
    private final PlatformMfaProperties properties;
    private final PlatformAuditService auditService;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final PlatformAccessGuard accessGuard;

    @Transactional
    public PlatformAuthResponse login(PlatformLoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        PlatformUser user = userRepository.findByNormalizedEmailAndEnabledTrue(normalizedEmail)
            .filter(candidate -> passwordEncoder.matches(request.getPassword(), candidate.getPasswordHash()))
            .orElseThrow(this::invalidCredentials);
        List<String> roles = userDetailsService.loadRoleCodes(user.getId());
        if (roles.isEmpty()) throw invalidCredentials();
        ensureNotLocked(user);

        boolean enrollment = !Boolean.TRUE.equals(user.getMfaEnabled());
        return authenticationChallenge(user, roles, enrollment);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public PlatformAuthResponse confirmEnrollment(PlatformMfaVerifyRequest request, HttpServletRequest http) {
        PlatformMfaChallenge challenge = challenge(request.getChallengeToken(), "ENROLL");
        PlatformUser user = userRepository.findById(challenge.getPlatformUserId()).orElseThrow(this::invalidMfa);
        ensureNotLocked(user);
        String secret = crypto.decrypt(challenge.getPendingSecretEncrypted());
        if (!crypto.verifyTotp(secret, request.getCode(), java.time.Instant.now())) {
            int attempts = fail(user, challenge, http);
            throw invalidCode(attempts);
        }

        RecoveryCodeSet recoveryCodeSet = newRecoveryCodes();
        user.setMfaSecretEncrypted(challenge.getPendingSecretEncrypted());
        user.setRecoveryCodeHashesJson(json(recoveryCodeSet.hashes()));
        user.setMfaEnabled(true); user.setMfaEnrolledAt(OffsetDateTime.now()); resetFailures(user);
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        challenge.setConsumedAt(OffsetDateTime.now());
        userRepository.save(user); challengeRepository.save(challenge);
        auditService.record(user.getId(), null, "MFA_ENROLLED", "platform_identity", "SUCCESS", Map.of(), http);
        return authenticated(user, recoveryCodeSet.codes());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public PlatformAuthResponse verify(PlatformMfaVerifyRequest request, HttpServletRequest http) {
        PlatformMfaChallenge challenge = challenge(request.getChallengeToken(), "VERIFY");
        PlatformUser user = userRepository.findById(challenge.getPlatformUserId()).orElseThrow(this::invalidMfa);
        ensureNotLocked(user);
        boolean recovery = request.getRecoveryCode() != null && !request.getRecoveryCode().isBlank();
        boolean verified;
        if (recovery) {
            verified = consumeRecoveryCode(user, request.getRecoveryCode());
        } else {
            verified = crypto.verifyTotp(crypto.decrypt(user.getMfaSecretEncrypted()), request.getCode(), java.time.Instant.now());
        }
        if (!verified) {
            int attempts = fail(user, challenge, http);
            throw invalidCode(attempts);
        }

        resetFailures(user); challenge.setConsumedAt(OffsetDateTime.now());
        userRepository.save(user); challengeRepository.save(challenge);
        auditService.record(user.getId(), null, recovery ? "MFA_RECOVERY_USED" : "MFA_VERIFIED",
            "platform_identity", "SUCCESS", Map.of(), http);
        return authenticated(user, null);
    }

    @Transactional(readOnly = true)
    public PlatformSessionResponse currentSession(String authorizationHeader) {
        var principal = accessGuard.requirePlatformUser();
        PlatformUser user = userRepository.findById(principal.getId())
            .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
            .orElseThrow(this::invalidCredentials);
        String token = bearerToken(authorizationHeader);
        OffsetDateTime expiresAt = jwtUtil.getPlatformTokenExpiration(token).toInstant().atOffset(ZoneOffset.UTC);
        return new PlatformSessionResponse(
            user.getNormalizedEmail(),
            userDetailsService.loadRoleCodes(user.getId()),
            Boolean.TRUE.equals(user.getMfaEnabled()),
            expiresAt
        );
    }

    @Transactional
    public PlatformReauthenticationChallengeResponse startLogoutAll() {
        var principal = accessGuard.requirePlatformUser();
        PlatformUser user = userRepository.findById(principal.getId()).orElseThrow(this::invalidMfa);
        ensureNotLocked(user);
        if (!Boolean.TRUE.equals(user.getMfaEnabled()) || user.getMfaSecretEncrypted() == null) {
            throw invalidMfa();
        }

        String challengeToken = crypto.newChallengeToken();
        challengeRepository.save(PlatformMfaChallenge.builder()
            .tokenHash(crypto.hashToken(challengeToken))
            .platformUserId(user.getId())
            .purpose("LOGOUT_ALL")
            .expiresAt(OffsetDateTime.now().plusMinutes(properties.getChallengeMinutes()))
            .build());
        return new PlatformReauthenticationChallengeResponse(
            challengeToken,
            properties.getChallengeMinutes() * 60_000L
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void logoutAll(PlatformMfaVerifyRequest request, HttpServletRequest http) {
        var principal = accessGuard.requirePlatformUser();
        PlatformMfaChallenge challenge = challenge(request.getChallengeToken(), "LOGOUT_ALL");
        if (!principal.getId().equals(challenge.getPlatformUserId())) throw invalidMfa();
        PlatformUser user = userRepository.findById(challenge.getPlatformUserId()).orElseThrow(this::invalidMfa);
        ensureNotLocked(user);

        boolean verified = request.getCode() != null
            && request.getCode().matches("\\d{6}")
            && crypto.verifyTotp(crypto.decrypt(user.getMfaSecretEncrypted()), request.getCode(), java.time.Instant.now());
        if (!verified) {
            fail(user, challenge, http);
            throw invalidMfa();
        }

        resetFailures(user);
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        challenge.setConsumedAt(OffsetDateTime.now());
        userRepository.save(user);
        challengeRepository.save(challenge);
        auditService.record(user.getId(), null, "SESSIONS_REVOKED", "platform_identity", "SUCCESS",
            Map.of("scope", "ALL_PLATFORM_SESSIONS"), http);
    }

    @Transactional(readOnly = true)
    public PlatformRecoveryCodeStatusResponse recoveryCodeStatus() {
        var principal = accessGuard.requirePlatformUser();
        PlatformUser user = userRepository.findById(principal.getId()).orElseThrow(this::invalidMfa);
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) throw invalidMfa();
        return new PlatformRecoveryCodeStatusResponse(recoveryHashes(user.getRecoveryCodeHashesJson()).size());
    }

    @Transactional
    public PlatformReauthenticationChallengeResponse startRecoveryCodeRegeneration() {
        var principal = accessGuard.requirePlatformUser();
        PlatformUser user = userRepository.findById(principal.getId()).orElseThrow(this::invalidMfa);
        ensureNotLocked(user);
        if (!Boolean.TRUE.equals(user.getMfaEnabled()) || user.getMfaSecretEncrypted() == null) throw invalidMfa();
        return createChallenge(user, "RECOVERY_REGEN");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public PlatformRecoveryCodesResponse regenerateRecoveryCodes(
        PlatformRecoveryCodeRegenerationRequest request,
        HttpServletRequest http
    ) {
        var principal = accessGuard.requirePlatformUser();
        PlatformMfaChallenge challenge = challenge(request.getChallengeToken(), "RECOVERY_REGEN");
        if (!principal.getId().equals(challenge.getPlatformUserId())) throw invalidMfa();
        PlatformUser user = userRepository.findById(challenge.getPlatformUserId()).orElseThrow(this::invalidMfa);
        ensureNotLocked(user);

        boolean passwordVerified = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        boolean totpVerified = crypto.verifyTotp(
            crypto.decrypt(user.getMfaSecretEncrypted()), request.getCode(), java.time.Instant.now());
        if (!passwordVerified || !totpVerified) {
            fail(user, challenge, http, "RECOVERY_CODES_REGENERATE");
            throw invalidMfa();
        }

        RecoveryCodeSet recoveryCodeSet = newRecoveryCodes();
        user.setRecoveryCodeHashesJson(json(recoveryCodeSet.hashes()));
        resetFailures(user);
        challenge.setConsumedAt(OffsetDateTime.now());
        userRepository.save(user);
        challengeRepository.save(challenge);
        auditService.record(user.getId(), null, "MFA_RECOVERY_REGENERATED", "platform_identity", "SUCCESS",
            Map.of("remaining", RECOVERY_CODE_COUNT), http);
        return new PlatformRecoveryCodesResponse(recoveryCodeSet.codes(), RECOVERY_CODE_COUNT);
    }

    @Transactional
    public PlatformReauthenticationChallengeResponse startAdminMfaReset(Long targetUserId) {
        var actorPrincipal = accessGuard.requireSuperAdmin();
        requireDifferentAdministrator(actorPrincipal.getId(), targetUserId);
        PlatformUser actor = userRepository.findById(actorPrincipal.getId()).orElseThrow(this::invalidMfa);
        PlatformUser target = userRepository.findById(targetUserId)
            .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
            .orElseThrow(() -> new IllegalArgumentException("Target platform administrator is unavailable"));
        ensureNotLocked(actor);
        if (!Boolean.TRUE.equals(actor.getMfaEnabled()) || actor.getMfaSecretEncrypted() == null) throw invalidMfa();
        if (!Boolean.TRUE.equals(target.getMfaEnabled())) {
            throw new IllegalArgumentException("Target platform administrator has no active MFA binding");
        }

        return createChallenge(actor, "ADMIN_MFA_RESET", target.getId());
    }

    @Transactional
    public PlatformReauthenticationChallengeResponse startAdminInvitation() {
        var principal=accessGuard.requireSuperAdmin();
        PlatformUser actor=userRepository.findById(principal.getId()).orElseThrow(this::invalidMfa);
        ensureNotLocked(actor);
        if(!Boolean.TRUE.equals(actor.getMfaEnabled())||actor.getMfaSecretEncrypted()==null)throw invalidMfa();
        return createChallenge(actor,"ADMIN_INVITE");
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public PlatformUser verifyAdminInvitation(String challengeToken,String password,String code,HttpServletRequest http) {
        var principal=accessGuard.requireSuperAdmin();
        PlatformMfaChallenge challenge=challenge(challengeToken,"ADMIN_INVITE");
        if(!principal.getId().equals(challenge.getPlatformUserId()))throw invalidMfa();
        PlatformUser actor=userRepository.findById(principal.getId()).orElseThrow(this::invalidMfa);
        ensureNotLocked(actor);
        boolean valid=passwordEncoder.matches(password,actor.getPasswordHash())&&actor.getMfaSecretEncrypted()!=null
            &&crypto.verifyTotp(crypto.decrypt(actor.getMfaSecretEncrypted()),code,java.time.Instant.now());
        if(!valid){fail(actor,challenge,http,"ADMIN_INVITE");throw invalidMfa();}
        resetFailures(actor); challenge.setConsumedAt(OffsetDateTime.now()); userRepository.save(actor); challengeRepository.save(challenge);
        return actor;
    }

    @Transactional
    public PlatformAuthResponse startInvitedEnrollment(PlatformUser user) {
        if(Boolean.TRUE.equals(user.getMfaEnabled()))throw invalidMfa();
        List<String> roles=userDetailsService.loadRoleCodes(user.getId());
        if(roles.isEmpty())throw invalidCredentials();
        return authenticationChallenge(user,roles,true);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void adminResetMfa(
        Long targetUserId,
        PlatformAdminMfaResetRequest request,
        HttpServletRequest http
    ) {
        var actorPrincipal = accessGuard.requireSuperAdmin();
        requireDifferentAdministrator(actorPrincipal.getId(), targetUserId);
        PlatformMfaChallenge challenge = challenge(request.getChallengeToken(), "ADMIN_MFA_RESET");
        if (!actorPrincipal.getId().equals(challenge.getPlatformUserId())
            || !targetUserId.equals(challenge.getTargetPlatformUserId())) {
            throw invalidMfa();
        }

        PlatformUser actor = userRepository.findById(actorPrincipal.getId()).orElseThrow(this::invalidMfa);
        PlatformUser target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new IllegalArgumentException("Target platform administrator is unavailable"));
        ensureNotLocked(actor);
        String reason = request.getReason().trim();
        auditService.record(actor.getId(), null, "MFA_RESET", "platform_identity", "REQUESTED",
            resetAuditDetail(target, reason, false), http);
        boolean passwordVerified = passwordEncoder.matches(request.getPassword(), actor.getPasswordHash());
        boolean totpVerified = actor.getMfaSecretEncrypted() != null
            && crypto.verifyTotp(crypto.decrypt(actor.getMfaSecretEncrypted()), request.getCode(), java.time.Instant.now());
        if (!passwordVerified || !totpVerified) {
            fail(actor, challenge, http, "ADMIN_MFA_RESET");
            auditService.record(actor.getId(), null, "MFA_RESET", "platform_identity", "FAILED",
                resetAuditDetail(target, reason, false), http);
            throw invalidMfa();
        }

        target.setMfaEnabled(false);
        target.setMfaSecretEncrypted(null);
        target.setRecoveryCodeHashesJson(null);
        target.setMfaEnrolledAt(null);
        target.setMfaFailedAttempts(0);
        target.setMfaLockedUntil(null);
        target.setSecurityVersion(target.getSecurityVersion() + 1);
        challengeRepository.deleteByPlatformUserId(target.getId());
        challenge.setConsumedAt(OffsetDateTime.now());
        resetFailures(actor);
        userRepository.save(target);
        userRepository.save(actor);
        challengeRepository.save(challenge);
        auditService.record(actor.getId(), null, "MFA_RESET", "platform_identity", "SUCCESS",
            resetAuditDetail(target, reason, true), http);
    }

    private PlatformAuthResponse authenticated(PlatformUser user, List<String> recoveryCodes) {
        List<String> roles = userDetailsService.loadRoleCodes(user.getId());
        String token = jwtUtil.generatePlatformToken(user.getId(), user.getNormalizedEmail(), roles, user.getSecurityVersion());
        return new PlatformAuthResponse("AUTHENTICATED", null, null, null, token, "Bearer",
            user.getNormalizedEmail(), roles, jwtProperties.getPlatformExpiration(), recoveryCodes);
    }

    private PlatformAuthResponse authenticationChallenge(PlatformUser user,List<String> roles,boolean enrollment){
        String challengeToken=crypto.newChallengeToken();
        String secret=enrollment?crypto.newTotpSecret():null;
        challengeRepository.save(PlatformMfaChallenge.builder().tokenHash(crypto.hashToken(challengeToken))
            .platformUserId(user.getId()).purpose(enrollment?"ENROLL":"VERIFY")
            .pendingSecretEncrypted(enrollment?crypto.encrypt(secret):null)
            .expiresAt(OffsetDateTime.now().plusMinutes(properties.getChallengeMinutes())).build());
        return new PlatformAuthResponse(enrollment?"MFA_ENROLLMENT_REQUIRED":"MFA_REQUIRED",challengeToken,secret,
            enrollment?otpauth(user,secret):null,null,null,user.getNormalizedEmail(),roles,0,null);
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw invalidCredentials();
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) throw invalidCredentials();
        return token;
    }

    private PlatformReauthenticationChallengeResponse createChallenge(PlatformUser user, String purpose) {
        return createChallenge(user, purpose, null);
    }

    private PlatformReauthenticationChallengeResponse createChallenge(
        PlatformUser user,
        String purpose,
        Long targetPlatformUserId
    ) {
        String challengeToken = crypto.newChallengeToken();
        challengeRepository.save(PlatformMfaChallenge.builder()
            .tokenHash(crypto.hashToken(challengeToken))
            .platformUserId(user.getId())
            .targetPlatformUserId(targetPlatformUserId)
            .purpose(purpose)
            .expiresAt(OffsetDateTime.now().plusMinutes(properties.getChallengeMinutes()))
            .build());
        return new PlatformReauthenticationChallengeResponse(
            challengeToken, properties.getChallengeMinutes() * 60_000L);
    }

    private PlatformMfaChallenge challenge(String token, String purpose) {
        if (token == null || token.isBlank()) throw invalidMfa(MFA_CHALLENGE_INVALID);
        PlatformMfaChallenge result = challengeRepository.findByTokenHashForUpdate(crypto.hashToken(token))
            .orElseThrow(() -> invalidMfa(MFA_CHALLENGE_INVALID));
        if (!purpose.equals(result.getPurpose()) || result.getConsumedAt() != null) {
            throw invalidMfa(MFA_CHALLENGE_INVALID);
        }
        if (!OffsetDateTime.now().isBefore(result.getExpiresAt())) {
            throw invalidMfa(MFA_CHALLENGE_EXPIRED);
        }
        if (result.getAttemptCount() >= properties.getMaxAttempts()) {
            throw invalidMfa(MFA_TEMPORARILY_LOCKED);
        }
        return result;
    }

    private int fail(PlatformUser user, PlatformMfaChallenge challenge, HttpServletRequest http) {
        return fail(user, challenge, http, "MFA_VERIFY");
    }

    private int fail(PlatformUser user, PlatformMfaChallenge challenge, HttpServletRequest http, String operation) {
        int attempts = Math.min(properties.getMaxAttempts(), user.getMfaFailedAttempts() + 1);
        user.setMfaFailedAttempts(attempts);
        challenge.setAttemptCount(Math.min(properties.getMaxAttempts(), challenge.getAttemptCount() + 1));
        if (attempts >= properties.getMaxAttempts()) user.setMfaLockedUntil(OffsetDateTime.now().plusMinutes(properties.getLockMinutes()));
        userRepository.save(user); challengeRepository.save(challenge);
        auditService.record(user.getId(), null, "MFA_FAILED", "platform_identity", "FAILED",
            Map.of("attempts", attempts, "locked", attempts >= properties.getMaxAttempts(),
                "operation", operation), http);
        return attempts;
    }

    private void ensureNotLocked(PlatformUser user) {
        OffsetDateTime lockedUntil = user.getMfaLockedUntil();
        if (lockedUntil != null && OffsetDateTime.now().isBefore(lockedUntil)) {
            throw invalidMfa(MFA_TEMPORARILY_LOCKED);
        }
        if (lockedUntil != null) resetFailures(user);
    }

    private void resetFailures(PlatformUser user) {
        user.setMfaFailedAttempts(0); user.setMfaLockedUntil(null);
    }

    private boolean consumeRecoveryCode(PlatformUser user, String supplied) {
        String normalized = crypto.normalizeRecoveryCode(supplied);
        if (normalized.length() != 10) return false;
        List<String> hashes = recoveryHashes(user.getRecoveryCodeHashesJson());
        for (int index = 0; index < hashes.size(); index++) {
            if (passwordEncoder.matches(normalized, hashes.get(index))) {
                hashes.remove(index); user.setRecoveryCodeHashesJson(json(hashes)); return true;
            }
        }
        return false;
    }

    private List<String> recoveryHashes(String json) {
        try { return json == null ? new ArrayList<>() : new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {})); }
        catch (Exception exception) { throw new IllegalStateException("Platform recovery codes are unreadable", exception); }
    }

    private RecoveryCodeSet newRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            String code = crypto.newRecoveryCode();
            codes.add(code);
            hashes.add(passwordEncoder.encode(crypto.normalizeRecoveryCode(code)));
        }
        return new RecoveryCodeSet(codes, hashes);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to store platform recovery codes", exception); }
    }

    private String otpauth(PlatformUser user, String secret) {
        String issuer = "BCWMS Platform";
        String label = issuer + ":" + user.getNormalizedEmail();
        return "otpauth://totp/" + URLEncoder.encode(label, StandardCharsets.UTF_8).replace("+", "%20")
            + "?secret=" + secret + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
            + "&algorithm=SHA1&digits=6&period=30";
    }

    private void requireDifferentAdministrator(Long actorUserId, Long targetUserId) {
        if (targetUserId == null) throw new IllegalArgumentException("Target platform administrator is required");
        if (actorUserId.equals(targetUserId)) {
            throw new AccessDeniedException("Platform administrators cannot reset their own MFA");
        }
    }

    private Map<String, Object> resetAuditDetail(PlatformUser target, String reason, boolean sessionsRevoked) {
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("targetPlatformUserId", target.getId());
        detail.put("targetSuperAdmin", userDetailsService.loadRoleCodes(target.getId()).contains("PLATFORM_SUPER_ADMIN"));
        detail.put("sessionsRevoked", sessionsRevoked);
        detail.put("backgroundTasksPreserved", true);
        if (reason != null && !reason.isBlank()) detail.put("reason", reason);
        return detail;
    }

    private BusinessException invalidCredentials() { return new BusinessException(ErrorKeys.AUTH_INVALID_CREDENTIALS, Map.of()); }
    private BusinessException invalidMfa() { return new BusinessException(ErrorKeys.AUTH_FAILED, Map.of("reason", "MFA_VERIFICATION_FAILED")); }
    private BusinessException invalidMfa(String mfaReason) {
        return new BusinessException(ErrorKeys.AUTH_FAILED,
            Map.of("reason", "MFA_VERIFICATION_FAILED", "mfaReason", mfaReason));
    }
    private BusinessException invalidCode(int attempts) {
        boolean locked = attempts >= properties.getMaxAttempts();
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("reason", "MFA_VERIFICATION_FAILED");
        detail.put("mfaReason", locked ? MFA_TEMPORARILY_LOCKED : MFA_CODE_INVALID);
        detail.put("remainingAttempts", Math.max(0, properties.getMaxAttempts() - attempts));
        if (locked) detail.put("retryAfterSeconds", properties.getLockMinutes() * 60);
        return new BusinessException(ErrorKeys.AUTH_FAILED, detail);
    }

    private record RecoveryCodeSet(List<String> codes, List<String> hashes) { }
}
