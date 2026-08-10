package com.wms.system.repository;

import com.wms.system.entity.HistoricalTestDataRegistry;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface HistoricalTestDataRegistryRepository
    extends Repository<HistoricalTestDataRegistry, Long> {

    Optional<HistoricalTestDataRegistry> findByCompanyIdAndSalesOrderId(
        Long companyId,
        Long salesOrderId
    );
}
