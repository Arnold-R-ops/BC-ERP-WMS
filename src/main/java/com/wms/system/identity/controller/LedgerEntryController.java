package com.wms.system.identity.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wms.system.identity.service.LedgerAccountFinder;
import com.wms.system.tenant.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/public/v1")
public class LedgerEntryController {
    private final LedgerAccountFinder finder;
    private final com.wms.system.tenant.config.TenancyProperties properties;
    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
        .maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();

    public LedgerEntryController(LedgerAccountFinder finder, com.wms.system.tenant.config.TenancyProperties properties) {
        this.finder = finder;
        this.properties = properties;
    }

    @GetMapping("/entry")
    public ResponseEntity<Map<String, String>> entry() {
        String surface = TenantContextHolder.current().map(c -> c.surface().name()).orElse("LOCAL_DEVELOPMENT");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "surface", surface, "baseDomain", properties.getBaseDomain().trim().toLowerCase(java.util.Locale.ROOT)));
    }

    @PostMapping("/account-finder")
    public ResponseEntity<Map<String, String>> find(@Valid @RequestBody FindAccount request, HttpServletRequest http) {
        if (attempts.get(clientAddress(http), key -> new AtomicInteger()).incrementAndGet() > 20)
            return ResponseEntity.status(429).header("Retry-After", "60").cacheControl(CacheControl.noStore())
                .body(Map.of("errorKey", "ACCOUNT_FINDER_RATE_LIMITED"));
        return finder.find(request.email())
            .map(url -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("loginUrl", url)))
            .orElseGet(() -> ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                .body(Map.of("errorKey", "ACCOUNT_FINDER_UNAVAILABLE")));
    }

    public record FindAccount(@NotBlank @Email @Size(max = 254) String email) {
        public FindAccount { if (email != null) email = email.trim(); }
    }

    private String clientAddress(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        // Production nginx connects over loopback and overwrites this header.
        // Forwarded headers from any other peer have no authority.
        if (java.util.Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(peer)) {
            String forwarded = request.getHeader("X-Real-IP");
            if (forwarded != null && forwarded.length() <= 45
                    && forwarded.matches("[0-9a-fA-F:.]+") && (forwarded.contains(".") || forwarded.contains(":")))
                return forwarded.toLowerCase(java.util.Locale.ROOT);
        }
        return peer;
    }
}
