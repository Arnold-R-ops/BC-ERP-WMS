package com.wms.system.tenant.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenancyPropertiesTest {

    @Test
    void foundationReleaseCannotBeEnabledByEnvironmentConfigurationAlone() {
        TenancyProperties properties = new TenancyProperties();
        properties.setEnabled(true);

        assertFalse(properties.isSafeToEnable());
    }

    @Test
    void disabledFoundationConfigurationIsValid() {
        assertTrue(new TenancyProperties().isSafeToEnable());
    }

    @Test
    void appAndPlatformHostsMustBeDistinctChildrenOfBaseDomain() {
        TenancyProperties properties = new TenancyProperties();
        assertTrue(properties.isHostTopologyValid());

        properties.setPlatformHost("app.bcwms.com");
        assertFalse(properties.isHostTopologyValid());

        properties.setPlatformHost("platform.example.com");
        assertFalse(properties.isHostTopologyValid());
    }
}
