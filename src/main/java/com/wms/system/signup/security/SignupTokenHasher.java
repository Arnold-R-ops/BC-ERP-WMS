package com.wms.system.signup.security;

import com.wms.system.signup.config.SignupProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class SignupTokenHasher {
    private final SignupProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SignupTokenHasher(SignupProperties properties) {
        this.properties = properties;
    }

    public String verificationCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    public String opaqueCode() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    public String hash(String purpose, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                (purpose + ":" + properties.getTokenPepper() + ":" + value)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean matches(String purpose, String raw, String expectedHash) {
        return MessageDigest.isEqual(
            hash(purpose, raw).getBytes(StandardCharsets.US_ASCII),
            expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
