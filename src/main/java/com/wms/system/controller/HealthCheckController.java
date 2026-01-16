package com.wms.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * System Health Check Controller
 *
 * Provides health status endpoint for monitoring system availability.
 *
 * @author WMS Team
 * @since 2025-01-09
 * @version 2.1 (Simplified Timezone Handling)
 */
@RestController
@RequestMapping("/health")
public class HealthCheckController {

    /**
     * Health Check Endpoint
     *
     * Access URL: http://localhost:8080/health/check
     *
     * Returns UTC timestamp (JVM timezone is forced to UTC)
     *
     * @return System status information
     */
    @GetMapping("/check")
    public Map<String, Object> healthCheck() {
        return Map.of(
                "status", "UP",
                "message", "WMS System is running successfully!",
                "timestamp", LocalDateTime.now(),
                "java_version", System.getProperty("java.version"),
                "spring_boot_version", "3.2.11"
        );
    }
}
