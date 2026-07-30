package com.wms.system.service;

import com.wms.system.entity.Warehouse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 仓库管理服务
 *
 * 核心职责：
 * 1. 仓库创建、查询、更新
 * 2. 仓库激活/停用管理
 * 3. 仓库统计信息（库位数量等）
 * 4. 业务异常处理（使用 Error Key 系统）
 *
 * Phase 3.4 新增：
 * - 支持多仓库管理
 * - 提供仓库主数据维护功能
 * - 与 Location 实体建立关联关系
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
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    /**
     * 创建仓库
     *
     * 业务流程：
     * 1. 验证仓库编码唯一性
     * 2. 创建仓库实体
     * 3. 保存到数据库
     *
     * 异常处理：
     * - WAREHOUSE_ALREADY_EXISTS: 仓库编码已存在
     *
     * @param code 仓库编码（例如：WH01, WH02）
     * @param name 仓库名称
     * @param address 仓库地址（可选）
     * @param contact 联系方式（可选）
     * @return 创建的仓库对象
     * @throws BusinessException 如果仓库编码已存在
     */
    @Transactional(rollbackFor = Exception.class)
    public Warehouse createWarehouse(String code, String name, String address, String contact, String phone) {
        log.info("Creating warehouse: code={}, name={}", code, name);

        // 验证仓库编码唯一性
        if (warehouseRepository.existsByCode(code)) {
            log.error("Warehouse code already exists: {}", code);
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_ALREADY_EXISTS,
                Map.of("code", code)
            );
        }

        // 创建仓库实体
        Warehouse warehouse = Warehouse.builder()
            .code(code)
            .name(name)
            .address(address)
            .contact(contact)
            .phone(phone)
            .isActive(true)
            .build();

        // 保存到数据库
        Warehouse savedWarehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse created successfully: id={}, code={}", savedWarehouse.getId(), savedWarehouse.getCode());

        return savedWarehouse;
    }

    /**
     * 根据ID查询仓库
     *
     * 异常处理：
     * - WAREHOUSE_NOT_FOUND: 仓库不存在
     *
     * @param warehouseId 仓库ID
     * @return 仓库对象
     * @throws BusinessException 如果仓库不存在
     */
    public Warehouse getWarehouseById(Long warehouseId) {
        log.debug("Querying warehouse by id: {}", warehouseId);

        return warehouseRepository.findById(warehouseId)
            .orElseThrow(() -> {
                log.error("Warehouse not found: id={}", warehouseId);
                return new BusinessException(
                    ErrorKeys.WAREHOUSE_NOT_FOUND,
                    Map.of("warehouseId", warehouseId)
                );
            });
    }

    /**
     * 根据编码查询仓库
     *
     * 异常处理：
     * - WAREHOUSE_NOT_FOUND: 仓库不存在
     *
     * @param code 仓库编码
     * @return 仓库对象
     * @throws BusinessException 如果仓库不存在
     */
    public Warehouse getWarehouseByCode(String code) {
        log.debug("Querying warehouse by code: {}", code);

        return warehouseRepository.findByCode(code)
            .orElseThrow(() -> {
                log.error("Warehouse not found: code={}", code);
                return new BusinessException(
                    ErrorKeys.WAREHOUSE_NOT_FOUND,
                    Map.of("code", code)
                );
            });
    }

    /**
     * 查询所有激活的仓库
     *
     * 使用场景：
     * 1. 下拉选择框（选择仓库）
     * 2. 仓库列表展示（排除已停用的仓库）
     *
     * @return 所有激活状态的仓库列表
     */
    public List<Warehouse> getAllActiveWarehouses() {
        log.debug("Querying all active warehouses");
        return warehouseRepository.findAllActive();
    }

    /**
     * 查询所有仓库（包括已停用的）
     *
     * 使用场景：
     * 1. 仓库管理界面（显示所有仓库）
     * 2. 统计报表（包含历史数据）
     *
     * @return 所有仓库列表（按编码排序）
     */
    public List<Warehouse> getAllWarehouses() {
        log.debug("Querying all warehouses");
        return warehouseRepository.findAll();
    }

    /**
     * 更新仓库信息
     *
     * 业务规则：
     * - 仓库编码不可修改（业务主键）
     * - 只能修改名称、地址、联系方式
     *
     * 异常处理：
     * - WAREHOUSE_NOT_FOUND: 仓库不存在
     *
     * @param warehouseId 仓库ID
     * @param name 新的仓库名称（可选）
     * @param address 新的仓库地址（可选）
     * @param contact 新的联系方式（可选）
     * @return 更新后的仓库对象
     * @throws BusinessException 如果仓库不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public Warehouse updateWarehouse(Long warehouseId, String name, String address, String contact) {
        return updateWarehouse(warehouseId, name, address, contact, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Warehouse updateWarehouse(Long warehouseId, String name, String address, String contact, String phone) {
        log.info("Updating warehouse: id={}", warehouseId);

        // 查询仓库
        Warehouse warehouse = getWarehouseById(warehouseId);

        // 更新字段（只更新非空字段）
        if (name != null) {
            warehouse.setName(name);
        }
        if (address != null) {
            warehouse.setAddress(address);
        }
        if (contact != null) {
            warehouse.setContact(contact);
        }
        if (phone != null) {
            warehouse.setPhone(phone);
        }

        // 保存更新
        Warehouse updatedWarehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse updated successfully: id={}, code={}", updatedWarehouse.getId(), updatedWarehouse.getCode());

        return updatedWarehouse;
    }

    /**
     * 激活仓库
     *
     * 业务场景：
     * 重新启用已停用的仓库
     *
     * 异常处理：
     * - WAREHOUSE_NOT_FOUND: 仓库不存在
     *
     * @param warehouseId 仓库ID
     * @return 更新后的仓库对象
     * @throws BusinessException 如果仓库不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public Warehouse activateWarehouse(Long warehouseId) {
        log.info("Activating warehouse: id={}", warehouseId);

        Warehouse warehouse = getWarehouseById(warehouseId);
        warehouse.setIsActive(true);

        Warehouse updatedWarehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse activated successfully: id={}, code={}", updatedWarehouse.getId(), updatedWarehouse.getCode());

        return updatedWarehouse;
    }

    /**
     * 停用仓库
     *
     * 业务场景：
     * 临时停用仓库（不删除数据，保留历史记录）
     *
     * 业务规则：
     * - 停用后不能创建新库位
     * - 现有库位和库存数据不受影响
     * - 可以重新激活
     *
     * 异常处理：
     * - WAREHOUSE_NOT_FOUND: 仓库不存在
     *
     * @param warehouseId 仓库ID
     * @return 更新后的仓库对象
     * @throws BusinessException 如果仓库不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public Warehouse deactivateWarehouse(Long warehouseId) {
        log.info("Deactivating warehouse: id={}", warehouseId);

        Warehouse warehouse = getWarehouseById(warehouseId);
        warehouse.setIsActive(false);

        Warehouse updatedWarehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse deactivated successfully: id={}, code={}", updatedWarehouse.getId(), updatedWarehouse.getCode());

        return updatedWarehouse;
    }

    /**
     * 获取仓库的库位数量
     *
     * 使用场景：
     * 1. 仓库管理界面显示统计信息
     * 2. 判断仓库是否可以删除（有库位则不能删除）
     *
     * @param warehouseId 仓库ID
     * @return 该仓库的库位数量
     */
    public long getLocationCount(Long warehouseId) {
        log.debug("Counting locations for warehouse: id={}", warehouseId);
        return warehouseRepository.countLocationsByWarehouseId(warehouseId);
    }
}
