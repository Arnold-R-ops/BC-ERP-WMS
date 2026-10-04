package com.wms.system.tenant.security;

import com.wms.system.tenant.config.TenantSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component @RequiredArgsConstructor
public class TenantCredentialCrypto {
    private final TenantSecurityProperties properties;
    public void requireConfigured() { key(); }
    public String fingerprint(String value) {
        if (value == null || value.isBlank()) return null;
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(reveal(value).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private byte[] key() {
        try {
            byte[] value = Base64.getDecoder().decode(properties.getCredentialEncryptionKey());
            if (value.length != 32) throw new IllegalArgumentException();
            return value;
        } catch (Exception invalid) { throw new IllegalStateException("Tenant credential encryption key is required (32-byte Base64)"); }
    }
    public String protect(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.startsWith("tc1.")) { reveal(value); return value; }
        // Existing installations stay compatible while controls are disabled. Enabling controls requires a key.
        if (!properties.isControlsEnabled() && properties.getCredentialEncryptionKey() == null) return value;
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD("bcwms-tenant-credential-v1".getBytes(StandardCharsets.UTF_8));
            return "tc1." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException("Credential protection failed"); }
    }
    public String reveal(String value) {
        if (value == null || !value.startsWith("tc1.")) return value;
        try {
            String[] parts = value.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"),
                new GCMParameterSpec(128, Base64.getUrlDecoder().decode(parts[1])));
            cipher.updateAAD("bcwms-tenant-credential-v1".getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new IllegalStateException("Credential cannot be decrypted"); }
    }
}
