package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseRemainderClosureMigrationContractTest {

    @Test
    void migrationEnforcesOnePendingRequestTenantIsolationAndSeparateAuthorities()
        throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_77__Purchase_Remainder_Closure_Approval.sql"
        )).replace("\r\n", "\n");

        assertThat(sql).contains(
            "CREATE TABLE IF NOT EXISTS public.purchase_remainder_closure_request",
            "WHERE status = 'PENDING'",
            "status = 'CANCELLED' OR reviewed_by IS NULL OR reviewed_by <> requested_by",
            "ALTER TABLE public.purchase_remainder_closure_request FORCE ROW LEVEL SECURITY",
            "'purchase:request_close_remaining'",
            "'purchase:approve_close_remaining'",
            "custom_assignable",
            "FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP"
        );
        assertThat(sql).doesNotContain("INSERT INTO public.sys_role_permission");
    }
}
