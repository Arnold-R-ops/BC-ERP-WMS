package com.wms.system.service;

import com.wms.system.entity.EmergencyStockCorrection;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.EmergencyStockCorrectionRepository;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.service.inventory.InventoryPostingCommand;
import com.wms.system.service.inventory.InventoryPostingResult;
import com.wms.system.service.inventory.InventoryPostingService;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyStockCorrectionApplicationExecutorTest {

    @Mock private EmergencyStockCorrectionRepository correctionRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private InventoryCorrectionPolicyService policyService;
    @Mock private InventoryCorrectionPreviewTokenService previewTokenService;
    @Mock private InventoryPostingService inventoryPostingService;
    @Mock private LocationOccupancyService locationOccupancyService;
    @Mock private BackorderService backorderService;

    private EmergencyStockCorrectionApplicationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EmergencyStockCorrectionApplicationExecutor(
            correctionRepository,
            inventoryBatchRepository,
            productSkuRepository,
            locationRepository,
            reservationRepository,
            policyService,
            previewTokenService,
            inventoryPostingService,
            locationOccupancyService,
            backorderService
        );
        TenantContextHolder.set(new TenantContext(
            42L,
            "company-42",
            "company-42.example.test",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void frozenDeltaIsPostedAgainstCurrentQuantityInsteadOfOverwritingCountedQuantity() {
        Fixture fixture = fixture(-10, 95, 20, EmergencyCorrectionApprovalMode.NONE);
        when(inventoryPostingService.post(any())).thenReturn(new InventoryPostingResult(
            11L, "B-001", 1L, 7L, 5L, -10, 95, 85,
            true, 801L, "ESC-99-ESC202608250001", "key-1"
        ));

        EmergencyStockCorrection result = executor.applyAtomic(
            99L,
            21L,
            "maker",
            "key-1",
            "preview-token"
        );

        ArgumentCaptor<InventoryPostingCommand> command =
            ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService).post(command.capture());
        verify(previewTokenService).verify(
            "preview-token", 99L, 0, 11L, 0, 95, 20
        );
        assertThat(command.getValue().delta()).isEqualTo(-10);
        assertThat(result.getCountedQty()).isEqualTo(90);
        assertThat(result.getAppliedQuantityBefore()).isEqualTo(95);
        assertThat(result.getAppliedQuantityAfter()).isEqualTo(85);
        assertThat(result.getStatus()).isEqualTo(EmergencyCorrectionStatus.APPLIED);
        assertThat(result.getPostingId()).isEqualTo("ESC-99-ESC202608250001");
        verify(locationOccupancyService, never()).validateCanStore(any(), any(), any());
        verify(backorderService, never()).wakeProduct(any());
        assertThat(fixture.batch().getQuantity()).isEqualTo(95);
    }

    @Test
    void positiveDeltaValidatesLocationCanStoreBeforePosting() {
        Fixture fixture = fixture(5, 95, 20, EmergencyCorrectionApprovalMode.NONE);
        when(inventoryPostingService.post(any())).thenReturn(new InventoryPostingResult(
            11L, "B-001", 1L, 7L, 5L, 5, 95, 100,
            true, 802L, "ESC-99-ESC202608250001", "key-positive"
        ));

        executor.applyAtomic(99L, 21L, "maker", "key-positive", "preview-token");

        verify(locationOccupancyService).validateCanStore(
            fixture.batch().getLocation(),
            fixture.batch().getProductSku(),
            fixture.batch().getBatchCode()
        );
        verify(inventoryPostingService).post(any(InventoryPostingCommand.class));
        verify(backorderService).wakeProduct(1L);
    }

    @Test
    void applyRechecksNewBatchIdentityAfterTheLocationLock() {
        Fixture fixture = fixture(5, 0, 0, EmergencyCorrectionApprovalMode.NONE);
        fixture.correction().setInventoryBatchId(null);
        when(inventoryBatchRepository.existsByBatchCodeAndLocationId("B-001", 7L))
            .thenReturn(true);

        assertThatThrownBy(() -> executor.applyAtomic(
            99L, 21L, "maker", "key-new-batch", "preview-token"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorKey())
                .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_BATCH_CONFLICT));

        verifyNoInteractions(inventoryPostingService);
        verify(inventoryBatchRepository, never()).saveAndFlush(any());
    }

    @Test
    void zeroDeltaAlsoRechecksNewBatchIdentityAfterTheLocationLock() {
        Fixture fixture = fixture(0, 0, 0, EmergencyCorrectionApprovalMode.NONE);
        fixture.correction().setInventoryBatchId(null);
        when(inventoryBatchRepository.existsByBatchCodeAndLocationId("B-001", 7L))
            .thenReturn(true);

        assertThatThrownBy(() -> executor.applyAtomic(
            99L, 21L, "maker", "key-zero-new-batch", "preview-token"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorKey())
                .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_BATCH_CONFLICT));

        verifyNoInteractions(inventoryPostingService);
        verify(inventoryBatchRepository, never()).saveAndFlush(any());
        verify(correctionRepository, never()).saveAndFlush(any());
    }

    @Test
    void projectedQuantityBelowReservedIsBlockedWithAffectedOrders() {
        fixture(-10, 95, 86, EmergencyCorrectionApprovalMode.NONE);
        InventoryReservation reservation = InventoryReservation.builder()
            .salesOrderId(501L)
            .salesOrderItemId(601L)
            .inventoryBatchId(11L)
            .productSkuId(1L)
            .locationId(7L)
            .reservedQty(86)
            .build();
        when(reservationRepository.findByInventoryBatchId(11L))
            .thenReturn(List.of(reservation));

        assertThatThrownBy(() -> executor.applyAtomic(
            99L,
            21L,
            "maker",
            "key-1",
            "preview-token"
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_RESERVED_QUANTITY_CONFLICT);
                assertThat(exception.getParam("projectedQuantity")).isEqualTo(85L);
                assertThat(exception.getParam("affectedSalesOrderIds"))
                    .isEqualTo(List.of(501L));
            });
        verifyNoInteractions(inventoryPostingService);
    }

    @Test
    void zeroDeltaAppliesAuditOutcomeWithoutLedgerOrOutboxPosting() {
        fixture(0, 95, 20, EmergencyCorrectionApprovalMode.NONE);

        EmergencyStockCorrection result = executor.applyAtomic(
            99L,
            21L,
            "maker",
            "key-zero",
            "preview-token"
        );

        assertThat(result.getStatus()).isEqualTo(EmergencyCorrectionStatus.APPLIED);
        assertThat(result.getAppliedQuantityBefore()).isEqualTo(95);
        assertThat(result.getAppliedQuantityAfter()).isEqualTo(95);
        assertThat(result.getPostingId()).isEqualTo("ESC-99-ESC202608250001");
        verifyNoInteractions(inventoryPostingService);
        verifyNoInteractions(locationOccupancyService);
        verifyNoInteractions(backorderService);
    }

    @Test
    void freshlyIssuedPreviewIsVerifiedBeforeDraftAuditMutatesTheCorrection() {
        Fixture fixture = fixture(0, 95, 20, null);
        when(policyService.effectiveMode()).thenReturn(EmergencyCorrectionApprovalMode.NONE);
        doAnswer(invocation -> {
            assertThat(fixture.correction().getStatus())
                .isEqualTo(EmergencyCorrectionStatus.DRAFT);
            assertThat(fixture.correction().getApprovalModeSnapshot()).isNull();
            assertThat(fixture.correction().getSubmittedBy()).isNull();
            assertThat(fixture.correction().getSubmittedAt()).isNull();
            return null;
        }).when(previewTokenService).verify(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            anyInt()
        );

        EmergencyStockCorrection result = executor.applyAtomic(
            99L,
            21L,
            "maker",
            "key-preview-order",
            "fresh-preview-token"
        );

        assertThat(result.getSubmittedBy()).isEqualTo(21L);
        assertThat(result.getSubmittedAt()).isNotNull();
        assertThat(result.getApprovalModeSnapshot())
            .isEqualTo(EmergencyCorrectionApprovalMode.NONE);
        assertThat(result.getStatus()).isEqualTo(EmergencyCorrectionStatus.APPLIED);
        verify(policyService).effectiveMode();
    }

    @Test
    void policyDowngradeDoesNotBypassCapturedInFlightApprovalMode() {
        fixture(-10, 95, 20, EmergencyCorrectionApprovalMode.THREE_PERSON);

        assertThatThrownBy(() -> executor.applyAtomic(
            99L,
            21L,
            "maker",
            "key-1",
            "preview-token"
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_MODE_CONFLICT));
        verifyNoInteractions(policyService);
        verifyNoInteractions(inventoryPostingService);
    }

    @Test
    void anotherOperatorCannotApplyTheMakersDraftInNoneMode() {
        fixture(-10, 95, 20, EmergencyCorrectionApprovalMode.NONE);

        assertThatThrownBy(() -> executor.applyAtomic(
            99L,
            22L,
            "another-operator",
            "key-1",
            "preview-token"
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_ACTOR_CONFLICT);
                assertThat(exception.getParam("operation")).isEqualTo("APPLY");
                assertThat(exception.getParam("reason")).isEqualTo("ONLY_MAKER_ALLOWED");
            });

        verifyNoInteractions(
            productSkuRepository,
            locationRepository,
            previewTokenService,
            inventoryPostingService
        );
    }

    private Fixture fixture(
        int delta,
        int currentQuantity,
        int reservedQuantity,
        EmergencyCorrectionApprovalMode mode
    ) {
        ProductSku product = ProductSku.builder().id(1L).build();
        product.setCompanyId(42L);
        Warehouse warehouse = Warehouse.builder().id(5L).build();
        warehouse.setCompanyId(42L);
        Location location = Location.builder()
            .id(7L)
            .locationCode("L-001")
            .warehouse(warehouse)
            .build();
        location.setCompanyId(42L);
        InventoryBatch batch = InventoryBatch.builder()
            .id(11L)
            .batchCode("B-001")
            .productSku(product)
            .location(location)
            .locationCode("L-001")
            .quantity(currentQuantity)
            .reservedQuantity(reservedQuantity)
            .active(true)
            .build();
        batch.setCompanyId(42L);
        EmergencyStockCorrection correction = EmergencyStockCorrection.builder()
            .id(99L)
            .correctionNo("ESC202608250001")
            .productSkuId(1L)
            .warehouseId(5L)
            .locationId(7L)
            .inventoryBatchId(11L)
            .batchCode("B-001")
            .systemQty(100)
            .countedQty(100 + delta)
            .adjustmentQty(delta)
            .reasonCode("COUNT_VARIANCE")
            .reasonDetail("cycle count evidence")
            .createdBy(21L)
            .approvalModeSnapshot(mode)
            .status(EmergencyCorrectionStatus.DRAFT)
            .build();
        correction.setCompanyId(42L);

        lenient().when(correctionRepository.findByIdForUpdate(99L))
            .thenReturn(Optional.of(correction));
        lenient().when(productSkuRepository.findById(1L)).thenReturn(Optional.of(product));
        lenient().when(locationRepository.findByIdForInventoryPosting(7L))
            .thenReturn(Optional.of(location));
        lenient().when(inventoryBatchRepository.findByIdForPosting(11L))
            .thenReturn(Optional.of(batch));
        lenient().doNothing().when(previewTokenService).verify(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            anyInt()
        );
        lenient().when(correctionRepository.saveAndFlush(any(EmergencyStockCorrection.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return new Fixture(correction, batch);
    }

    private record Fixture(
        EmergencyStockCorrection correction,
        InventoryBatch batch
    ) { }
}
