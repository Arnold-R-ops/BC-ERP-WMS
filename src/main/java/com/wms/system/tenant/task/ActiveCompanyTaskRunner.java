package com.wms.system.tenant.task;

import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/** Runs scheduled/background work once per active company with strict cleanup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveCompanyTaskRunner {

    private final TenantRepository tenantRepository;

    public void forEachActiveCompany(String taskName, Consumer<Tenant> action) {
        for (Tenant tenant : tenantRepository.findAllByStatusOrderByIdAsc(TenantStatus.ACTIVE)) {
            TenantContext context = new TenantContext(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getSlug() + ".background",
                RequestSurface.TENANT,
                tenant.getStatus()
            );
            try {
                TenantContextHolder.runWithTenant(context, () -> action.accept(tenant));
            } catch (RuntimeException exception) {
                log.error("Background task failed: task={}, companyId={}",
                    taskName, tenant.getId(), exception);
            }
        }
    }
}
