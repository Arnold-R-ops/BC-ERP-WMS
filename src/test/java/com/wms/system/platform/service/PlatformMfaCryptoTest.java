package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformMfaProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMfaCryptoTest {
    private PlatformMfaCrypto crypto() {
        PlatformMfaProperties properties = new PlatformMfaProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new PlatformMfaCrypto(properties);
    }

    @Test void encryptsWithRandomizedAuthenticatedEncryption() {
        PlatformMfaCrypto crypto = crypto();
        String first = crypto.encrypt("JBSWY3DPEHPK3PXP");
        String second = crypto.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(first).isNotEqualTo(second).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(crypto.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test void verifiesStandardTotpVectorWithinOneTimeStepOnly() {
        PlatformMfaCrypto crypto = crypto();
        String rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(crypto.verifyTotp(rfcSecret, "287082", Instant.ofEpochSecond(59))).isTrue();
        assertThat(crypto.verifyTotp(rfcSecret, "287083", Instant.ofEpochSecond(59))).isFalse();
    }

    @Test void challengeRecoveryAndStoredTokenValuesHaveExpectedShape() {
        PlatformMfaCrypto crypto = crypto();
        assertThat(crypto.newChallengeToken()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(crypto.hashToken("challenge")).hasSize(64).doesNotContain("challenge");
        assertThat(crypto.newRecoveryCode()).matches("[A-Z2-9]{5}-[A-Z2-9]{5}");
        assertThat(crypto.normalizeRecoveryCode("abcde-f2345")).isEqualTo("ABCDEF2345");
    }
}
