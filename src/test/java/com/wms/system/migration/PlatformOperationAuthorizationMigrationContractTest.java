package com.wms.system.migration;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformOperationAuthorizationMigrationContractTest {
    @Test void operationCapabilityIsOneTimeAndNotGeneralSqlAccess() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V4_56__Platform_Company_Operation_Authorizations.sql"));
        assertThat(sql).contains("request_fingerprint", "consumed_at", "execution_key",
            "PLATFORM_TENANT_WRITE", "PLATFORM_TENANT_DELETE", "'PENDING','APPROVED','REVOKED','EXPIRED','CONSUMED','FAILED'");
        assertThat(sql).doesNotContain("GRANT ALL", "EXECUTE IMMEDIATE", "arbitrary");
    }
}
