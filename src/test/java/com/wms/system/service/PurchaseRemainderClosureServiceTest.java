package com.wms.system.service;

import com.wms.system.entity.PurchaseAsn;
import com.wms.system.entity.PurchaseAsnItem;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseRemainderClosureRequest;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.PurchaseAsnStatus;
import com.wms.system.entity.enums.PurchaseRemainderClosureStatus;
import com.wms.system.entity.enums.ShortReceiptClosureMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.PurchaseAsnRepository;
import com.wms.system.repository.PurchaseRemainderClosureRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRemainderClosureServiceTest {

    @Mock private PurchaseRemainderClosureRequestRepository requestRepository;
    @Mock private PurchaseAsnRepository purchaseAsnRepository;
    @Mock private PurchaseGovernancePolicyService governancePolicyService;
    @Mock private PurchaseAsnService purchaseAsnService;

    private PurchaseRemainderClosureService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseRemainderClosureService(
            requestRepository, purchaseAsnRepository,
            governancePolicyService, purchaseAsnService);
    }

    @Test
    void approvalModeCreatesPendingRequestInsteadOfClosingInventoryWorkflow() {
        PurchaseAsn asn = partiallyReceivedAsn();
        when(governancePolicyService.effectiveShortReceiptClosureMode())
            .thenReturn(ShortReceiptClosureMode.APPROVAL_REQUIRED);
        when(purchaseAsnRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(asn));
        when(requestRepository.findFirstByPurchaseAsnIdAndStatus(
            3L, PurchaseRemainderClosureStatus.PENDING)).thenReturn(Optional.empty());
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.request(3L, "Supplier cannot deliver remainder", 7L, "buyer");

        assertThat(response.status()).isEqualTo(PurchaseRemainderClosureStatus.PENDING);
        assertThat(response.reason()).isEqualTo("Supplier cannot deliver remainder");
        verify(purchaseAsnService, never()).closeRemainderAfterApproval(
            any(), any(), any(), any());
    }

    @Test
    void requesterCannotApproveTheirOwnClosureRequest() {
        PurchaseRemainderClosureRequest request = pendingRequest(partiallyReceivedAsn());
        when(requestRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(20L, "approve", 7L, "buyer"))
            .isInstanceOf(BusinessException.class);
        verify(purchaseAsnService, never()).closeRemainderAfterApproval(
            any(), any(), any(), any());
    }

    @Test
    void differentApproverClosesThroughTheGuardedApprovalEntryPoint() {
        PurchaseAsn asn = partiallyReceivedAsn();
        PurchaseRemainderClosureRequest request = pendingRequest(asn);
        when(requestRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approve(20L, "Evidence checked", 9L, "manager");

        assertThat(response.status()).isEqualTo(PurchaseRemainderClosureStatus.APPROVED);
        assertThat(response.reviewedBy()).isEqualTo(9L);
        verify(purchaseAsnService).closeRemainderAfterApproval(
            3L, "Supplier cannot deliver remainder", 9L, "manager");
    }

    private PurchaseAsn partiallyReceivedAsn() {
        PurchaseAsnItem item = PurchaseAsnItem.builder()
            .id(4L).shippedQuantity(10).receivedQuantity(6).closedQuantity(0).build();
        return PurchaseAsn.builder()
            .id(3L)
            .asnNumber("ASN-3")
            .purchaseOrder(PurchaseOrder.builder().id(2L).poNumber("PO-2").build())
            .warehouse(Warehouse.builder().id(8L).build())
            .status(PurchaseAsnStatus.PARTIALLY_RECEIVED)
            .items(List.of(item))
            .build();
    }

    private PurchaseRemainderClosureRequest pendingRequest(PurchaseAsn asn) {
        return PurchaseRemainderClosureRequest.builder()
            .id(20L)
            .version(0L)
            .purchaseAsn(asn)
            .status(PurchaseRemainderClosureStatus.PENDING)
            .reason("Supplier cannot deliver remainder")
            .requestedBy(7L)
            .requestedByName("buyer")
            .requestedAt(LocalDateTime.now())
            .build();
    }
}
