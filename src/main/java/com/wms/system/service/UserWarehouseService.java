package com.wms.system.service;

import com.wms.system.dto.AssignableWarehouseDTO;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserWarehouse;
import com.wms.system.entity.Warehouse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysUserWarehouseRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserWarehouseService {

    public static final String WAREHOUSE_STAFF = "WAREHOUSE_STAFF";

    private final SysUserWarehouseRepository userWarehouseRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public List<AssignableWarehouseDTO> listAssignableWarehouses() {
        return warehouseRepository.findAllActiveByCompanyId(
                CompanyScope.currentCompanyId()).stream()
            .map(warehouse -> AssignableWarehouseDTO.builder()
                .id(warehouse.getId())
                .code(warehouse.getCode())
                .name(warehouse.getName())
                .build())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AssignableWarehouseDTO> getAssignedWarehouses(Long userId) {
        Set<Long> warehouseIds = userWarehouseRepository
            .findWarehouseIdsByCompanyIdAndUserId(
                CompanyScope.currentCompanyId(), userId);
        if (warehouseIds.isEmpty()) {
            return List.of();
        }
        return warehouseRepository.findAllByCompanyIdAndIdIn(
                CompanyScope.currentCompanyId(), warehouseIds).stream()
            .sorted((left, right) -> left.getCode().compareTo(right.getCode()))
            .map(warehouse -> AssignableWarehouseDTO.builder()
                .id(warehouse.getId())
                .code(warehouse.getCode())
                .name(warehouse.getName())
                .build())
            .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> getAssignedWarehouseIds(Long userId) {
        return userWarehouseRepository.findWarehouseIdsByCompanyIdAndUserId(
            CompanyScope.currentCompanyId(), userId);
    }

    /** Validate before role mutation so an invalid selection cannot partially change IAM state. */
    @Transactional(readOnly = true)
    public List<Warehouse> validateSelection(List<SysRole> roles, List<Long> requestedWarehouseIds) {
        boolean includesWarehouseStaff = roles.stream()
            .anyMatch(role -> WAREHOUSE_STAFF.equals(role.getRoleCode()));
        if (!includesWarehouseStaff) {
            return List.of();
        }

        Set<Long> warehouseIds = normalize(requestedWarehouseIds);
        if (warehouseIds.isEmpty()) {
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_SCOPE_REQUIRED,
                Map.of("roleCode", WAREHOUSE_STAFF)
            );
        }

        List<Warehouse> warehouses = warehouseRepository
            .findAllByCompanyIdAndIdIn(
                CompanyScope.currentCompanyId(), warehouseIds);
        if (warehouses.size() != warehouseIds.size()) {
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_NOT_FOUND,
                Map.of("warehouseIds", warehouseIds)
            );
        }
        List<Long> inactiveIds = warehouses.stream()
            .filter(warehouse -> !Boolean.TRUE.equals(warehouse.getIsActive()))
            .map(Warehouse::getId)
            .toList();
        if (!inactiveIds.isEmpty()) {
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_INACTIVE,
                Map.of("warehouseIds", inactiveIds)
            );
        }
        return warehouses;
    }

    @Transactional
    public void replaceAssignments(
        Long userId,
        List<SysRole> roles,
        List<Long> requestedWarehouseIds,
        Long assignedBy
    ) {
        Long companyId = CompanyScope.currentCompanyId();
        List<Warehouse> warehouses = validateSelection(roles, requestedWarehouseIds);
        userWarehouseRepository.deleteByCompanyIdAndUserId(companyId, userId);
        userWarehouseRepository.flush();
        if (warehouses.isEmpty()) {
            return;
        }
        List<SysUserWarehouse> assignments = warehouses.stream()
            .map(warehouse -> SysUserWarehouse.builder()
                .companyId(companyId)
                .userId(userId)
                .warehouseId(warehouse.getId())
                .assignedBy(assignedBy)
                .build())
            .toList();
        userWarehouseRepository.saveAll(assignments);
    }

    @Transactional
    public void clearAssignments(Long userId) {
        userWarehouseRepository.deleteByCompanyIdAndUserId(
            CompanyScope.currentCompanyId(), userId);
    }

    private Set<Long> normalize(List<Long> warehouseIds) {
        if (warehouseIds == null) {
            return Set.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        warehouseIds.stream()
            .filter(id -> id != null && id > 0)
            .forEach(normalized::add);
        return normalized;
    }
}
