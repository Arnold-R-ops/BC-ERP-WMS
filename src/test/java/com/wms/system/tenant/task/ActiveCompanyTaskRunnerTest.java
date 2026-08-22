package com.wms.system.tenant.task;

import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveCompanyTaskRunnerTest {

    @Mock private TenantRepository tenantRepository;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void executesEachActiveCompanyInItsOwnContextAndAlwaysClears() {
        when(tenantRepository.findAllByStatusOrderByIdAsc(TenantStatus.ACTIVE))
            .thenReturn(List.of(tenant(10L, "alpha"), tenant(20L, "beta")));
        ActiveCompanyTaskRunner runner = new ActiveCompanyTaskRunner(tenantRepository);
        List<Long> observed = new ArrayList<>();

        runner.forEachActiveCompany("TEST", tenant ->
            observed.add(TenantContextHolder.requireTenant().tenantId()));

        assertThat(observed).containsExactly(10L, 20L);
        assertThat(TenantContextHolder.current()).isEmpty();
    }

    @Test
    void failureInOneCompanyDoesNotSkipTheNextCompany() {
        when(tenantRepository.findAllByStatusOrderByIdAsc(TenantStatus.ACTIVE))
            .thenReturn(List.of(tenant(10L, "alpha"), tenant(20L, "beta")));
        ActiveCompanyTaskRunner runner = new ActiveCompanyTaskRunner(tenantRepository);
        List<Long> completed = new ArrayList<>();

        runner.forEachActiveCompany("TEST", tenant -> {
            if (tenant.getId().equals(10L)) {
                throw new IllegalStateException("alpha failed");
            }
            completed.add(TenantContextHolder.requireTenant().tenantId());
        });

        assertThat(completed).containsExactly(20L);
        assertThat(TenantContextHolder.current()).isEmpty();
    }

    private Tenant tenant(Long id, String slug) {
        return Tenant.builder().id(id).slug(slug).status(TenantStatus.ACTIVE).build();
    }
}
