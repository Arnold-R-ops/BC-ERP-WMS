package com.wms.system.service;

import com.wms.system.dto.supplier.CreateSupplierRequest;
import com.wms.system.dto.supplier.SupplierResponse;
import com.wms.system.dto.supplier.UpdateSupplierRequest;
import com.wms.system.entity.Supplier;
import com.wms.system.entity.enums.InboundOrderStatus;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InboundOrderRepository;
import com.wms.system.repository.PurchaseOrderRepository;
import com.wms.system.repository.SupplierRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private static final List<PurchaseOrderStatus> OPEN_PURCHASE_STATUSES = List.of(
        PurchaseOrderStatus.ORDERING,
        PurchaseOrderStatus.IN_TRANSIT,
        PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    private static final List<InboundOrderStatus> OPEN_INBOUND_STATUSES = List.of(
        InboundOrderStatus.PENDING_APPROVAL,
        InboundOrderStatus.APPROVED_PLAN,
        InboundOrderStatus.AWAITING_RECEIVAL
    );

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InboundOrderRepository inboundOrderRepository;

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(boolean activeOnly) {
        List<Supplier> suppliers = activeOnly
            ? supplierRepository.findAllByCompanyIdAndIsDeletedFalseAndIsActiveTrueOrderByNameAsc(companyId())
            : supplierRepository.findAllByCompanyIdAndIsDeletedFalseOrderByNameAsc(companyId());
        return suppliers.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return toResponse(requireSupplier(id));
    }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        String code = normalizeCode(request.code());
        if (supplierRepository.existsByCompanyIdAndCode(companyId(), code)) {
            throw new BusinessException(
                ErrorKeys.SUPPLIER_ALREADY_EXISTS,
                Map.of("code", code)
            );
        }

        Supplier supplier = Supplier.builder()
            .code(code)
            .name(request.name().trim())
            .contact(trimToNull(request.contact()))
            .address(trimToNull(request.address()))
            .email(trimToNull(request.email()))
            .phone(trimToNull(request.phone()))
            .remark(trimToNull(request.remark()))
            .isActive(true)
            .isDeleted(false)
            .build();
        supplier.setCompanyId(companyId());

        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long id, UpdateSupplierRequest request) {
        Supplier supplier = requireSupplier(id);
        supplier.setName(request.name().trim());
        supplier.setContact(trimToNull(request.contact()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setEmail(trimToNull(request.email()));
        supplier.setPhone(trimToNull(request.phone()));
        supplier.setRemark(trimToNull(request.remark()));
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse activate(Long id) {
        Supplier supplier = requireSupplier(id);
        supplier.setIsActive(true);
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse deactivate(Long id) {
        Supplier supplier = requireSupplier(id);
        supplier.setIsActive(false);
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public void delete(Long id) {
        Supplier supplier = requireSupplier(id);
        long openPurchaseOrders = purchaseOrderRepository.countBySupplierReference_IdAndStatusIn(
            id,
            OPEN_PURCHASE_STATUSES
        );
        long openInboundOrders = inboundOrderRepository.countBySupplier_IdAndStatusIn(
            id,
            OPEN_INBOUND_STATUSES
        );

        if (openPurchaseOrders > 0 || openInboundOrders > 0) {
            throw new BusinessException(
                ErrorKeys.SUPPLIER_IN_USE,
                Map.of(
                    "supplierId", id,
                    "openPurchaseOrders", openPurchaseOrders,
                    "openInboundOrders", openInboundOrders,
                    "allowedAction", "DEACTIVATE"
                )
            );
        }

        supplier.setIsActive(false);
        supplier.setIsDeleted(true);
        supplierRepository.save(supplier);
    }

    @Transactional(readOnly = true)
    public Supplier requireActiveSupplier(Long id) {
        Supplier supplier = requireSupplier(id);
        if (!Boolean.TRUE.equals(supplier.getIsActive())) {
            throw new BusinessException(
                ErrorKeys.SUPPLIER_NOT_ACTIVE,
                Map.of("supplierId", id, "supplierCode", supplier.getCode())
            );
        }
        return supplier;
    }

    private Supplier requireSupplier(Long id) {
        return supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(id, companyId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SUPPLIER_NOT_FOUND,
                Map.of("supplierId", id)
            ));
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
            supplier.getId(),
            supplier.getCompanyId(),
            supplier.getCode(),
            supplier.getName(),
            supplier.getContact(),
            supplier.getAddress(),
            supplier.getEmail(),
            supplier.getPhone(),
            supplier.getRemark(),
            supplier.getIsActive(),
            supplier.getCreatedAt(),
            supplier.getUpdatedAt()
        );
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long companyId() {
        return CompanyScope.currentCompanyId();
    }
}
