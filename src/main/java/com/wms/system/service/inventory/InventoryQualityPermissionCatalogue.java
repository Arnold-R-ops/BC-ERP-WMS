package com.wms.system.service.inventory;

import com.wms.system.entity.SysPermission;
import com.wms.system.repository.SysPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Ensures newly created tenants receive the current assignable quality catalogue. */
@Service
@RequiredArgsConstructor
public class InventoryQualityPermissionCatalogue {
    private final SysPermissionRepository permissionRepository;

    @Transactional(rollbackFor = Exception.class)
    public void provisionForTenant(Long tenantId) {
        for (PermissionSeed seed : seeds()) {
            if (permissionRepository.existsByCompanyIdAndPermissionCode(tenantId, seed.code())) {
                continue;
            }
            permissionRepository.save(SysPermission.builder()
                .permissionCode(seed.code())
                .permissionName(seed.name())
                .permissionType("API")
                .resourcePath(seed.resourcePath())
                .httpMethod(seed.httpMethod())
                .dataScope("ALL")
                .riskLevel(seed.riskLevel())
                .customAssignable(true)
                .description(seed.description())
                .status("ACTIVE")
                .sortOrder(seed.sortOrder())
                .build());
        }
    }

    private List<PermissionSeed> seeds() {
        return List.of(
            new PermissionSeed("inventory_quality:view", "查看质量处理记录",
                "/api/inventory-quality/**", "GET", SysPermission.RISK_LEVEL_NORMAL,
                150, "View warehouse-scoped quality actions and safe batch data"),
            new PermissionSeed("inventory_quality:hold", "冻结库存",
                "/api/inventory-quality/hold-preview;/api/inventory-quality/holds", "POST",
                SysPermission.RISK_LEVEL_HIGH, 151,
                "Immediately quarantine or recall-hold authorised stock"),
            new PermissionSeed("inventory_quality:release", "申请或执行恢复可售",
                "/api/inventory-quality/releases;/api/inventory-quality/actions/*/withdraw", "POST",
                SysPermission.RISK_LEVEL_HIGH, 152,
                "Release held stock directly or request approval according to tenant policy"),
            new PermissionSeed("inventory_quality:review", "审批恢复可售",
                "/api/inventory-quality/actions/*/approve;/api/inventory-quality/actions/*/reject", "POST",
                SysPermission.RISK_LEVEL_CRITICAL, 153,
                "Approve or reject another user's release request"),
            new PermissionSeed("inventory_quality:policy", "配置恢复可售策略",
                "/api/inventory-quality/policy", "*", SysPermission.RISK_LEVEL_CRITICAL,
                154, "Configure the tenant release-to-saleable policy when explicitly assigned"),
            new PermissionSeed("receiving_inspection:view", "查看收货质检任务",
                "/api/receiving-inspections/**", "GET", SysPermission.RISK_LEVEL_NORMAL,
                155, "View warehouse-scoped receipt inspection tasks and safe receipt data"),
            new PermissionSeed("receiving_inspection:execute", "执行收货质检",
                "/api/receiving-inspections/*/results", "POST", SysPermission.RISK_LEVEL_HIGH,
                156, "Submit full or sampling inspection decisions"),
            new PermissionSeed("receiving_inspection:review", "复核质检放行",
                "/api/receiving-inspections/results/*/approve;/api/receiving-inspections/results/*/reject",
                "POST", SysPermission.RISK_LEVEL_CRITICAL, 157,
                "Approve or reject another user release-to-saleable decision"),
            new PermissionSeed("receiving_inspection:correct", "发起质检更正复检",
                "/api/receiving-inspections/reinspections", "POST",
                SysPermission.RISK_LEVEL_CRITICAL, 158,
                "Create an immutable correction and reinspection chain"),
            new PermissionSeed("receiving_inspection:policy", "配置收货质检策略",
                "/api/receiving-inspections/policy", "*", SysPermission.RISK_LEVEL_CRITICAL,
                159, "Configure optional tenant inspection and release review policy")
        );
    }

    private record PermissionSeed(
        String code,
        String name,
        String resourcePath,
        String httpMethod,
        String riskLevel,
        int sortOrder,
        String description
    ) { }
}
