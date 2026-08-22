package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformMfaProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Component
public class PlatformMfaCrypto {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final char[] RECOVERY = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final byte[] encryptionKey;

    public PlatformMfaCrypto(PlatformMfaProperties properties) {
        try {
            byte[] source = Base64.getDecoder().decode(properties.getEncryptionKey());
            if (source.length < 32) throw new IllegalArgumentException("Platform MFA encryption key must be at least 256 bits");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("bcwms-platform-mfa-aes-gcm-v1".getBytes(StandardCharsets.UTF_8));
            encryptionKey = digest.digest(source);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize platform MFA cryptography", exception);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD("platform-mfa-v1".getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect platform MFA secret", exception);
        }
    }

    public String decrypt(String encrypted) {
        try {
            String[] parts = encrypted.split("\\.");
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("Unsupported protected value");
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD("platform-mfa-v1".getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read platform MFA secret", exception);
        }
    }

    public String newTotpSecret() {
        byte[] bytes = new byte[20]; random.nextBytes(bytes); return base32(bytes);
    }

    public String newChallengeToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String newRecoveryCode() {
        StringBuilder value = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) value.append('-');
            value.append(RECOVERY[random.nextInt(RECOVERY.length)]);
        }
        return value.toString();
    }

    public String hashToken(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public boolean verifyTotp(String secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long step = now.getEpochSecond() / 30;
        for (long offset = -1; offset <= 1; offset++) {
            String expected = totp(secret, step + offset);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) return true;
        }
        return false;
    }

    public String normalizeRecoveryCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z2-9]", "");
    }

    private String totp(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = digest[digest.length - 1] & 0xf;
            int binary = ((digest[offset] & 0x7f) << 24) | ((digest[offset + 1] & 0xff) << 16)
                | ((digest[offset + 2] & 0xff) << 8) | (digest[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception exception) { throw new IllegalStateException("Unable to verify TOTP", exception); }
    }

    private String base32(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff); bits += 8;
            while (bits >= 5) { result.append(BASE32[(buffer >> (bits - 5)) & 31]); bits -= 5; }
        }
        if (bits > 0) result.append(BASE32[(buffer << (5 - bits)) & 31]);
        return result.toString();
    }

    private byte[] base32Decode(String value) {
        ByteBuffer output = ByteBuffer.allocate(value.length() * 5 / 8 + 1);
        int buffer = 0, bits = 0;
        for (char character : value.toUpperCase(Locale.ROOT).toCharArray()) {
            int index = character >= 'A' && character <= 'Z' ? character - 'A'
                : character >= '2' && character <= '7' ? character - '2' + 26 : -1;
            if (index < 0) continue;
            buffer = (buffer << 5) | index; bits += 5;
            if (bits >= 8) { output.put((byte) ((buffer >> (bits - 8)) & 0xff)); bits -= 8; }
        }
        byte[] result = new byte[output.position()]; output.flip(); output.get(result); return result;
    }
}
