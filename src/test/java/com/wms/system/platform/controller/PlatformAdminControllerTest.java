package com.wms.system.platform.controller;

import com.wms.system.controller.GlobalExceptionHandler;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.dto.PlatformAdminDetailResponse;
import com.wms.system.platform.dto.PlatformAdminRoleResponse;
import com.wms.system.platform.dto.PlatformAdminSummaryResponse;
import com.wms.system.platform.service.PlatformAdminDirectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformAdminControllerTest {
    private final PlatformAdminDirectoryService service = mock(PlatformAdminDirectoryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlatformAdminController(service)).build();

    @Test
    void exposesReadOnlyDirectoryAndDetailWithoutSensitiveIdentityFields() throws Exception {
        OffsetDateTime enrolledAt = OffsetDateTime.parse("2026-08-20T09:00:00+08:00");
        PlatformAdminRoleResponse role = new PlatformAdminRoleResponse(
            "PLATFORM_SUPER_ADMIN", "平台超级管理员", "JOB_ROLE");
        PlatformAdminSummaryResponse summary = new PlatformAdminSummaryResponse(
            7L, "Administrator", "admin@example.com", true, List.of(role), "ENROLLED",
            enrolledAt, null, enrolledAt.minusDays(2), enrolledAt, 3L, true, true);
        when(service.list(eq("admin"), eq(true), eq("PLATFORM_SUPER_ADMIN"), eq("ENROLLED"),
            eq(0), eq(20), any()))
            .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/platform/admins")
                .param("keyword", "admin").param("enabled", "true")
                .param("role", "PLATFORM_SUPER_ADMIN").param("mfaStatus", "ENROLLED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(7))
            .andExpect(jsonPath("$.content[0].roles[0].type").value("JOB_ROLE"))
            .andExpect(jsonPath("$.content[0].mfaStatus").value("ENROLLED"))
            .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.content[0].mfaSecretEncrypted").doesNotExist())
            .andExpect(jsonPath("$.content[0].recoveryCodeHashesJson").doesNotExist())
            .andExpect(jsonPath("$.content[0].securityVersion").doesNotExist());
        verify(service).list(eq("admin"), eq(true), eq("PLATFORM_SUPER_ADMIN"), eq("ENROLLED"),
            eq(0), eq(20), any());

        PlatformAdminDetailResponse detail = new PlatformAdminDetailResponse(
            7L, "Administrator", "admin@example.com", true, List.of(role), "ENROLLED",
            enrolledAt, null, enrolledAt.minusDays(2), enrolledAt, 3L,
            Map.of("READ", 2L, "EXPORT", 1L), true, true);
        when(service.detail(org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(detail);

        mvc.perform(get("/api/platform/admins/7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeGrantCounts.READ").value(2))
            .andExpect(jsonPath("$.activeGrantCounts.EXPORT").value(1))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.mfaSecretEncrypted").doesNotExist());
        verify(service).detail(org.mockito.ArgumentMatchers.eq(7L), any());
    }

    @Test
    void mapsMissingAdministratorToStableNotFoundContract() throws Exception {
        MockMvc advisedMvc = MockMvcBuilders.standaloneSetup(new PlatformAdminController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        when(service.detail(eq(404L), any()))
            .thenThrow(new BusinessException(ErrorKeys.PLATFORM_ADMIN_NOT_FOUND,
                Map.of("targetUserId", 404L)));

        advisedMvc.perform(get("/api/platform/admins/404"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorKey").value("PLATFORM_ADMIN_NOT_FOUND"))
            .andExpect(jsonPath("$.params.targetUserId").value(404));
    }
}
