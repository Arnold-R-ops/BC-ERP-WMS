package com.wms.system.service.rma;

import com.wms.system.dto.rma.RmaChannelCaseResponse;
import com.wms.system.entity.ChannelRawEvent;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.SalesReturnChannelCase;
import com.wms.system.entity.enums.RmaChannelCaseStatus;
import com.wms.system.repository.SalesOrderRepository;
import com.wms.system.repository.SalesReturnChannelCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RmaChannelCaseService {
    private final SalesReturnChannelCaseRepository repository;
    private final SalesOrderRepository salesOrderRepository;

    /** Signed refunds become tenant review cases; this never posts stock or money. */
    @Transactional
    public SalesReturnChannelCase ingestRefund(String channel, String storeIdentifier,
                                                String externalCaseId, String externalOrderId,
                                                ChannelRawEvent rawEvent) {
        String caseId = StringUtils.hasText(externalCaseId)
            ? externalCaseId : rawEvent.getWebhookEventId();
        SalesReturnChannelCase existing = repository
            .findByChannelAndStoreIdentifierAndExternalCaseId(channel, storeIdentifier, caseId)
            .orElse(null);
        if (existing != null) {
            return existing;
        }

        SalesOrder order = StringUtils.hasText(externalOrderId)
            ? salesOrderRepository.findByExternalOrderId(externalOrderId)
                .filter(candidate -> channel.equalsIgnoreCase(candidate.getChannel()))
                .orElse(null)
            : null;
        LocalDateTime now = LocalDateTime.now();
        return repository.save(SalesReturnChannelCase.builder()
            .channel(channel)
            .storeIdentifier(storeIdentifier)
            .externalCaseId(caseId)
            .externalOrderId(externalOrderId)
            .rawEventId(rawEvent.getId())
            .salesOrder(order)
            .status(order == null ? RmaChannelCaseStatus.UNMATCHED_PENDING
                : RmaChannelCaseStatus.MATCHED_PENDING)
            .receivedAt(now)
            .matchedAt(order == null ? null : now)
            .build());
    }

    @Transactional(readOnly = true)
    public List<RmaChannelCaseResponse> list() {
        return repository.findAllByOrderByReceivedAtDescIdDesc().stream().map(this::toResponse).toList();
    }

    private RmaChannelCaseResponse toResponse(SalesReturnChannelCase item) {
        return new RmaChannelCaseResponse(item.getId(), item.getChannel(), item.getStoreIdentifier(),
            item.getExternalCaseId(), item.getExternalOrderId(),
            item.getSalesOrder() == null ? null : item.getSalesOrder().getId(),
            item.getSalesOrder() == null ? null : item.getSalesOrder().getOrderNo(),
            item.getStatus(), item.getRmaId(), item.getReceivedAt(), item.getMatchedAt());
    }
}
