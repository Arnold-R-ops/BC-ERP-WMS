package com.wms.system.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageStatusFormatterTest {

    @Test
    void identifiesFullLooseAndMixedStock() {
        assertThat(PackageStatusFormatter.plain(24, 12)).isEqualTo("整箱");
        assertThat(PackageStatusFormatter.plain(5, 12)).isEqualTo("散货");
        assertThat(PackageStatusFormatter.plain(85, 12)).isEqualTo("整散混合");
    }

    @Test
    void iconStatusUsesSameCompositionRule() {
        assertThat(PackageStatusFormatter.withIcon(85, 12)).contains("整散混合");
    }
}
