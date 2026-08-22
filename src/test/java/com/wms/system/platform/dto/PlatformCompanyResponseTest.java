package com.wms.system.platform.dto;

import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCompanyResponseTest {

    @Test
    void legacyDefaultCompanyUsesTenantTerminologyOnlyInPlatformResponse() {
        Tenant tenant = Tenant.builder()
            .tenantCode("COMPANY-000001")
            .displayName("默认公司")
            .slug("default")
            .status(TenantStatus.ACTIVE)
            .build();

        PlatformCompanyResponse response = PlatformCompanyResponse.from(tenant);

        assertThat(response.displayName()).isEqualTo("默认租户");
        assertThat(tenant.getDisplayName()).isEqualTo("默认公司");
    }

    @Test
    void customerProvidedTenantNamesAreNotRewritten() {
        Tenant tenant = Tenant.builder()
            .tenantCode("TENANT-002")
            .displayName("华东贸易公司")
            .slug("east-trading")
            .status(TenantStatus.ACTIVE)
            .build();

        assertThat(PlatformCompanyResponse.from(tenant).displayName()).isEqualTo("华东贸易公司");
    }
}
