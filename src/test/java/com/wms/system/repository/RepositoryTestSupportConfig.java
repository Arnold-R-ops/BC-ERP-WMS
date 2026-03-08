package com.wms.system.repository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Shared support config for DataJpa repository tests.
 *
 * Provides the minimum beans required by WmsSystemApplication startup hooks
 * without executing real startup side effects.
 */
@TestConfiguration
public class RepositoryTestSupportConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CommandLineRunner initAdminUser() {
        return args -> {
            // no-op in repository slice tests
        };
    }
}

