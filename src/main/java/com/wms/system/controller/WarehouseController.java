package com.wms.system.controller;

import com.wms.system.entity.Warehouse;
import com.wms.system.service.WarehouseService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仓库管理控制器
 *
 * 提供仓库的 CRUD 操作接口
 *
 * Phase 3.4 新增：
 * - 支持多仓库管理
 * - 提供仓库主数据维护功能
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@Slf4j
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Validated
public class WarehouseController {

    private final WarehouseService warehouseService;

    /**
     * 获取所有仓库
     *
     * @return 仓库列表
     */
    @GetMapping
    public ResponseEntity<List<Warehouse>> getAllWarehouses() {
        log.info("Getting all warehouses");
        List<Warehouse> warehouses = warehouseService.getAllWarehouses();
        return ResponseEntity.ok(warehouses);
    }

    /**
     * 获取所有激活的仓库
     *
     * @return 激活的仓库列表
     */
    @GetMapping("/active")
    public ResponseEntity<List<Warehouse>> getAllActiveWarehouses() {
        log.info("Getting all active warehouses");
        List<Warehouse> warehouses = warehouseService.getAllActiveWarehouses();
        return ResponseEntity.ok(warehouses);
    }

    /**
     * 根据ID获取仓库
     *
     * @param id 仓库ID
     * @return 仓库信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<Warehouse> getWarehouseById(@PathVariable Long id) {
        log.info("Getting warehouse by id: {}", id);
        Warehouse warehouse = warehouseService.getWarehouseById(id);
        return ResponseEntity.ok(warehouse);
    }

    /**
     * 根据编码获取仓库
     *
     * @param code 仓库编码
     * @return 仓库信息
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<Warehouse> getWarehouseByCode(@PathVariable String code) {
        log.info("Getting warehouse by code: {}", code);
        Warehouse warehouse = warehouseService.getWarehouseByCode(code);
        return ResponseEntity.ok(warehouse);
    }

    /**
     * 创建仓库
     *
     * @param request 创建仓库请求
     * @return 创建的仓库信息
     */
    @PostMapping
    public ResponseEntity<Warehouse> createWarehouse(@RequestBody @Validated CreateWarehouseRequest request) {
        log.info("Creating warehouse: code={}, name={}", request.code, request.name);
        Warehouse warehouse = warehouseService.createWarehouse(
                request.code,
                request.name,
                request.address,
                request.contact,
                request.phone
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(warehouse);
    }

    /**
     * 更新仓库信息
     *
     * @param id 仓库ID
     * @param request 更新仓库请求
     * @return 更新后的仓库信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<Warehouse> updateWarehouse(
            @PathVariable Long id,
            @RequestBody @Validated UpdateWarehouseRequest request) {
        log.info("Updating warehouse: id={}", id);
        Warehouse warehouse = warehouseService.updateWarehouse(
                id,
                request.name,
                request.address,
                request.contact
        );
        return ResponseEntity.ok(warehouse);
    }

    /**
     * 激活仓库
     *
     * @param id 仓库ID
     * @return 更新后的仓库信息
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<Warehouse> activateWarehouse(@PathVariable Long id) {
        log.info("Activating warehouse: id={}", id);
        Warehouse warehouse = warehouseService.activateWarehouse(id);
        return ResponseEntity.ok(warehouse);
    }

    /**
     * 停用仓库
     *
     * @param id 仓库ID
     * @return 更新后的仓库信息
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Warehouse> deactivateWarehouse(@PathVariable Long id) {
        log.info("Deactivating warehouse: id={}", id);
        Warehouse warehouse = warehouseService.deactivateWarehouse(id);
        return ResponseEntity.ok(warehouse);
    }

    /**
     * 获取仓库的库位数量
     *
     * @param id 仓库ID
     * @return 库位数量
     */
    @GetMapping("/{id}/location-count")
    public ResponseEntity<Long> getLocationCount(@PathVariable Long id) {
        log.info("Getting location count for warehouse: id={}", id);
        long count = warehouseService.getLocationCount(id);
        return ResponseEntity.ok(count);
    }

    /**
     * 创建仓库请求 DTO
     */
    public record CreateWarehouseRequest(
            @NotBlank(message = "仓库编码不能为空")
            @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,49}$", message = "仓库编码格式不正确")
            String code,

            @NotBlank(message = "仓库名称不能为空")
            @Size(max = 100, message = "仓库名称长度不能超过100个字符")
            String name,

            @Size(max = 255, message = "仓库地址长度不能超过255个字符")
            String address,

            @Size(max = 50, message = "联系方式长度不能超过50个字符")
            String contact,

            @Size(max = 20, message = "联系电话长度不能超过20个字符")
            String phone
    ) {}

    /**
     * 更新仓库请求 DTO
     */
    public record UpdateWarehouseRequest(
            @Size(max = 100, message = "仓库名称长度不能超过100个字符")
            String name,

            @Size(max = 255, message = "仓库地址长度不能超过255个字符")
            String address,

            @Size(max = 50, message = "联系方式长度不能超过50个字符")
            String contact
    ) {}
}
