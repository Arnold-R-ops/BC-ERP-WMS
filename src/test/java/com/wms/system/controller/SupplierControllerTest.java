package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.supplier.CreateSupplierRequest;
import com.wms.system.dto.supplier.SupplierResponse;
import com.wms.system.dto.supplier.UpdateSupplierRequest;
import com.wms.system.service.SupplierService;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("SupplierController Tests")
class SupplierControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private SupplierService supplierService;

    private SupplierResponse response() {
        return new SupplierResponse(
            7L, 1L, "SUP-TEST", "Test Supplier", "Buyer", null,
            "supplier@example.com", null, "Preferred", true, null, null
        );
    }

    @Test
    @WithMockUser(authorities = {"supplier:view"})
    void listReturnsSupplierMasterData() throws Exception {
        when(supplierService.list(true)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/suppliers").param("activeOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("SUP-TEST"));
    }

    @Test
    @WithMockUser(authorities = {"supplier:create"})
    void createReturns201() throws Exception {
        CreateSupplierRequest request = new CreateSupplierRequest(
            "SUP-TEST", "Test Supplier", null, null, null, null, null
        );
        when(supplierService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(authorities = {"supplier:update"})
    void updateAndDeactivateUseDedicatedOperations() throws Exception {
        UpdateSupplierRequest request = new UpdateSupplierRequest(
            "Updated Supplier", null, null, null, null, null
        );
        when(supplierService.update(any(), any())).thenReturn(response());
        when(supplierService.deactivate(7L)).thenReturn(response());

        mockMvc.perform(put("/api/suppliers/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/suppliers/7/deactivate"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"supplier:delete"})
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/suppliers/7"))
            .andExpect(status().isNoContent());

        verify(supplierService).delete(7L);
    }

    @Test
    @WithMockUser(authorities = {"supplier:view"})
    void writeWithoutWritePermissionReturns403() throws Exception {
        CreateSupplierRequest request = new CreateSupplierRequest(
            "SUP-TEST", "Test Supplier", null, null, null, null, null
        );

        mockMvc.perform(post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }
}
