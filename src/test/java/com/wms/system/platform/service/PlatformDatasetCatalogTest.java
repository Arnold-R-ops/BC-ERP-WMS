package com.wms.system.platform.service;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformDatasetCatalogTest {
    @Test void catalogNeverExposesAuthenticationOrIntegrationSecrets() {
        String catalog = PlatformDatasetCatalog.all().toString().toLowerCase(Locale.ROOT);
        assertThat(catalog).doesNotContain("password", "token", "secret", "api_key", "apikey",
            "verification", "code_hash", "securityversion", "mustchangepassword");
        assertThat(PlatformDatasetCatalog.find("users")).isPresent();
        assertThat(PlatformDatasetCatalog.find("integrations")).isPresent();
        assertThat(PlatformDatasetCatalog.find("arbitrary_table")).isEmpty();
    }
}
