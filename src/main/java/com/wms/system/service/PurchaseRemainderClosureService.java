package com.wms.system.service;

import com.wms.system.dto.purchase.PurchaseRemainderClosureResponse;
import com.wms.system.entity.PurchaseAsn;
import com.wms.system.entity.PurchaseRemainderClosureRequest;
import com.wms.system.entity.enums.PurchaseAsnStatus;
import com.wms.system.entity.enums.PurchaseRemainderClosureStatus;
import com.wms.system.entity.enums.ShortReceiptClosureMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.PurchaseAsnRepository;
import com.wms.system.repository.PurchaseRemainderClosureRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PurchaseRemainderClosureService {

    private final PurchaseRemainderClosureRequestRepository requestRepository;
    private final PurchaseAsnRepository purchaseAsnRepository;
    private final PurchaseGovernancePolicyService governancePolicyService;
    private final PurchaseAsnService purchaseAsnService;

    @Transactional(readOnly = true)
    public List<PurchaseRemainderClosureResponse> list(PurchaseRemainderClosureStatus status) {
        List<PurchaseRemainderClosureRequest> requests = status == null
            ? requestRepository.findAllByOrderByIdDesc()
            : requestRepository.findByStatusOrderByIdDesc(status);
        return requests.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseRemainderClosureResponse get(Long id) {
        return toResponse(requireRequest(id));
    }

    @Transactional(readOnly = true)
    public Long getWarehouseId(Long id) {
        return requireRequest(id).getPurchaseAsn().getWarehouse().getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseRemainderClosureResponse request(
        Long purchaseAsnId,
        String reason,
        Long actorId,
        String actorName
    ) {
        if (governancePolicyService.effectiveShortReceiptClosureMode()
            != ShortReceiptClosureMode.APPROVAL_REQUIRED) {
            throw operationNotAllowed("REQUEST_SHORT_RECEIPT_CLOSURE", "DIRECT_MODE_ACTIVE");
        }
        PurchaseAsn asn = purchaseAsnRepository.findByIdForUpdate(purchaseAsnId)
            .orElseThrow(() -> resourceNotFound("PurchaseAsn", purchaseAsnId));
        requireClosable(asn);
        requestRepository.findFirstByPurchaseAsnIdAndStatus(
            purchaseAsnId, PurchaseRemainderClosureStatus.PENDING).ifPresent(existing -> {
                throw operationNotAllowed(
                    "REQUEST_SHORT_RECEIPT_CLOSURE", "PENDING_REQUEST_EXISTS");
            });

        PurchaseRemainderClosureRequest request = PurchaseRemainderClosureRequest.builder()
            .purchaseAsn(asn)
            .status(PurchaseRemainderClosureStatus.PENDING)
            .reason(normalizeRequired(reason))
            .requestedBy(actorId)
            .requestedByName(normalizeActor(actorName))
            .requestedAt(LocalDateTime.now())
            .build();
        return toResponse(requestRepository.save(request));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseRemainderClosureResponse approve(
        Long id,
        String comment,
        Long actorId,
        String actorName
    ) {
        PurchaseRemainderClosureRequest request = requirePendingForUpdate(id);
        if (request.getRequestedBy().equals(actorId)) {
            throw operationNotAllowed("APPROVE_SHORT_RECEIPT_CLOSURE", "APPROVER_IS_REQUESTER");
        }

        purchaseAsnService.closeRemainderAfterApproval(
            request.getPurchaseAsn().getId(), request.getReason(), actorId, actorName);
        completeReview(
            request, PurchaseRemainderClosureStatus.APPROVED,
            normalizeRequired(comment), actorId, actorName);
        return toResponse(requestRepository.save(request));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseRemainderClosureResponse reject(
        Long id,
        String comment,
        Long actorId,
        String actorName
    ) {
        PurchaseRemainderClosureRequest request = requirePendingForUpdate(id);
        if (request.getRequestedBy().equals(actorId)) {
            throw operationNotAllowed("REJECT_SHORT_RECEIPT_CLOSURE", "REVIEWER_IS_REQUESTER");
        }
        completeReview(
            request, PurchaseRemainderClosureStatus.REJECTED,
            normalizeRequired(comment), actorId, actorName);
        return toResponse(requestRepository.save(request));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseRemainderClosureResponse cancel(
        Long id,
        String comment,
        Long actorId,
        String actorName
    ) {
        PurchaseRemainderClosureRequest request = requirePendingForUpdate(id);
        if (!request.getRequestedBy().equals(actorId)) {
            throw operationNotAllowed("CANCEL_SHORT_RECEIPT_CLOSURE", "ONLY_REQUESTER_CAN_CANCEL");
        }
        completeReview(
            request, PurchaseRemainderClosureStatus.CANCELLED,
            normalizeRequired(comment), actorId, actorName);
        return toResponse(requestRepository.save(request));
    }

    private void requireClosable(PurchaseAsn asn) {
        if (asn.getStatus() != PurchaseAsnStatus.PARTIALLY_RECEIVED
            || !asn.hasReceipts()
            || asn.getItems().stream().noneMatch(item -> item.remainingQuantity() > 0)) {
            throw operationNotAllowed(
                "REQUEST_SHORT_RECEIPT_CLOSURE", "ASN_NOT_PARTIALLY_RECEIVED");
        }
    }

    private PurchaseRemainderClosureRequest requirePendingForUpdate(Long id) {
        PurchaseRemainderClosureRequest request = requestRepository.findByIdForUpdate(id)
            .orElseThrow(() -> resourceNotFound("PurchaseRemainderClosureRequest", id));
        if (request.getStatus() != PurchaseRemainderClosureStatus.PENDING) {
            throw operationNotAllowed(
                "REVIEW_SHORT_RECEIPT_CLOSURE", "REQUEST_NOT_PENDING");
        }
        return request;
    }

    private PurchaseRemainderClosureRequest requireRequest(Long id) {
        return requestRepository.findById(id)
            .orElseThrow(() -> resourceNotFound("PurchaseRemainderClosureRequest", id));
    }

    private void completeReview(
        PurchaseRemainderClosureRequest request,
        PurchaseRemainderClosureStatus status,
        String comment,
        Long actorId,
        String actorName
    ) {
        request.setStatus(status);
        request.setReviewedBy(actorId);
        request.setReviewedByName(normalizeActor(actorName));
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewComment(comment);
    }

    private PurchaseRemainderClosureResponse toResponse(
        PurchaseRemainderClosureRequest request
    ) {
        PurchaseAsn asn = request.getPurchaseAsn();
        return new PurchaseRemainderClosureResponse(
            request.getId(),
            request.getVersion(),
            asn.getId(),
            asn.getAsnNumber(),
            asn.getPurchaseOrder().getId(),
            asn.getPurchaseOrder().getPoNumber(),
            asn.getWarehouse().getId(),
            request.getStatus(),
            request.getReason(),
            request.getRequestedBy(),
            request.getRequestedByName(),
            request.getRequestedAt(),
            request.getReviewedBy(),
            request.getReviewedByName(),
            request.getReviewedAt(),
            request.getReviewComment()
        );
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "reason", "reason", "MUST_NOT_BE_BLANK")
            );
        }
        return value.trim();
    }

    private String normalizeActor(String actorName) {
        return actorName == null || actorName.isBlank() ? "unknown" : actorName.trim();
    }

    private BusinessException operationNotAllowed(String operation, String reason) {
        return new BusinessException(
            ErrorKeys.OPERATION_NOT_ALLOWED,
            Map.of("operation", operation, "reason", reason)
        );
    }

    private BusinessException resourceNotFound(String resourceType, Long id) {
        return new BusinessException(
            ErrorKeys.RESOURCE_NOT_FOUND,
            Map.of("resourceType", resourceType, "resourceId", id)
        );
    }
}
