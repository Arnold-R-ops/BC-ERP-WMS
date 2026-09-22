package com.wms.system.platform.service;

import com.wms.system.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/** Browser grouping only: this cookie never authenticates a user or grants API access. */
@Service
public class PlatformBrowserBinding {
    private static final String COOKIE = "__Secure-bcwms-platform-browser";
    private static final String LOCAL_COOKIE = "bcwms-platform-browser-loopback";
    private final JwtProperties keys;
    private final SecureRandom random = new SecureRandom();

    public PlatformBrowserBinding(JwtProperties keys) { this.keys = keys; }

    public void initialize(HttpServletRequest request, HttpServletResponse response) {
        // An explicit non-simple same-origin request; no authentication material in its response body.
        String site = request.getHeader("Sec-Fetch-Site");
        if (!"1".equals(request.getHeader("X-Platform-Browser-Init"))
            || (site != null && !"same-origin".equals(site) && !"none".equals(site))) throw invalid();
        String value = read(request);
        if (value == null) {
            byte[] nonce = new byte[32]; random.nextBytes(nonce);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
            value = encoded + "." + signature(encoded);
        }
        boolean local = loopbackHttp(request);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(local ? LOCAL_COOKIE : COOKIE, value)
            .httpOnly(true).secure(!local).sameSite("Strict").path("/api/platform")
            .maxAge(Duration.ofHours(12)).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    public String requireHash(HttpServletRequest request) {
        String value = request == null ? null : read(request);
        if (value == null) throw invalid();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private String read(HttpServletRequest request) {
        String expectedName = loopbackHttp(request) ? LOCAL_COOKIE : COOKIE;
        String value = null;
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (!expectedName.equals(cookie.getName())) continue;
            if (value != null) throw invalid(); // Ambiguous duplicate cookies fail closed.
            value = cookie.getValue();
        }
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}\\.[A-Za-z0-9_-]{43}")) return null;
        String encoded = value.substring(0,43);
        return MessageDigest.isEqual(signature(encoded).getBytes(StandardCharsets.US_ASCII),
            value.substring(44).getBytes(StandardCharsets.US_ASCII)) ? value : null;
    }

    private String signature(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(keys.getPlatformSecret()), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                ("BCWMS:platform-browser:v1:" + value).getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException(failure); }
    }

    private static boolean loopbackHttp(HttpServletRequest request) {
        // Never trust forwarded headers for this exception. Both peer and host must be literal loopback.
        return !request.isSecure() && "127.0.0.1".equals(request.getServerName())
            && "127.0.0.1".equals(request.getRemoteAddr());
    }

    private static PlatformSessionException invalid() {
        return new PlatformSessionException("PLATFORM_BROWSER_BINDING_REQUIRED");
    }
}
