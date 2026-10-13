package com.wms.system.tenant.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenancyPropertiesTest {

    @Test
    void tenancyCannotBeEnabledWithUnsafeDevelopmentDefaults() {
        TenancyProperties properties = new TenancyProperties();
        properties.setEnabled(true);

        assertFalse(properties.isSafeToEnable());
    }

    @Test
    void disabledLocalConfigurationIsValid() {
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

    @Test
    void ledgerRootIsPublicHostAndEnabledModeRequiresSafeRoleWithoutLocalFallback() {
        TenancyProperties p = new TenancyProperties();
        p.setBaseDomain(" LEDGER.FONGVO.COM ");
        assertEquals("ledger.fongvo.com", p.getBaseDomain());
        p.setAppHost("ledger.fongvo.com");
        p.setPlatformHost("ops.ledger.fongvo.com");
        p.setEnabled(true);
        p.setRequireSafeDatabaseRole(true);
        assertTrue(p.isHostTopologyValid());
        assertFalse(p.isSafeToEnable());
        p.setAllowLocalDevelopmentHost(false);
        assertTrue(p.isSafeToEnable());
        for (String invalid : java.util.List.of("https://ledger.fongvo.com", "ledger.fongvo.com:443", "*.ledger.fongvo.com", "ledger.fongvo.com.", "ledger..fongvo.com", "ledger.fongvo.com/")) {
            p.setAppHost(invalid);
            assertFalse(p.isHostTopologyValid(), invalid);
        }
    }
}
