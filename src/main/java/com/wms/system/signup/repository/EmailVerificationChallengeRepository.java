package com.wms.system.signup.repository;

import com.wms.system.signup.model.EmailVerificationChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.OffsetDateTime;
import jakarta.persistence.LockModeType;

public interface EmailVerificationChallengeRepository extends JpaRepository<EmailVerificationChallenge, Long> {
    Optional<EmailVerificationChallenge> findFirstBySignupRequestIdOrderByCreatedAtDesc(Long signupRequestId);
    long countBySignupRequestIdAndCreatedAtGreaterThanEqual(Long signupRequestId, OffsetDateTime since);

    @Query("""
        select count(challenge) from EmailVerificationChallenge challenge
        join SignupRequest request on request.id = challenge.signupRequestId
        where request.normalizedEmail = :email and challenge.createdAt >= :since
        """)
    long countRecentByEmail(
        @Param("email") String normalizedEmail,
        @Param("since") OffsetDateTime since);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from EmailVerificationChallenge challenge where challenge.id = :id")
    Optional<EmailVerificationChallenge> findByIdForUpdate(@Param("id") Long id);
}
