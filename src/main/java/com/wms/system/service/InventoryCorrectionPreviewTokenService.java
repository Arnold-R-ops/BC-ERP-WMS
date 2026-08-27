package com.wms.system.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.security.JwtProperties;
import com.wms.system.tenant.context.CompanyScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Issues short-lived, tenant-bound proof that the operator saw the exact stock
 * snapshot later confirmed by an emergency correction apply command.
 */
@Service
public class InventoryCorrectionPreviewTokenService {

    static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final String VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] KEY_CONTEXT =
        "bcwms:inventory-correction-preview:v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec signingKey;
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public InventoryCorrectionPreviewTokenService(JwtProperties properties) {
        this(decodeTenantKey(properties.getTenantSecret()), Clock.systemUTC(), DEFAULT_TTL);
    }

    InventoryCorrectionPreviewTokenService(byte[] tenantKey, Clock clock, Duration ttl) {
        if (tenantKey == null || tenantKey.length < 32) {
            throw new IllegalStateException("Company preview signing key must contain at least 256 bits");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Preview token TTL must be positive");
        }
        this.signingKey = new SecretKeySpec(deriveKey(tenantKey), ALGORITHM);
    }

    public IssuedPreview issue(
        Long correctionId,
        Integer correctionVersion,
        Long batchId,
        Integer batchVersion,
        int currentQuantity,
        int reservedQuantity
    ) {
        Instant expiresAt = clock.instant().plus(ttl);
        PreviewSnapshot snapshot = snapshot(
            correctionId,
            correctionVersion,
            batchId,
            batchVersion,
            currentQuantity,
            reservedQuantity,
            expiresAt.getEpochSecond()
        );
        byte[] payload = serialize(snapshot).getBytes(StandardCharsets.UTF_8);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
            + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
        return new IssuedPreview(token, expiresAt);
    }

    public void verify(
        String token,
        Long correctionId,
        Integer correctionVersion,
        Long batchId,
        Integer batchVersion,
        int currentQuantity,
        int reservedQuantity
    ) {
        if (token == null || token.isBlank()) {
            throw stale(correctionId, "TOKEN_MISSING");
        }

        try {
            String[] tokenParts = token.split("\\.", -1);
            if (tokenParts.length != 2) {
                throw stale(correctionId, "TOKEN_MALFORMED");
            }
            byte[] payload = Base64.getUrlDecoder().decode(tokenParts[0]);
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(tokenParts[1]);
            if (!MessageDigest.isEqual(sign(payload), suppliedSignature)) {
                throw stale(correctionId, "SIGNATURE_INVALID");
            }

            PreviewSnapshot supplied = parse(new String(payload, StandardCharsets.UTF_8));
            long now = clock.instant().getEpochSecond();
            if (supplied.expiresAtEpochSecond() <= now) {
                throw stale(correctionId, "TOKEN_EXPIRED");
            }

            PreviewSnapshot expected = snapshot(
                correctionId,
                correctionVersion,
                batchId,
                batchVersion,
                currentQuantity,
                reservedQuantity,
                supplied.expiresAtEpochSecond()
            );
            if (!supplied.equals(expected)) {
                throw stale(correctionId, "SNAPSHOT_CHANGED");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw stale(correctionId, "TOKEN_MALFORMED");
        }
    }

    private PreviewSnapshot snapshot(
        Long correctionId,
        Integer correctionVersion,
        Long batchId,
        Integer batchVersion,
        int currentQuantity,
        int reservedQuantity,
        long expiresAtEpochSecond
    ) {
        return new PreviewSnapshot(
            CompanyScope.currentCompanyId(),
            Objects.requireNonNull(correctionId, "correctionId"),
            correctionVersion == null ? 0 : correctionVersion,
            batchId == null ? 0L : batchId,
            batchId == null ? -1 : Objects.requireNonNullElse(batchVersion, 0),
            currentQuantity,
            reservedQuantity,
            expiresAtEpochSecond
        );
    }

    private String serialize(PreviewSnapshot snapshot) {
        return String.join(
            "|",
            VERSION,
            snapshot.companyId().toString(),
            snapshot.correctionId().toString(),
            Integer.toString(snapshot.correctionVersion()),
            Long.toString(snapshot.batchId()),
            Integer.toString(snapshot.batchVersion()),
            Integer.toString(snapshot.currentQuantity()),
            Integer.toString(snapshot.reservedQuantity()),
            Long.toString(snapshot.expiresAtEpochSecond())
        );
    }

    private PreviewSnapshot parse(String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 9 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported preview token payload");
        }
        return new PreviewSnapshot(
            Long.valueOf(parts[1]),
            Long.valueOf(parts[2]),
            Integer.parseInt(parts[3]),
            Long.parseLong(parts[4]),
            Integer.parseInt(parts[5]),
            Integer.parseInt(parts[6]),
            Integer.parseInt(parts[7]),
            Long.parseLong(parts[8])
        );
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign inventory correction preview", exception);
        }
    }

    private static byte[] deriveKey(byte[] tenantKey) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(tenantKey, ALGORITHM));
            return mac.doFinal(KEY_CONTEXT);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive inventory correction preview key", exception);
        }
    }

    private static byte[] decodeTenantKey(String encoded) {
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Company preview signing key must be valid Base64", exception);
        }
    }

    private BusinessException stale(Long correctionId, String reason) {
        return new BusinessException(
            ErrorKeys.INVENTORY_CORRECTION_PREVIEW_STALE,
            Map.of("correctionId", correctionId, "reason", reason)
        );
    }

    public record IssuedPreview(String token, Instant expiresAt) {
    }

    private record PreviewSnapshot(
        Long companyId,
        Long correctionId,
        int correctionVersion,
        long batchId,
        int batchVersion,
        int currentQuantity,
        int reservedQuantity,
        long expiresAtEpochSecond
    ) {
    }
}
