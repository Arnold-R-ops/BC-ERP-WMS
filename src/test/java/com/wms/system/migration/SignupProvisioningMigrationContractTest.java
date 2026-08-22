package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SignupProvisioningMigrationContractTest {
    @Test
    void v453AddsProvisioningDetailsAndSingleUseHandoff() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_53__Signup_Verification_Provisioning_And_Handoff.sql"));
        assertThat(sql)
            .contains("ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255)")
            .contains("ADD COLUMN IF NOT EXISTS identity_id BIGINT")
            .contains("CREATE TABLE IF NOT EXISTS session_handoff_codes")
            .contains("CONSTRAINT uk_session_handoff_codes_signup UNIQUE (signup_request_id)")
            .contains("expires_at TIMESTAMPTZ NOT NULL")
            .contains("consumed_at TIMESTAMPTZ");
    }
}
