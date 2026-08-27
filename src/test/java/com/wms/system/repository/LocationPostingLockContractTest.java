package com.wms.system.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LocationPostingLockContractTest {

    @Test
    void inventoryPostingLookupSerializesLocationOccupancyDecisions() throws Exception {
        Method method = LocationRepository.class
            .getMethod("findByIdForInventoryPosting", Long.class);

        assertThat(method.getAnnotation(Lock.class))
            .isNotNull()
            .extracting(Lock::value)
            .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(method.getAnnotation(Query.class))
            .isNotNull()
            .extracting(Query::value)
            .asString()
            .contains("WHERE l.id = :id");
    }
}
