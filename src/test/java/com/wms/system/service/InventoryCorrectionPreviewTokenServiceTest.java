package com.wms.system.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryCorrectionPreviewTokenServiceTest {

    private static final byte[] KEY =
        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-08-25T04:00:00Z");

    @Test
    void roundTripBindsExactCorrectionAndBatchSnapshot() {
        InventoryCorrectionPreviewTokenService service = serviceAt(NOW);

        InventoryCorrectionPreviewTokenService.IssuedPreview issued = service.issue(
            99L, 3, 11L, 7, 95, 20
        );

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThatCode(() -> service.verify(
            issued.token(), 99L, 3, 11L, 7, 95, 20
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsExpiredToken() {
        InventoryCorrectionPreviewTokenService.IssuedPreview issued = serviceAt(NOW).issue(
            99L, 3, 11L, 7, 95, 20
        );

        assertStale(() -> serviceAt(NOW.plus(Duration.ofMinutes(6))).verify(
            issued.token(), 99L, 3, 11L, 7, 95, 20
        ), "TOKEN_EXPIRED");
    }

    @Test
    void rejectsTamperedToken() {
        InventoryCorrectionPreviewTokenService service = serviceAt(NOW);
        String token = service.issue(99L, 3, 11L, 7, 95, 20).token();
        String tampered = (token.charAt(0) == 'A' ? "B" : "A") + token.substring(1);

        assertStale(() -> service.verify(tampered, 99L, 3, 11L, 7, 95, 20),
            "SIGNATURE_INVALID");
    }

    @Test
    void rejectsChangedCorrectionOrBatchVersionAndQuantity() {
        InventoryCorrectionPreviewTokenService service = serviceAt(NOW);
        String token = service.issue(99L, 3, 11L, 7, 95, 20).token();

        assertStale(() -> service.verify(token, 99L, 4, 11L, 7, 95, 20),
            "SNAPSHOT_CHANGED");
        assertStale(() -> service.verify(token, 99L, 3, 11L, 8, 95, 20),
            "SNAPSHOT_CHANGED");
        assertStale(() -> service.verify(token, 99L, 3, 11L, 7, 96, 20),
            "SNAPSHOT_CHANGED");
    }

    private InventoryCorrectionPreviewTokenService serviceAt(Instant instant) {
        return new InventoryCorrectionPreviewTokenService(
            KEY,
            Clock.fixed(instant, ZoneOffset.UTC),
            Duration.ofMinutes(5)
        );
    }

    private void assertStale(Runnable invocation, String reason) {
        assertThatThrownBy(invocation::run)
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_PREVIEW_STALE);
                assertThat(exception.getParam("reason")).isEqualTo(reason);
            });
    }
}
