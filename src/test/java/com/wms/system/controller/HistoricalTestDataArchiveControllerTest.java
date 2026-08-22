package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.sales.HistoricalTestDataArchivePreview;
import com.wms.system.dto.sales.HistoricalTestDataArchiveRequest;
import com.wms.system.dto.sales.HistoricalTestDataArchiveResult;
import com.wms.system.service.HistoricalTestDataArchiveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class HistoricalTestDataArchiveControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private HistoricalTestDataArchiveService archiveService;

    @Test
    @WithMockUser(username = "admin", authorities = "TENANT_ADMIN")
    void superAdminCanPreview() throws Exception {
        when(archiveService.preview(13L)).thenReturn(HistoricalTestDataArchivePreview.builder()
            .salesOrderId(13L)
            .orderNo("SO-V4.4-TEST-13")
            .orderStatus("APPROVED_AWAITING_SHIPMENT")
            .eligible(true)
            .blockers(List.of())
            .taskIds(List.of(1L, 2L))
            .snapshotFingerprint("a".repeat(64))
            .build());

        mockMvc.perform(get("/api/admin/historical-test-data/sales-orders/13/archive-preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eligible").value(true))
            .andExpect(jsonPath("$.taskIds[0]").value(1));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "TENANT_ADMIN")
    void superAdminCanSubmitValidatedRequest() throws Exception {
        HistoricalTestDataArchiveRequest request = HistoricalTestDataArchiveRequest.builder()
            .reason("Archive V4.4 historical test data")
            .confirmationOrderNo("SO-V4.4-TEST-13")
            .expectedFingerprint("a".repeat(64))
            .build();
        when(archiveService.archive(eq(13L), any(), any(), eq("admin")))
            .thenReturn(HistoricalTestDataArchiveResult.builder()
                .salesOrderId(13L)
                .orderNo("SO-V4.4-TEST-13")
                .orderStatus("VOIDED")
                .archivedTaskIds(List.of(1L, 2L))
                .auditId(100L)
                .archivedAt(LocalDateTime.now())
                .inventoryChanged(false)
                .reservationsChanged(false)
                .stockTransactionsCreated(false)
                .build());

        mockMvc.perform(post("/api/admin/historical-test-data/sales-orders/13/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("VOIDED"))
            .andExpect(jsonPath("$.inventoryChanged").value(false));
    }

    @Test
    @WithMockUser(username = "operator", authorities = "sales:cancel")
    void ordinaryCancellationPermissionCannotAccessArchive() throws Exception {
        mockMvc.perform(get("/api/admin/historical-test-data/sales-orders/13/archive-preview"))
            .andExpect(status().isForbidden());
        verify(archiveService, never()).preview(any());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "TENANT_ADMIN")
    void invalidSubmissionIsRejectedBeforeService() throws Exception {
        HistoricalTestDataArchiveRequest request = HistoricalTestDataArchiveRequest.builder()
            .reason("")
            .confirmationOrderNo("")
            .expectedFingerprint("short")
            .build();

        mockMvc.perform(post("/api/admin/historical-test-data/sales-orders/13/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
        verify(archiveService, never()).archive(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "TENANT_ADMIN")
    void openApiPublishesPreviewAndArchiveContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/admin/historical-test-data/sales-orders/{id}/archive-preview'].get").exists())
            .andExpect(jsonPath("$.paths['/api/admin/historical-test-data/sales-orders/{id}/archive'].post").exists())
            .andExpect(jsonPath("$.components.schemas.HistoricalTestDataArchiveRequest.required").isArray());
    }
}
