package com.wms.system.repository;

import com.wms.system.entity.TransferOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long> {
    List<TransferOrder> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferOrder t where t.id = :id")
    Optional<TransferOrder> findByIdForUpdate(@Param("id") Long id);
}
