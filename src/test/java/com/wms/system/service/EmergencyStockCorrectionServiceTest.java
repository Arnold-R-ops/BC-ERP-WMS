package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.v45.EmergencyStockCorrectionImpactResponse;
import com.wms.system.dto.v45.EmergencyStockCorrectionRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionResponse;
import com.wms.system.entity.EmergencyStockCorrection;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.EmergencyStockCorrectionRepository;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EmergencyStockCorrectionServiceTest {

    @Mock private EmergencyStockCorrectionRepository correctionRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private LocationOccupancyService locationOccupancyService;
    @Mock private InventoryCorrectionPolicyService policyService;
    @Mock private InventoryCorrectionPreviewTokenService previewTokenService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private EmergencyStockCorrectionApplicationExecutor applicationExecutor;

    private EmergencyStockCorrectionService service;

    @BeforeEach
    void setUp() {
        service = new EmergencyStockCorrectionService(
            correctionRepository,
            inventoryBatchRepository,
            reservationRepository,
            productSkuRepository,
            locationRepository,
            warehouseRepository,
            locationOccupancyService,
            policyService,
            previewTokenService,
            idempotencyService,
            applicationExecutor,
            new ObjectMapper()
        );
        lenient().when(correctionRepository.save(any(EmergencyStockCorrection.class)))
            .thenAnswer(invocation -> {
                EmergencyStockCorrection correction = invocation.getArgument(0);
                if (correction.getId() == null) {
                    correction.setId(99L);
                }
                return correction;
            });
        lenient().when(previewTokenService.issue(
                any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new InventoryCorrectionPreviewTokenService.IssuedPreview(
                "preview-1", Instant.parse("2026-08-25T04:05:00Z")
            ));
        lenient().when(locationOccupancyService.canStore(any(), any(), any()))
            .thenReturn(true);
    }

    @Test
    void createFreezesDeltaButLeavesApprovalModeOpenUntilWorkflowEntry() {
        ProductSku product = product(1L);
        Location location = location(7L, 5L);
        InventoryBatch batch = batch(11L, product, location, 100, 12);
        EmergencyStockCorrectionRequest request = request(1L, 7L, 11L, 90);

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(product));
        when(locationRepository.findByIdForInventoryPosting(7L)).thenReturn(Optional.of(location));
        when(inventoryBatchRepository.findById(11L)).thenReturn(Optional.of(batch));
        when(correctionRepository.existsByCorrectionNo(any())).thenReturn(false);

        EmergencyStockCorrectionResponse response = service.create(request, 21L, 5L);

        assertThat(response.getSystemQty()).isEqualTo(100);
        assertThat(response.getCountedQty()).isEqualTo(90);
        assertThat(response.getAdjustmentQty()).isEqualTo(-10);
        assertThat(response.getWarehouseId()).isEqualTo(5L);
        assertThat(response.getCreatedBy()).isEqualTo(21L);
        assertThat(response.getSubmittedBy()).isNull();
        assertThat(response.getApprovalModeSnapshot()).isNull();
        verify(policyService, never()).effectiveMode();
    }

    @Test
    void warehouseResolutionDoesNotInspectBatchBeforeAuthorization() {
        EmergencyStockCorrectionRequest request = request(1L, 7L, 11L, 90);
        when(locationRepository.findById(7L)).thenReturn(Optional.of(location(7L, 5L)));

        assertThat(service.resolveWarehouseId(request)).isEqualTo(5L);

        verifyNoInteractions(inventoryBatchRepository);
    }

    @Test
    void submitCapturesTheCurrentEffectiveApprovalMode() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.DRAFT,
            null
        );
        when(correctionRepository.findByIdForUpdate(99L))
            .thenReturn(Optional.of(correction));
        when(policyService.effectiveMode())
            .thenReturn(EmergencyCorrectionApprovalMode.TWO_PERSON);

        EmergencyStockCorrectionResponse submitted = service.submit(99L, 21L, "submit");

        assertThat(submitted.getApprovalModeSnapshot())
            .isEqualTo(EmergencyCorrectionApprovalMode.TWO_PERSON);
        assertThat(submitted.getStatus())
            .isEqualTo(EmergencyCorrectionStatus.PENDING_APPROVAL);
        verify(policyService).effectiveMode();
    }

    @Test
    void detailResponseIncludesCorrectionScopedMasterDataLabels() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.PENDING_APPROVAL,
            EmergencyCorrectionApprovalMode.TWO_PERSON
        );
        ProductSku product = ProductSku.builder()
            .id(1L)
            .skuCode("SKU-001")
            .name("验收商品")
            .build();
        Location location = location(7L, 5L);
        Warehouse warehouse = Warehouse.builder()
            .id(5L)
            .code("WH-01")
            .name("主仓")
            .build();
        when(correctionRepository.findById(99L)).thenReturn(Optional.of(correction));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(product));
        when(locationRepository.findById(7L)).thenReturn(Optional.of(location));
        when(warehouseRepository.findById(5L)).thenReturn(Optional.of(warehouse));

        EmergencyStockCorrectionResponse response = service.get(99L);

        assertThat(response.getProductSkuCode()).isEqualTo("SKU-001");
        assertThat(response.getProductSkuName()).isEqualTo("验收商品");
        assertThat(response.getWarehouseCode()).isEqualTo("WH-01");
        assertThat(response.getWarehouseName()).isEqualTo("主仓");
        assertThat(response.getLocationCode()).isEqualTo("L-001");
    }

    @Test
    void listResponseBatchLoadsCorrectionScopedMasterDataLabels() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.PENDING_APPROVAL,
            EmergencyCorrectionApprovalMode.TWO_PERSON
        );
        ProductSku product = ProductSku.builder()
            .id(1L)
            .skuCode("SKU-001")
            .name("验收商品")
            .build();
        Location location = location(7L, 5L);
        Warehouse warehouse = Warehouse.builder()
            .id(5L)
            .code("WH-01")
            .name("主仓")
            .build();
        when(correctionRepository.findAll()).thenReturn(List.of(correction));
        when(productSkuRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(locationRepository.findAllById(List.of(7L))).thenReturn(List.of(location));
        when(warehouseRepository.findAllById(List.of(5L))).thenReturn(List.of(warehouse));

        List<EmergencyStockCorrectionResponse> responses = service.list(null);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getProductSkuCode()).isEqualTo("SKU-001");
            assertThat(response.getProductSkuName()).isEqualTo("验收商品");
            assertThat(response.getWarehouseCode()).isEqualTo("WH-01");
            assertThat(response.getWarehouseName()).isEqualTo("主仓");
            assertThat(response.getLocationCode()).isEqualTo("L-001");
        });
    }

    @Test
    void createRejectsANewBatchIdentityThatAlreadyExistsAtTheLocation() {
        ProductSku product = product(1L);
        Location location = location(7L, 5L);
        EmergencyStockCorrectionRequest request = request(1L, 7L, null, 5);
        request.setBatchCode("B-001");

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(product));
        when(locationRepository.findByIdForInventoryPosting(7L)).thenReturn(Optional.of(location));
        when(inventoryBatchRepository.existsByBatchCodeAndLocationId("B-001", 7L))
            .thenReturn(true);

        assertThatThrownBy(() -> service.create(request, 21L, 5L))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_BATCH_CONFLICT);
                assertThat(exception.getParam("reason")).isEqualTo("USE_EXISTING_BATCH");
            });
        verify(correctionRepository, never()).save(any());
    }

    @Test
    void threePersonFlowKeepsMakerReviewerAndApproverDistinct() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.DRAFT,
            EmergencyCorrectionApprovalMode.THREE_PERSON
        );
        when(correctionRepository.findByIdForUpdate(99L))
            .thenReturn(Optional.of(correction));

        EmergencyStockCorrectionResponse submitted = service.submit(99L, 21L, "submit");
        assertThat(submitted.getStatus()).isEqualTo(EmergencyCorrectionStatus.PENDING_REVIEW);
        assertThat(submitted.getSubmissionComment()).isEqualTo("submit");

        assertThatThrownBy(() -> service.review(99L, 21L, "self review"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_ACTOR_CONFLICT));

        EmergencyStockCorrectionResponse reviewed = service.review(99L, 22L, "reviewed");
        assertThat(reviewed.getStatus()).isEqualTo(EmergencyCorrectionStatus.PENDING_APPROVAL);
        assertThat(reviewed.getReviewedBy()).isEqualTo(22L);
        assertThat(reviewed.getSubmissionComment()).isEqualTo("submit");
        assertThat(reviewed.getReviewComment()).isEqualTo("reviewed");

        assertThatThrownBy(() -> service.approve(99L, 22L, "self approve"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_ACTOR_CONFLICT));

        EmergencyStockCorrectionResponse approved = service.approve(99L, 23L, "approved");
        assertThat(approved.getStatus()).isEqualTo(EmergencyCorrectionStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo(23L);
    }

    @Test
    void impactCarriesFrozenDeltaForwardAndReportsReservedOrders() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.DRAFT,
            EmergencyCorrectionApprovalMode.NONE
        );
        ProductSku product = product(1L);
        Location location = location(7L, 5L);
        InventoryBatch batch = batch(11L, product, location, 95, 86);
        InventoryReservation reservation = InventoryReservation.builder()
            .salesOrderId(501L)
            .salesOrderItemId(601L)
            .inventoryBatchId(11L)
            .productSkuId(1L)
            .locationId(7L)
            .reservedQty(86)
            .build();

        when(correctionRepository.findById(99L)).thenReturn(Optional.of(correction));
        when(inventoryBatchRepository.findById(11L)).thenReturn(Optional.of(batch));
        when(reservationRepository.findByInventoryBatchId(11L))
            .thenReturn(List.of(reservation));

        EmergencyStockCorrectionImpactResponse impact = service.impact(99L);

        assertThat(impact.getSnapshotSystemQty()).isEqualTo(100);
        assertThat(impact.getCurrentQty()).isEqualTo(95);
        assertThat(impact.getAdjustmentQty()).isEqualTo(-10);
        assertThat(impact.getProjectedQty()).isEqualTo(85L);
        assertThat(impact.getReservedQty()).isEqualTo(86);
        assertThat(impact.isCanApply()).isFalse();
        assertThat(impact.getAffectedSalesOrderIds()).containsExactly(501L);
        assertThat(impact.getPreviewToken()).isEqualTo("preview-1");
    }

    @Test
    void impactAllowsOnlyGovernedPositiveRecoveryOfAnInactiveEmptyBatch() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.DRAFT,
            EmergencyCorrectionApprovalMode.NONE
        );
        correction.setSystemQty(0);
        correction.setCountedQty(5);
        correction.setAdjustmentQty(5);
        ProductSku product = product(1L);
        Location location = location(7L, 5L);
        InventoryBatch batch = batch(11L, product, location, 0, 0);
        batch.setActive(false);

        when(correctionRepository.findById(99L)).thenReturn(Optional.of(correction));
        when(inventoryBatchRepository.findById(11L)).thenReturn(Optional.of(batch));
        when(locationRepository.findById(7L)).thenReturn(Optional.of(location));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThat(service.impact(99L).isCanApply()).isTrue();

        batch.setQuantity(1);
        assertThat(service.impact(99L).isCanApply()).isFalse();
    }

    @Test
    void impactBlocksPositiveCorrectionWhenLocationContainsAnotherBatch() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.DRAFT,
            EmergencyCorrectionApprovalMode.NONE
        );
        correction.setCountedQty(105);
        correction.setAdjustmentQty(5);
        ProductSku product = product(1L);
        Location location = location(7L, 5L);
        InventoryBatch batch = batch(11L, product, location, 100, 0);

        when(correctionRepository.findById(99L)).thenReturn(Optional.of(correction));
        when(inventoryBatchRepository.findById(11L)).thenReturn(Optional.of(batch));
        when(locationRepository.findById(7L)).thenReturn(Optional.of(location));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(product));
        when(locationOccupancyService.canStore(location, product, "B-001"))
            .thenReturn(false);

        assertThat(service.impact(99L).isCanApply()).isFalse();
    }

    @Test
    void impactAllowsZeroDeltaAuditForAnInactiveEmptyBatch() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.DRAFT,
            EmergencyCorrectionApprovalMode.NONE
        );
        correction.setSystemQty(0);
        correction.setCountedQty(0);
        correction.setAdjustmentQty(0);
        ProductSku product = product(1L);
        Location location = location(7L, 5L);
        InventoryBatch batch = batch(11L, product, location, 0, 0);
        batch.setActive(false);

        when(correctionRepository.findById(99L)).thenReturn(Optional.of(correction));
        when(inventoryBatchRepository.findById(11L)).thenReturn(Optional.of(batch));

        assertThat(service.impact(99L).isCanApply()).isTrue();
    }

    @Test
    void impactBlocksCreatingADuplicateBatchIdentity() {
        EmergencyStockCorrection correction = correction(
            EmergencyCorrectionStatus.DRAFT,
            EmergencyCorrectionApprovalMode.NONE
        );
        correction.setInventoryBatchId(null);
        correction.setSystemQty(0);
        correction.setCountedQty(5);
        correction.setAdjustmentQty(5);

        when(correctionRepository.findById(99L)).thenReturn(Optional.of(correction));
        when(inventoryBatchRepository.existsByBatchCodeAndLocationId("B-001", 7L))
            .thenReturn(true);

        assertThat(service.impact(99L).isCanApply()).isFalse();
    }

    @Test
    void applyPersistsTerminalSuccessAndReplaysWithoutPostingAgain() {
        IdempotencyRequest request = IdempotencyRequest.builder()
            .id(70L)
            .idempotencyKey("key-1")
            .operation(EmergencyStockCorrectionService.APPLY_OPERATION)
            .requestHash("hash")
            .status(IdempotencyStatus.PROCESSING)
            .build();
        EmergencyStockCorrection applied = correction(
            EmergencyCorrectionStatus.APPLIED,
            EmergencyCorrectionApprovalMode.NONE
        );
        applied.setIdempotencyKey("key-1");
        when(idempotencyService.claim(eq("key-1"), eq(EmergencyStockCorrectionService.APPLY_OPERATION), any()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, request));
        when(applicationExecutor.applyAtomic(99L, 21L, "maker", "key-1", "preview-1"))
            .thenReturn(applied);

        EmergencyStockCorrectionResponse response = service.apply(
            99L, 21L, "maker", " key-1 ", "preview-1"
        );

        assertThat(response.getStatus()).isEqualTo(EmergencyCorrectionStatus.APPLIED);
        verify(idempotencyService).markSucceeded(eq(request), eq(200), any());

        when(idempotencyService.claim(eq("key-1"), eq(EmergencyStockCorrectionService.APPLY_OPERATION), any()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.REPLAY_SUCCEEDED, request));
        when(correctionRepository.findById(99L)).thenReturn(Optional.of(applied));

        assertThat(service.apply(99L, 21L, "maker", "key-1", "preview-1").getStatus())
            .isEqualTo(EmergencyCorrectionStatus.APPLIED);
        verify(applicationExecutor).applyAtomic(99L, 21L, "maker", "key-1", "preview-1");
    }

    @Test
    void deterministicFailedClaimReplaysOriginalBusinessFailure() {
        IdempotencyRequest request = IdempotencyRequest.builder()
            .id(70L)
            .idempotencyKey("key-failed")
            .operation(EmergencyStockCorrectionService.APPLY_OPERATION)
            .requestHash("hash")
            .status(IdempotencyStatus.FAILED)
            .responseStatus(409)
            .responseBody("{\"errorKey\":\"INVENTORY_RESERVED_QUANTITY_CONFLICT\",\"params\":{\"reservedQuantity\":86}}")
            .build();
        when(idempotencyService.claim(eq("key-failed"), eq(EmergencyStockCorrectionService.APPLY_OPERATION), any()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.REPLAY_FAILED, request));

        assertThatThrownBy(() -> service.apply(
            99L, 21L, "maker", "key-failed", "preview-failed"
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_RESERVED_QUANTITY_CONFLICT);
                assertThat(exception.getParam("reservedQuantity")).isEqualTo(86);
            });
        verify(applicationExecutor, never()).applyAtomic(any(), any(), any(), any(), any());
    }

    private EmergencyStockCorrectionRequest request(
        Long productId,
        Long locationId,
        Long batchId,
        int countedQuantity
    ) {
        EmergencyStockCorrectionRequest request = new EmergencyStockCorrectionRequest();
        request.setProductSkuId(productId);
        request.setLocationId(locationId);
        request.setInventoryBatchId(batchId);
        request.setCountedQty(countedQuantity);
        request.setReasonCode("COUNT_VARIANCE");
        request.setReasonDetail("cycle count evidence");
        request.setExpiryDate(LocalDate.now().plusMonths(6));
        return request;
    }

    private EmergencyStockCorrection correction(
        EmergencyCorrectionStatus status,
        EmergencyCorrectionApprovalMode mode
    ) {
        return EmergencyStockCorrection.builder()
            .id(99L)
            .correctionNo("ESC202608250001")
            .productSkuId(1L)
            .warehouseId(5L)
            .locationId(7L)
            .inventoryBatchId(11L)
            .batchCode("B-001")
            .systemQty(100)
            .countedQty(90)
            .adjustmentQty(-10)
            .reasonCode("COUNT_VARIANCE")
            .reasonDetail("cycle count evidence")
            .createdBy(21L)
            .approvalModeSnapshot(mode)
            .status(status)
            .build();
    }

    private ProductSku product(Long id) {
        return ProductSku.builder().id(id).build();
    }

    private Location location(Long id, Long warehouseId) {
        Warehouse warehouse = Warehouse.builder().id(warehouseId).build();
        return Location.builder()
            .id(id)
            .locationCode("L-001")
            .warehouse(warehouse)
            .build();
    }

    private InventoryBatch batch(
        Long id,
        ProductSku product,
        Location location,
        int quantity,
        int reservedQuantity
    ) {
        return InventoryBatch.builder()
            .id(id)
            .batchCode("B-001")
            .productSku(product)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(quantity)
            .reservedQuantity(reservedQuantity)
            .active(true)
            .build();
    }
}
