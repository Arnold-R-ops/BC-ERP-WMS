package com.wms.system.controller;

import com.wms.system.entity.Location;
import com.wms.system.entity.enums.Zone;
import com.wms.system.service.LocationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库位管理控制器
 *
 * 提供库位的 CRUD 操作接口
 *
 * Phase 3.4 更新：
 * - 创建库位时关联 Warehouse 实体
 * - warehouseCode 自动从 warehouse.code 同步
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@Slf4j
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Validated
public class LocationController {

    private final LocationService locationService;

    /**
     * 根据ID获取库位
     *
     * @param id 库位ID
     * @return 库位信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<Location> getLocationById(@PathVariable Long id) {
        log.info("Getting location by id: {}", id);
        Location location = locationService.getLocationById(id);
        return ResponseEntity.ok(location);
    }

    /**
     * 根据仓库ID获取所有库位
     *
     * @param warehouseId 仓库ID
     * @return 库位列表
     */
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<Location>> getLocationsByWarehouse(@PathVariable Long warehouseId) {
        log.info("Getting locations by warehouse id: {}", warehouseId);
        List<Location> locations = locationService.getLocationsByWarehouse(warehouseId);
        return ResponseEntity.ok(locations);
    }

    /**
     * 获取指定仓库的空闲库位
     *
     * @param warehouseId 仓库ID
     * @return 空闲库位列表
     */
    @GetMapping("/warehouse/{warehouseId}/empty")
    public ResponseEntity<List<Location>> getEmptyLocationsByWarehouse(@PathVariable Long warehouseId) {
        log.info("Getting empty locations by warehouse id: {}", warehouseId);
        List<Location> locations = locationService.getEmptyLocationsByWarehouse(warehouseId);
        return ResponseEntity.ok(locations);
    }

    /**
     * 创建库位
     *
     * @param request 创建库位请求
     * @return 创建的库位信息
     */
    @PostMapping
    public ResponseEntity<Location> createLocation(@RequestBody @Validated CreateLocationRequest request) {
        log.info("Creating location: warehouseId={}, zone={}, shelf={}, position={}",
                request.warehouseId, request.zone, request.shelfNumber, request.positionNumber);
        Location location = locationService.createLocation(
                request.warehouseId,
                request.zone,
                request.shelfNumber,
                request.positionNumber,
                request.remark
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(location);
    }

    /**
     * 更新库位信息
     *
     * @param id 库位ID
     * @param request 更新库位请求
     * @return 更新后的库位信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<Location> updateLocation(
            @PathVariable Long id,
            @RequestBody @Validated UpdateLocationRequest request) {
        log.info("Updating location: id={}", id);
        Location location = locationService.updateLocation(id, request.remark);
        return ResponseEntity.ok(location);
    }

    /**
     * 启用库位
     *
     * @param id 库位ID
     * @return 更新后的库位信息
     */
    @PutMapping("/{id}/enable")
    public ResponseEntity<Location> enableLocation(@PathVariable Long id) {
        log.info("Enabling location: id={}", id);
        Location location = locationService.enableLocation(id);
        return ResponseEntity.ok(location);
    }

    /**
     * 禁用库位
     *
     * @param id 库位ID
     * @return 更新后的库位信息
     */
    @PutMapping("/{id}/disable")
    public ResponseEntity<Location> disableLocation(@PathVariable Long id) {
        log.info("Disabling location: id={}", id);
        Location location = locationService.disableLocation(id);
        return ResponseEntity.ok(location);
    }

    /**
     * 创建库位请求 DTO
     */
    public record CreateLocationRequest(
            @NotNull(message = "仓库ID不能为空")
            Long warehouseId,

            @NotNull(message = "库区不能为空")
            Zone zone,

            @NotBlank(message = "货架号不能为空")
            @Size(max = 20, message = "货架号长度不能超过20个字符")
            String shelfNumber,

            @NotBlank(message = "位号不能为空")
            @Size(max = 10, message = "位号长度不能超过10个字符")
            String positionNumber,

            @Size(max = 500, message = "备注长度不能超过500个字符")
            String remark
    ) {}

    /**
     * 更新库位请求 DTO
     */
    public record UpdateLocationRequest(
            @Size(max = 500, message = "备注长度不能超过500个字符")
            String remark
    ) {}
}
