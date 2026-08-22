package com.wms.system.signup;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.identity.repository.UserIdentityRepository;
import com.wms.system.signup.config.SignupProperties;
import com.wms.system.signup.dto.BeginSignupRequest;
import com.wms.system.signup.dto.VerifyEmailRequest;
import com.wms.system.signup.mail.VerificationMailSender;
import com.wms.system.signup.model.EmailVerificationChallenge;
import com.wms.system.signup.model.SignupRequest;
import com.wms.system.signup.model.SignupStatus;
import com.wms.system.signup.repository.EmailVerificationChallengeRepository;
import com.wms.system.signup.repository.SignupRequestRepository;
import com.wms.system.signup.security.SignupTokenHasher;
import com.wms.system.signup.service.SignupVerificationService;
import com.wms.system.subscription.model.PlanCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupVerificationServiceTest {
    @Mock SignupRequestRepository signupRepository;
    @Mock EmailVerificationChallengeRepository challengeRepository;
    @Mock UserIdentityRepository identityRepository;
    @Mock VerificationMailSender mailSender;
    @Mock JdbcTemplate jdbcTemplate;
    SignupProperties properties;
    SignupTokenHasher hasher;
    SignupVerificationService service;

    @BeforeEach
    void setUp() {
        Environment environment = mock(Environment.class);
        properties = new SignupProperties(environment);
        properties.setTokenPepper("test-pepper-that-is-not-production");
        hasher = new SignupTokenHasher(properties);
        service = new SignupVerificationService(signupRepository, challengeRepository,
            identityRepository, hasher, mailSender, properties, jdbcTemplate);
    }

    @Test
    void sameIdempotencyKeyWithDifferentRequestIsRejected() {
        SignupRequest existing = SignupRequest.builder()
            .id(1L).publicId("signup-1").idempotencyKey("key-1")
            .normalizedEmail("a@example.com").requestedPlanCode("FREE")
            .requestFingerprint(hasher.hash("signup-request", "a@example.com:FREE"))
            .status(SignupStatus.EMAIL_PENDING).expiresAt(OffsetDateTime.now().plusHours(1)).build();
        when(signupRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.begin(
            new BeginSignupRequest("b@example.com", PlanCode.TRIAL), "key-1"))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.SIGNUP_IDEMPOTENCY_CONFLICT);
        verifyNoInteractions(mailSender);
    }

    @Test
    void fifthWrongCodeLocksChallengeWithoutSendingCodeToLogsOrResponse() {
        SignupRequest signup = SignupRequest.builder()
            .id(7L).publicId("signup-7").idempotencyKey("key-7")
            .normalizedEmail("owner@example.com").requestedPlanCode("FREE")
            .status(SignupStatus.EMAIL_PENDING).expiresAt(OffsetDateTime.now().plusHours(1)).build();
        EmailVerificationChallenge challenge = EmailVerificationChallenge.builder()
            .id(9L).signupRequestId(7L)
            .codeHash(hasher.hash("email-code:signup-7", "123456"))
            .attemptCount(4).maxAttempts(5).expiresAt(OffsetDateTime.now().plusMinutes(10)).build();
        when(signupRepository.findByPublicIdForUpdate("signup-7")).thenReturn(Optional.of(signup));
        when(challengeRepository.findFirstBySignupRequestIdOrderByCreatedAtDesc(7L))
            .thenReturn(Optional.of(challenge));
        when(challengeRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.verify(new VerifyEmailRequest("signup-7", "000000")))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.SIGNUP_VERIFICATION_ATTEMPTS_EXCEEDED);
        assertThat(challenge.getAttemptCount()).isEqualTo(5);
        verify(challengeRepository).save(challenge);
    }

    @Test
    void resendInsideCooldownIsRejected() {
        SignupRequest signup = SignupRequest.builder()
            .id(7L).publicId("signup-7").normalizedEmail("owner@example.com")
            .status(SignupStatus.EMAIL_PENDING).expiresAt(OffsetDateTime.now().plusHours(1)).build();
        EmailVerificationChallenge challenge = EmailVerificationChallenge.builder()
            .id(9L).signupRequestId(7L).attemptCount(0).maxAttempts(5)
            .codeHash("hash").createdAt(OffsetDateTime.now())
            .expiresAt(OffsetDateTime.now().plusMinutes(10)).build();
        when(signupRepository.findByPublicIdForUpdate("signup-7")).thenReturn(Optional.of(signup));
        when(challengeRepository.findFirstBySignupRequestIdOrderByCreatedAtDesc(7L))
            .thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.resend("signup-7"))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.SIGNUP_RESEND_TOO_SOON);
        verifyNoInteractions(mailSender);
    }
}
