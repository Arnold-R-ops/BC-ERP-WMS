package com.wms.system.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyRequestRepositoryContractTest {

    @Test
    void reservationIsCompanyScopedInsertOnConflict() throws Exception {
        Method method = IdempotencyRequestRepository.class.getMethod(
            "insertReservation",
            Long.class,
            String.class,
            String.class,
            String.class,
            LocalDateTime.class,
            String.class
        );

        Query query = method.getAnnotation(Query.class);
        Modifying modifying = method.getAnnotation(Modifying.class);
        String sql = query.value().replaceAll("\\s+", " ").toLowerCase();

        assertThat(query.nativeQuery()).isTrue();
        assertThat(modifying.flushAutomatically()).isTrue();
        assertThat(sql)
            .contains("company_id, idempotency_key")
            .contains("on conflict (company_id, idempotency_key) do nothing")
            .contains("lease_token");
    }

    @Test
    void competingClaimsLockTheCompanyAndKeyRow() throws Exception {
        Method method = IdempotencyRequestRepository.class.getMethod(
            "findByCompanyAndKeyForUpdate",
            Long.class,
            String.class
        );

        Query query = method.getAnnotation(Query.class);
        String sql = query.value().replaceAll("\\s+", " ").toLowerCase();

        assertThat(query.nativeQuery()).isTrue();
        assertThat(sql)
            .contains("where company_id = :companyid")
            .contains("idempotency_key = :idempotencykey")
            .contains("for update");
    }
}
