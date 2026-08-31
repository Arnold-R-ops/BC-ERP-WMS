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
                159, "Configure optional tenant inspection and release review policy"),
            new PermissionSeed("inventory_disposition:view", "查看库存处置",
                "/api/inventory-dispositions/**", "GET", SysPermission.RISK_LEVEL_NORMAL,
                160, "View warehouse-scoped damage, expiry and disposal records"),
            new PermissionSeed("inventory_disposition:report", "登记破损库存",
                "/api/inventory-dispositions/damage-preview;/api/inventory-dispositions/damage-reports",
                "POST", SysPermission.RISK_LEVEL_HIGH, 161,
                "Immediately make damaged stock unavailable with governed sales impact"),
            new PermissionSeed("inventory_disposition:create", "创建库存处置单",
                "/api/inventory-dispositions/scrap-orders;/api/inventory-dispositions/orders/*/submit;/api/inventory-dispositions/orders/*/cancel",
                "POST", SysPermission.RISK_LEVEL_HIGH, 162,
                "Create, submit or cancel scrap and destruction documents"),
            new PermissionSeed("inventory_disposition:execute", "执行库存处置",
                "/api/inventory-dispositions/orders/*/executions", "POST",
                SysPermission.RISK_LEVEL_CRITICAL, 163,
                "Confirm physical destruction and atomically post inventory loss"),
            new PermissionSeed("inventory_disposition:review", "审批库存处置",
                "/api/inventory-dispositions/orders/*/approve;/api/inventory-dispositions/orders/*/reject",
                "POST", SysPermission.RISK_LEVEL_CRITICAL, 164,
                "Approve or reject another user inventory disposition"),
            new PermissionSeed("inventory_disposition:policy", "配置库存处置策略",
                "/api/inventory-dispositions/policy", "*", SysPermission.RISK_LEVEL_CRITICAL,
                165, "Configure optional review, automatic expiry and business timezone"),
            new PermissionSeed("rma:view", "查看销售退货", "/api/rmas/**", "GET",
                SysPermission.RISK_LEVEL_NORMAL, 166, "View tenant-side RMA business details"),
            new PermissionSeed("rma:create", "创建和关闭销售退货", "/api/rmas;/api/rmas/*/cancel;/api/rmas/*/close-remainder", "POST",
                SysPermission.RISK_LEVEL_HIGH, 167, "Create linked RMA cases and close authorised remainders"),
            new PermissionSeed("rma:approve", "审批销售退货", "/api/rmas/*/approve;/api/rmas/*/reject", "POST",
                SysPermission.RISK_LEVEL_HIGH, 168, "Approve or reject another user's RMA"),
            new PermissionSeed("rma:receive", "执行退货收货", "/api/rma-warehouse/tasks/**", "*",
                SysPermission.RISK_LEVEL_HIGH, 169, "Receive authorised returns within assigned warehouse scope"),
            new PermissionSeed("rma:inspect", "执行退货质检", "/api/rma-warehouse/tasks/*/inspections", "POST",
                SysPermission.RISK_LEVEL_HIGH, 170, "Inspect return stock and choose an individual quantity disposition"),
            new PermissionSeed("rma:resolve", "处理退款或换货", "/api/rmas/*/refund-eligibility;/api/rmas/*/resolution;/api/rmas/*/refund-confirmation", "POST",
                SysPermission.RISK_LEVEL_HIGH, 171, "Request and confirm externally executed refunds or create replacement orders"),
            new PermissionSeed("rma:policy", "配置销售退货策略", "/api/rmas/policy", "*",
                SysPermission.RISK_LEVEL_HIGH, 172, "Configure optional RMA approval and risk features"),
            new PermissionSeed("rma:advance-resolution", "提前退款或换货", "/api/rmas/*/resolution", "POST",
                SysPermission.RISK_LEVEL_HIGH, 173, "Permit resolution before receipt and inspection when the tenant switch is enabled")
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
