package com.wms.system.signup.repository;

import com.wms.system.signup.model.SessionHandoffCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionHandoffCodeRepository extends JpaRepository<SessionHandoffCode, Long> {
    Optional<SessionHandoffCode> findByCodeHash(String codeHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select handoff from SessionHandoffCode handoff where handoff.codeHash = :codeHash")
    Optional<SessionHandoffCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);

    Optional<SessionHandoffCode> findBySignupRequestId(Long signupRequestId);
}
