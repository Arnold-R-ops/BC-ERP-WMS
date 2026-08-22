package com.wms.system;

import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
import com.wms.system.tenant.persistence.CompanyScopedJpaRepository;
import com.wms.system.tenant.persistence.CompanyRlsJpaTransactionManager;
import com.wms.system.tenant.persistence.CompanyTenantIdentifierResolver;
import jakarta.persistence.EntityManagerFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import java.security.SecureRandom;
import java.util.TimeZone;

/**
 * WMS System Main Application
 *
 * Architecture Standards:
 * - Backend Timezone: UTC (absolute time storage for international systems)
 * - Frontend: Converts UTC to local timezone for display
 * - JPA Auditing: Enabled via JpaAuditingConfig
 * - Retry Mechanism: Enabled for optimistic lock conflict handling
 * - Database: PostgreSQL 16 with UTF-8 encoding
 * - Security: Spring Security + JWT authentication
 *
 * @author WMS Team
 * @since 2025-01-09
 * @version 3.0 (Security Layer + Auto User Initialization)
 */
@Slf4j
@SpringBootApplication
@EnableRetry  // Enable retry mechanism for @Retryable in InventoryService
@EnableScheduling
@EnableTransactionManagement
@EnableJpaRepositories(repositoryBaseClass = CompanyScopedJpaRepository.class)
public class WmsSystemApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(WmsSystemApplication.class, args);
    }

    /**
     * Registered on the root application configuration so JPA test slices and
     * the production application use the exact same company discriminator.
     */
    @Bean
    public CompanyTenantIdentifierResolver companyTenantIdentifierResolver() {
        return new CompanyTenantIdentifierResolver();
    }

    /** Sets PostgreSQL's transaction-local company id before any ORM query. */
    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource) {
        return new CompanyRlsJpaTransactionManager(entityManagerFactory, dataSource);
    }

    /**
     * Force JVM timezone to UTC on application startup
     *
     * International Architecture Decision:
     * - Backend stores absolute time in UTC (no timezone ambiguity)
     * - Frontend displays time in user's local timezone
     * - Supports multi-region deployments (China, UK, US, etc.)
     *
     * This ensures consistent time handling regardless of server location.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.out.println("=======================================================");
        System.out.println("=== WMS System Started Successfully                 ===");
        System.out.println("=== Backend Timezone: UTC (International Standard)  ===");
        System.out.println("=======================================================");
    }

    /**
     * Bootstrap Admin User (first startup only)
     *
     * Security contract (P0 fix):
     * - Creates the admin user ONLY when it does not exist yet.
     * - If the admin user already exists, its password is NEVER touched.
     *   (The previous behaviour force-reset the password to a hardcoded
     *   value on every startup, which made any manual password change
     *   revert on restart.)
     * - The initial password comes from the ADMIN_INITIAL_PASSWORD
     *   environment variable; when absent, a random password is
     *   generated and printed to the console exactly once.
     *
     * v3.3 Multi-Role System:
     * - Role assignment is handled via sys_user_role (Flyway V3_3).
     *
     * @param userRepository User repository for database operations
     * @param passwordEncoder BCrypt password encoder from SecurityConfig
     * @param initialPassword Optional initial password from environment
     * @return CommandLineRunner that bootstraps the admin user
     */
    @Bean
    @Transactional
    @ConditionalOnProperty(
        name = "wms.legacy-admin-bootstrap-enabled",
        havingValue = "true"
    )
    public CommandLineRunner initAdminUser(UserRepository userRepository,
                                            PasswordEncoder passwordEncoder,
                                            @Value("${ADMIN_INITIAL_PASSWORD:}") String initialPassword) {
        return args -> {
            String adminUsername = "admin";

            try {
                if (userRepository.findByUsername(adminUsername).isPresent()) {
                    log.info("[STARTUP] Admin user exists - leaving credentials untouched");
                    return;
                }

                String adminPassword = initialPassword;
                boolean generated = false;
                if (adminPassword == null || adminPassword.isBlank()) {
                    adminPassword = generateRandomPassword();
                    generated = true;
                }

                User adminUser = User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .displayName("System Administrator")
                    .enabled(true)
                    .remark("Auto-created admin user on first startup")
                    .build();

                userRepository.save(adminUser);

                System.out.println("\n=======================================================");
                System.out.println("=== [STARTUP] Admin user created (first run)        ===");
                System.out.println("===   Username: " + adminUsername);
                if (generated) {
                    System.out.println("===   Initial password (SHOWN ONCE, change it now): ===");
                    System.out.println("===   " + adminPassword);
                } else {
                    System.out.println("===   Initial password: from ADMIN_INITIAL_PASSWORD ===");
                }
                System.out.println("=======================================================\n");

            } catch (Exception e) {
                log.error("[STARTUP] Failed to bootstrap admin user", e);
                throw new RuntimeException("Admin user initialization failed", e);
            }
        };
    }

    /**
     * Generate a random 20-char password from a URL-safe alphabet.
     * Used only when ADMIN_INITIAL_PASSWORD is not provided on first startup.
     */
    private static String generateRandomPassword() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
