package com.wms.system.signup.repository;

import com.wms.system.signup.model.SignupRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;

public interface SignupRequestRepository extends JpaRepository<SignupRequest, Long> {
    Optional<SignupRequest> findByPublicId(String publicId);
    Optional<SignupRequest> findByIdempotencyKey(String idempotencyKey);
    List<SignupRequest> findByNormalizedEmailAndStatusInOrderByCreatedAtDesc(
        String normalizedEmail, List<com.wms.system.signup.model.SignupStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from SignupRequest request where request.publicId = :publicId")
    Optional<SignupRequest> findByPublicIdForUpdate(@Param("publicId") String publicId);
}
