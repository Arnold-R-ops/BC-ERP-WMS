package com.wms.system.service.inventory;

import com.wms.system.entity.SysPermission;
import com.wms.system.repository.SysPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Ensures tenants created after V4.79 receive the same assignable catalogue. */
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
                154, "Configure the tenant release-to-saleable policy when explicitly assigned")
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
