package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.v45.EmergencyStockCorrectionResponse;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.entity.enums.Zone;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.EmergencyStockCorrectionService;
import com.wms.system.service.InventoryBatchService;
import com.wms.system.service.InventoryCorrectionPolicyService;
import com.wms.system.service.WarehouseScopeService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class EmergencyStockCorrectionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private EmergencyStockCorrectionService correctionService;
    @MockBean private InventoryBatchService inventoryBatchService;
    @MockBean private InventoryCorrectionPolicyService policyService;
    @MockBean private WarehouseScopeService warehouseScopeService;

    @Test
    @WithMockUser(username = "maker", authorities = "inventory:correction:execute")
    void executePermissionCreatesOnlyInsideTheAuthorizedWarehouseSnapshot() throws Exception {
        when(correctionService.resolveWarehouseId(any())).thenReturn(5L);
        when(correctionService.create(any(), eq(0L), eq(5L))).thenReturn(response());

        mockMvc.perform(post("/api/emergency-stock-corrections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "productSkuId", 1,
                    "locationId", 7,
                    "inventoryBatchId", 11,
                    "countedQty", 90,
                    "reasonCode", "COUNT_VARIANCE",
                    "reasonDetail", "Counted twice"
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(99));

        verify(warehouseScopeService).requireAccess(
            any(), eq(List.of(5L)), eq("EmergencyStockCorrection"), eq("new")
        );
        verify(correctionService).create(any(), eq(0L), eq(5L));
    }

    @Test
    @WithMockUser(username = "maker", authorities = "inventory:correction:execute")
    void executePermissionListsCorrectionSpecificBatchOptionsInsideWarehouseScope() throws Exception {
        InventoryBatch inactiveEmptyBatch = batchOption(false, 0);
        List<InventoryBatch> candidates = List.of(inactiveEmptyBatch);
        when(inventoryBatchService.getCorrectionBatchOptions(1L)).thenReturn(candidates);
        when(warehouseScopeService.filterAccessible(any(), eq(candidates), any()))
            .thenReturn(candidates);

        mockMvc.perform(get("/api/emergency-stock-corrections/batch-options")
                .param("productSkuId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(11))
            .andExpect(jsonPath("$[0].batchCode").value("BATCH-11"))
            .andExpect(jsonPath("$[0].quantity").value(0))
            .andExpect(jsonPath("$[0].locationId").value(7))
            .andExpect(jsonPath("$[0].active").value(false));

        verify(inventoryBatchService).getCorrectionBatchOptions(1L);
        verify(warehouseScopeService).filterAccessible(any(), eq(candidates), any());
    }

    @Test
    @WithMockUser(username = "viewer", authorities = "inventory:correction:view")
    void viewPermissionCannotListCorrectionBatchOptions() throws Exception {
        mockMvc.perform(get("/api/emergency-stock-corrections/batch-options")
                .param("productSkuId", "1"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(inventoryBatchService);
    }

    @Test
    @WithMockUser(username = "viewer", authorities = "inventory:correction:view")
    void viewPermissionCannotCreateOrApply() throws Exception {
        mockMvc.perform(post("/api/emergency-stock-corrections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "productSkuId", 1,
                    "locationId", 7,
                    "inventoryBatchId", 11,
                    "countedQty", 90,
                    "reasonCode", "COUNT_VARIANCE",
                    "reasonDetail", "Counted twice"
                ))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/emergency-stock-corrections/99/apply")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true,\"previewToken\":\"preview-1\"}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(correctionService);
    }

    @Test
    @WithMockUser(username = "viewer", authorities = "inventory:correction:view")
    void viewPermissionReceivesCorrectionScopedDisplayLabels() throws Exception {
        EmergencyStockCorrectionResponse response = response();
        when(correctionService.list(null)).thenReturn(List.of(response));
        when(warehouseScopeService.filterAccessible(any(), eq(List.of(response)), any()))
            .thenReturn(List.of(response));

        mockMvc.perform(get("/api/emergency-stock-corrections"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productSkuCode").value("SKU-001"))
            .andExpect(jsonPath("$[0].productSkuName").value("Test product"))
            .andExpect(jsonPath("$[0].warehouseCode").value("WH-05"))
            .andExpect(jsonPath("$[0].warehouseName").value("Warehouse 5"))
            .andExpect(jsonPath("$[0].locationCode").value("WH-05-A-01"));
    }

    @Test
    @WithMockUser(username = "maker", authorities = "inventory:correction:execute")
    void executePermissionStillCannotListGetReviewApproveOrReject() throws Exception {
        mockMvc.perform(get("/api/emergency-stock-corrections"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/emergency-stock-corrections/99"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/emergency-stock-corrections/99/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"review\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/emergency-stock-corrections/99/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"approve\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/emergency-stock-corrections/99/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"reject\"}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(correctionService, warehouseScopeService);
    }

    @Test
    @WithMockUser(username = "maker", authorities = "inventory:correction:execute")
    void applyRequiresAndForwardsBothIdempotencyAndPreviewProof() throws Exception {
        when(correctionService.getWarehouseId(99L)).thenReturn(5L);
        when(correctionService.apply(99L, 0L, "maker", "key-1", "preview-1"))
            .thenReturn(response());

        mockMvc.perform(post("/api/emergency-stock-corrections/99/apply")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true,\"previewToken\":\"preview-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(correctionService).apply(99L, 0L, "maker", "key-1", "preview-1");
    }

    @Test
    @WithMockUser(username = "maker", authorities = "inventory:correction:execute")
    void applyRejectsMissingHeaderOrPreviewBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/emergency-stock-corrections/99/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true,\"previewToken\":\"preview-1\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/emergency-stock-corrections/99/apply")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true}"))
            .andExpect(status().isBadRequest());

        verify(correctionService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "maker", authorities = "inventory:correction:execute")
    void warehouseScopeDenialBlocksImpactBeforeInventoryFactsAreReturned() throws Exception {
        when(correctionService.getWarehouseId(99L)).thenReturn(5L);
        doThrow(new BusinessException(
            ErrorKeys.WAREHOUSE_SCOPE_DENIED,
            Map.of("warehouseId", 5L)
        )).when(warehouseScopeService).requireAccess(
            any(), eq(List.of(5L)), eq("EmergencyStockCorrection"), eq(99L)
        );

        mockMvc.perform(get("/api/emergency-stock-corrections/99/impact"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorKey").value(ErrorKeys.WAREHOUSE_SCOPE_DENIED));

        verify(correctionService, never()).impact(99L);
    }

    @Test
    @WithMockUser(username = "maker", authorities = "inventory:correction:execute")
    void onlyTenantAdminCanChangeApprovalPolicy() throws Exception {
        mockMvc.perform(put("/api/emergency-stock-corrections/policy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"NONE\",\"reason\":\"change\",\"confirmed\":true}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(policyService);
    }

    private EmergencyStockCorrectionResponse response() {
        return EmergencyStockCorrectionResponse.builder()
            .id(99L)
            .correctionNo("ESC202608250001")
            .productSkuId(1L)
            .productSkuCode("SKU-001")
            .productSkuName("Test product")
            .warehouseId(5L)
            .warehouseCode("WH-05")
            .warehouseName("Warehouse 5")
            .locationId(7L)
            .locationCode("WH-05-A-01")
            .createdBy(0L)
            .approvalModeSnapshot(EmergencyCorrectionApprovalMode.NONE)
            .status(EmergencyCorrectionStatus.DRAFT)
            .build();
    }

    private InventoryBatch batchOption(boolean active, int quantity) {
        Warehouse warehouse = Warehouse.builder()
            .id(5L)
            .code("WH-05")
            .name("Warehouse 5")
            .build();
        Location location = Location.builder()
            .id(7L)
            .warehouse(warehouse)
            .warehouseCode("WH-05")
            .locationCode("WH-05-A-01")
            .zone(Zone.ZONE_A)
            .shelfNumber("A")
            .positionNumber("01")
            .build();
        ProductSku product = ProductSku.builder()
            .id(1L)
            .name("Test product")
            .barcode("BAR-1")
            .build();
        return InventoryBatch.builder()
            .id(11L)
            .batchCode("BATCH-11")
            .productSku(product)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(quantity)
            .reservedQuantity(0)
            .initialQuantity(10)
            .active(active)
            .build();
    }
}
