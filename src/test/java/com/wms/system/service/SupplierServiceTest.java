package com.wms.system.service;

import com.wms.system.dto.supplier.CreateSupplierRequest;
import com.wms.system.dto.supplier.SupplierResponse;
import com.wms.system.dto.supplier.UpdateSupplierRequest;
import com.wms.system.entity.Supplier;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InboundOrderRepository;
import com.wms.system.repository.PurchaseOrderRepository;
import com.wms.system.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierService Tests")
class SupplierServiceTest {

    @Mock private SupplierRepository supplierRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private InboundOrderRepository inboundOrderRepository;

    @InjectMocks private SupplierService supplierService;

    private Supplier supplier;

    @BeforeEach
    void setUp() {
        supplier = Supplier.builder()
            .id(7L)
            .code("SUP-TEST")
            .name("Test Supplier")
            .isActive(true)
            .isDeleted(false)
            .build();
        supplier.setCompanyId(1L);
    }

    @Test
    void createNormalizesCodeAndCreatesActiveSupplier() {
        when(supplierRepository.existsByCompanyIdAndCode(1L, "SUP-NEW"))
            .thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });

        SupplierResponse response = supplierService.create(new CreateSupplierRequest(
            "sup-new", " New Supplier ", " Buyer ", null,
            "supplier@example.com", null, " Preferred "
        ));

        assertThat(response.id()).isEqualTo(8L);
        assertThat(response.code()).isEqualTo("SUP-NEW");
        assertThat(response.name()).isEqualTo("New Supplier");
        assertThat(response.isActive()).isTrue();
        assertThat(response.remark()).isEqualTo("Preferred");
    }

    @Test
    void createRejectsReservedCodeIncludingDeletedRecords() {
        when(supplierRepository.existsByCompanyIdAndCode(1L, "SUP-TEST"))
            .thenReturn(true);

        assertThatThrownBy(() -> supplierService.create(new CreateSupplierRequest(
            "SUP-TEST", "Duplicate", null, null, null, null, null
        )))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SUPPLIER_ALREADY_EXISTS);

        verify(supplierRepository, never()).save(any());
    }

    @Test
    void listUsesActiveOnlyRepositoryQuery() {
        when(supplierRepository.findAllByCompanyIdAndIsDeletedFalseAndIsActiveTrueOrderByNameAsc(1L))
            .thenReturn(List.of(supplier));

        List<SupplierResponse> result = supplierService.list(true);

        assertThat(result).extracting(SupplierResponse::id).containsExactly(7L);
    }

    @Test
    void updateKeepsImmutableCode() {
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(7L, 1L))
            .thenReturn(Optional.of(supplier));
        when(supplierRepository.save(supplier)).thenReturn(supplier);

        SupplierResponse response = supplierService.update(7L, new UpdateSupplierRequest(
            "Renamed Supplier", null, null, null, null, "Updated"
        ));

        assertThat(response.code()).isEqualTo("SUP-TEST");
        assertThat(response.name()).isEqualTo("Renamed Supplier");
    }

    @Test
    void deleteRejectsSupplierReferencedByOpenDocuments() {
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(7L, 1L))
            .thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.countBySupplierReference_IdAndStatusIn(any(), anyList()))
            .thenReturn(2L);
        when(inboundOrderRepository.countBySupplier_IdAndStatusIn(any(), anyList()))
            .thenReturn(1L);

        assertThatThrownBy(() -> supplierService.delete(7L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SUPPLIER_IN_USE);

        verify(supplierRepository, never()).save(any());
    }

    @Test
    void deleteMarksSupplierDeletedWhenOnlyHistoricalReferencesRemain() {
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(7L, 1L))
            .thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.countBySupplierReference_IdAndStatusIn(any(), anyList()))
            .thenReturn(0L);
        when(inboundOrderRepository.countBySupplier_IdAndStatusIn(any(), anyList()))
            .thenReturn(0L);
        when(supplierRepository.save(supplier)).thenReturn(supplier);

        supplierService.delete(7L);

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(captor.capture());
        assertThat(captor.getValue().getIsDeleted()).isTrue();
        assertThat(captor.getValue().getIsActive()).isFalse();
    }

    @Test
    void getDoesNotExposeDeletedSupplier() {
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(7L, 1L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.get(7L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SUPPLIER_NOT_FOUND);
    }
}
