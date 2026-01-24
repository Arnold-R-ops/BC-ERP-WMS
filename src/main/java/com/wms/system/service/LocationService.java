package com.wms.system.service;

import com.wms.system.entity.Location;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 库位管理服务
 *
 * 核心职责：
 * 1. 库位创建、查询、更新
 * 2. 库位启用/禁用管理
 * 3. 空闲库位查询（用于入库推荐）
 * 4. 业务异常处理（使用 Error Key 系统）
 *
 * Phase 3.4 更新：
 * - 创建库位时关联 Warehouse 实体
 * - warehouseCode 自动从 warehouse.code 同步（@PrePersist/@PreUpdate）
 * - 支持基于 warehouseId 的查询方法
 *
 * 技术特性：
 * - @Transactional: 确保数据一致性
 * - Error Key System: 所有异常使用错误键，支持前端国际化
 * - Constructor Injection: 使用 @RequiredArgsConstructor 注入依赖
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * 创建库位
     *
     * 业务流程：
     * 1. 验证仓库是否存在
     * 2. 创建库位实体（关联 Warehouse 对象）
     * 3. 保存到数据库（触发 @PrePersist，自动同步 warehouseCode 和生成 locationCode）
     *
     * Phase 3.4 关键逻辑：
     * - 通过 warehouseId 查询 Warehouse 对象
     * - 设置 location.warehouse 关系
     * - warehouseCode 由 @PrePersist 自动从 warehouse.code 同步
     * - locationCode 由 @PrePersist 自动生成
     *
     * 异常处理：
     * - WAREHOUSE_NOT_FOUND: 仓库不存在
     * - LOCATION_ALREADY_EXISTS: 库位已存在（唯一约束冲突）
     *
     * @param warehouseId 仓库ID
     * @param zone 库位区域
     * @param shelfNumber 货架号
     * @param positionNumber 位号
     * @param remark 备注（可选）
     * @return 创建的库位对象
     * @throws BusinessException 如果仓库不存在或库位已存在
     */
    @Transactional(rollbackFor = Exception.class)
    public Location createLocation(Long warehouseId, Zone zone, String shelfNumber,
                                     String positionNumber, String remark) {
        log.info("Creating location: warehouseId={}, zone={}, shelf={}, position={}",
                 warehouseId, zone, shelfNumber, positionNumber);

        // 验证仓库是否存在
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
            .orElseThrow(() -> {
                log.error("Warehouse not found: id={}", warehouseId);
                return new BusinessException(
                    ErrorKeys.WAREHOUSE_NOT_FOUND,
                    Map.of("warehouseId", warehouseId)
                );
            });

        // 检查库位是否已存在（通过唯一约束字段）
        locationRepository.findByWarehouseCodeAndZoneAndShelfNumberAndPositionNumber(
            warehouse.getCode(), zone, shelfNumber, positionNumber
        ).ifPresent(existingLocation -> {
            log.error("Location already exists: warehouseCode={}, zone={}, shelf={}, position={}",
                      warehouse.getCode(), zone, shelfNumber, positionNumber);
            throw new BusinessException(
                ErrorKeys.LOCATION_ALREADY_EXISTS,
                Map.of(
                    "warehouseCode", warehouse.getCode(),
                    "zone", zone.name(),
                    "shelfNumber", shelfNumber,
                    "positionNumber", positionNumber
                )
            );
        });

        // 创建库位实体（关联 Warehouse 对象）
        Location location = Location.builder()
            .warehouse(warehouse)  // 设置 warehouse 关系（warehouseCode 会自动同步）
            .zone(zone)
            .shelfNumber(shelfNumber)
            .positionNumber(positionNumber)
            .enabled(true)
            .remark(remark)
            .build();

        // 保存到数据库（触发 @PrePersist，自动同步 warehouseCode 和生成 locationCode）
        Location savedLocation = locationRepository.save(location);
        log.info("Location created successfully: id={}, locationCode={}, warehouseCode={}",
                 savedLocation.getId(), savedLocation.getLocationCode(), savedLocation.getWarehouseCode());

        return savedLocation;
    }

    /**
     * 根据ID查询库位
     *
     * 异常处理：
     * - LOCATION_NOT_FOUND: 库位不存在
     *
     * @param locationId 库位ID
     * @return 库位对象
     * @throws BusinessException 如果库位不存在
     */
    public Location getLocationById(Long locationId) {
        log.debug("Querying location by id: {}", locationId);

        return locationRepository.findById(locationId)
            .orElseThrow(() -> {
                log.error("Location not found: id={}", locationId);
                return new BusinessException(
                    ErrorKeys.LOCATION_NOT_FOUND,
                    Map.of("locationId", locationId)
                );
            });
    }

    /**
     * 根据仓库ID查询所有库位
     *
     * 使用场景：
     * 1. 仓库管理界面显示库位列表
     * 2. 统计仓库库位数量
     *
     * @param warehouseId 仓库ID
     * @return 该仓库的所有库位
     */
    public List<Location> getLocationsByWarehouse(Long warehouseId) {
        log.debug("Querying locations by warehouseId: {}", warehouseId);
        return locationRepository.findByWarehouseId(warehouseId);
    }

    /**
     * 查询指定仓库的空闲库位
     *
     * 业务场景：
     * 1. 入库时推荐可用库位
     * 2. 统计仓库可用库位数量
     *
     * @param warehouseId 仓库ID
     * @return 该仓库的所有空闲库位
     */
    public List<Location> getEmptyLocationsByWarehouse(Long warehouseId) {
        log.debug("Querying empty locations by warehouseId: {}", warehouseId);
        return locationRepository.findEmptyLocationsByWarehouseId(warehouseId);
    }

    /**
     * 更新库位信息
     *
     * 业务规则：
     * - 不能修改仓库关系（warehouse）
     * - 不能修改核心位置字段（zone, shelfNumber, positionNumber）
     * - 只能修改备注信息
     *
     * 异常处理：
     * - LOCATION_NOT_FOUND: 库位不存在
     *
     * @param locationId 库位ID
     * @param remark 新的备注信息
     * @return 更新后的库位对象
     * @throws BusinessException 如果库位不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public Location updateLocation(Long locationId, String remark) {
        log.info("Updating location: id={}", locationId);

        // 查询库位
        Location location = getLocationById(locationId);

        // 更新备注
        if (remark != null) {
            location.setRemark(remark);
        }

        // 保存更新
        Location updatedLocation = locationRepository.save(location);
        log.info("Location updated successfully: id={}, locationCode={}",
                 updatedLocation.getId(), updatedLocation.getLocationCode());

        return updatedLocation;
    }

    /**
     * 启用库位
     *
     * 业务场景：
     * 重新启用已禁用的库位
     *
     * 异常处理：
     * - LOCATION_NOT_FOUND: 库位不存在
     *
     * @param locationId 库位ID
     * @return 更新后的库位对象
     * @throws BusinessException 如果库位不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public Location enableLocation(Long locationId) {
        log.info("Enabling location: id={}", locationId);

        Location location = getLocationById(locationId);
        location.setEnabled(true);

        Location updatedLocation = locationRepository.save(location);
        log.info("Location enabled successfully: id={}, locationCode={}",
                 updatedLocation.getId(), updatedLocation.getLocationCode());

        return updatedLocation;
    }

    /**
     * 禁用库位
     *
     * 业务场景：
     * 临时禁用库位（维修中、封存等）
     *
     * 业务规则：
     * - 禁用后不能用于新的入库操作
     * - 现有库存数据不受影响
     * - 可以重新启用
     *
     * 异常处理：
     * - LOCATION_NOT_FOUND: 库位不存在
     *
     * @param locationId 库位ID
     * @return 更新后的库位对象
     * @throws BusinessException 如果库位不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public Location disableLocation(Long locationId) {
        log.info("Disabling location: id={}", locationId);

        Location location = getLocationById(locationId);
        location.setEnabled(false);

        Location updatedLocation = locationRepository.save(location);
        log.info("Location disabled successfully: id={}, locationCode={}",
                 updatedLocation.getId(), updatedLocation.getLocationCode());

        return updatedLocation;
    }
}
