package com.wms.system.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InventoryBatchRepository posting lock contract")
class InventoryBatchPostingLockContractTest {

    @Test
    @DisplayName("posting lookup uses a pessimistic write lock")
    void postingLookupUsesPessimisticWriteLock() throws Exception {
        Method method = InventoryBatchRepository.class
            .getMethod("findByIdForPosting", Long.class);

        assertThat(method.getAnnotation(Lock.class))
            .isNotNull()
            .extracting(Lock::value)
            .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(method.getAnnotation(Query.class))
            .isNotNull()
            .extracting(Query::value)
            .asString()
            .contains("WHERE b.id = :batchId");
    }
}
