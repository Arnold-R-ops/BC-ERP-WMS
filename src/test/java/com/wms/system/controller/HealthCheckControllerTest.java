package com.wms.system.controller;

import com.wms.system.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("HealthCheckController Tests")
class HealthCheckControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser
    @DisplayName("GET /health/check - returns UP status with required fields")
    void testHealthCheck_Success() throws Exception {
        mockMvc.perform(get("/health/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.java_version").isString())
                .andExpect(jsonPath("$.spring_boot_version").value("3.2.11"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /health/check - message confirms system running")
    void testHealthCheck_MessageContent() throws Exception {
        mockMvc.perform(get("/health/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("WMS System")));
    }
}
