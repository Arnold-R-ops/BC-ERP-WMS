package com.wms.system.service.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.*;
import com.wms.system.service.DomainOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Gives safety/quality operations priority over open transfer reservations.
 * The caller owns the transaction: a failed hold or damage operation rolls the
 * transfer evacuation back with it.
 */
@Service
@RequiredArgsConstructor
public class TransferAllocationCoordinator {
    private final TransferAllocationRepository allocationRepository;
    private final TransferOrderLineRepository lineRepository;
    private final InventoryBatchRepository batchRepository;
    private final TransferEventRepository eventRepository;
    private final DomainOutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<Long, Integer> openReservedQuantities(Collection<Long> sourceBatchIds) {
        if (sourceBatchIds == null || sourceBatchIds.isEmpty()) return Map.of();
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (TransferAllocation allocation : allocationRepository.findBySourceBatchIds(sourceBatchIds)) {
            int open = allocation.remainingQuantity();
            if (open <= 0 || allocation.getTransferLine().getTransferOrder().getBusinessStatus()
                != TransferBusinessStatus.APPROVED) continue;
            result.merge(allocation.getSourceBatch().getId(), open, Integer::sum);
        }
        return Collections.unmodifiableMap(result);
    }

    public int evacuate(Collection<Long> sourceBatchIds, String reason,
                        Long actorId, String actorName) {
        Set<Long> excluded = new LinkedHashSet<>(sourceBatchIds);
        List<TransferAllocation> allocations = allocationRepository
            .findBySourceBatchIdsForUpdate(excluded);
        Map<Long, int[]> effects = new LinkedHashMap<>();
        for (TransferAllocation allocation : allocations) {
            int open = allocation.remainingQuantity();
            TransferOrderLine line = allocation.getTransferLine();
            TransferOrder order = line.getTransferOrder();
            if (open <= 0 || order.getBusinessStatus() != TransferBusinessStatus.APPROVED) continue;
            InventoryBatch source = batchRepository.findByIdForPosting(allocation.getSourceBatch().getId())
                .orElseThrow();
            source.releaseReservedQuantity(open);
            batchRepository.saveAndFlush(source);
            allocation.setReleasedQuantity(allocation.getReleasedQuantity() + open);
            allocation.setStatus(allocation.getConsumedQuantity() > 0
                ? TransferAllocationStatus.PARTIALLY_RELEASED : TransferAllocationStatus.RELEASED);
            line.setAllocatedQuantity(line.getAllocatedQuantity() - open);
            allocationRepository.save(allocation);

            int remaining = open;
            for (InventoryBatch target : batchRepository.findTransferCandidatesForUpdate(
                order.getSourceWarehouse().getId(), line.getProductSku().getId())) {
                if (remaining == 0) break;
                if (excluded.contains(target.getId())) continue;
                int chunk = Math.min(remaining, target.getAvailableQuantity());
                if (chunk <= 0) continue;
                target.reserveQuantity(chunk);
                batchRepository.saveAndFlush(target);
                allocationRepository.save(TransferAllocation.builder()
                    .transferLine(line).sourceBatch(target).allocatedQuantity(chunk)
                    .status(TransferAllocationStatus.ACTIVE).build());
                line.setAllocatedQuantity(line.getAllocatedQuantity() + chunk);
                remaining -= chunk;
            }
            lineRepository.save(line);
            int[] effect = effects.computeIfAbsent(order.getId(), ignored -> new int[2]);
            effect[0] += open - remaining;
            effect[1] += remaining;
        }
        for (Map.Entry<Long, int[]> entry : effects.entrySet()) {
            TransferOrder order = allocations.stream().map(a -> a.getTransferLine().getTransferOrder())
                .filter(item -> item.getId().equals(entry.getKey())).findFirst().orElseThrow();
            Map<String, Object> payload = Map.of("reason", reason,
                "reallocatedQuantity", entry.getValue()[0], "releasedQuantity", entry.getValue()[1]);
            eventRepository.save(TransferEvent.builder().transferOrder(order)
                .eventType("TRANSFER_ALLOCATION_EVACUATED_FOR_SAFETY")
                .payloadJson(json(payload)).actorId(actorId)
                .actorName(actorName == null || actorName.isBlank() ? "SYSTEM" : actorName)
                .occurredAt(LocalDateTime.now()).build());
            outboxService.append("TRANSFER_ALLOCATION_CHANGED", "TransferOrder", order.getId(), payload);
        }
        return effects.values().stream().mapToInt(value -> value[0] + value[1]).sum();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
