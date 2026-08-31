package com.wms.system.repository;

import com.wms.system.entity.SalesReturnChannelCase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesReturnChannelCaseRepository extends JpaRepository<SalesReturnChannelCase, Long> {
    Optional<SalesReturnChannelCase> findByChannelAndStoreIdentifierAndExternalCaseId(
        String channel, String storeIdentifier, String externalCaseId);

    List<SalesReturnChannelCase> findAllByOrderByReceivedAtDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SalesReturnChannelCase c where c.id = :id")
    Optional<SalesReturnChannelCase> findByIdForUpdate(@Param("id") Long id);
}
