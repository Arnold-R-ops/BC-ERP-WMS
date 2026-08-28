package com.wms.system.service;

import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.ReceivingTaskResponse;
import com.wms.system.dto.receiving.ReceivingTaskLocationResponse;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.ReceiptEventRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Collection;

@Service
@RequiredArgsConstructor
public class ReceivingTaskService {

    private final ReceivingTaskRepository receivingTaskRepository;
    private final ReceiptEventRepository receiptEventRepository;
    private final LocationRepository locationRepository;
    private final ReceivingResponseMapper mapper;

    @Transactional(readOnly = true)
    public List<ReceivingTaskResponse> list(ReceivingTaskStatus status) {
        List<ReceivingTask> tasks = status == null
            ? receivingTaskRepository.findAllByOrderByIdDesc()
            : receivingTaskRepository.findByStatusInOrderByIdDesc(List.of(status));
        return tasks.stream().map(mapper::toTask).toList();
    }

    @Transactional(readOnly = true)
    public List<ReceivingTaskResponse> list(
        ReceivingTaskStatus status,
        Collection<Long> warehouseIds
    ) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return List.of();
        }
        List<ReceivingTask> tasks = status == null
            ? receivingTaskRepository.findByWarehouseIdInOrderByIdDesc(warehouseIds)
            : receivingTaskRepository.findByStatusAndWarehouseIdInOrderByIdDesc(
                status, warehouseIds);
        return tasks.stream().map(mapper::toTask).toList();
    }

    @Transactional(readOnly = true)
    public ReceivingTaskResponse get(Long id) {
        return mapper.toTask(requireTask(id));
    }

    @Transactional(readOnly = true)
    public Long getWarehouseId(Long id) {
        return requireTask(id).getWarehouse().getId();
    }

    @Transactional(readOnly = true)
    public List<ReceiptEventResponse> listReceipts(Long taskId) {
        requireTask(taskId);
        return receiptEventRepository.findByReceivingTaskIdOrderByIdAsc(taskId)
            .stream().map(mapper::toReceipt).toList();
    }

    @Transactional(readOnly = true)
    public List<ReceivingTaskLocationResponse> listEnabledLocations(Long taskId) {
        Long warehouseId = requireTask(taskId).getWarehouse().getId();
        return locationRepository
            .findByWarehouseIdAndEnabledTrueOrderByLocationCodeAsc(warehouseId)
            .stream()
            .map(location -> new ReceivingTaskLocationResponse(
                location.getId(), location.getLocationCode(), location.getLocationCode()))
            .toList();
    }

    private ReceivingTask requireTask(Long id) {
        return receivingTaskRepository.findById(id).orElseThrow(() ->
            new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "ReceivingTask", "resourceId", id)
            ));
    }
}
