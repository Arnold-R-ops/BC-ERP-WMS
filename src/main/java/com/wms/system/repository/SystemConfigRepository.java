package com.wms.system.repository;

import com.wms.system.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 系统配置数据访问接口
 *
 * V3.7 架构：系统配置管理
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 根据配置键查询配置
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    /**
     * 根据配置键查询配置
     *
     * 使用场景：
     * 1. 获取审批金额阈值
     * 2. 获取系统参数配置
     *
     * @param configKey 配置键
     * @return Optional<SystemConfig> 配置对象（可能为空）
     */
    Optional<SystemConfig> findByConfigKey(String configKey);

    /**
     * 检查配置键是否已存在
     *
     * @param configKey 配置键
     * @return true 表示配置键已存在
     */
    boolean existsByConfigKey(String configKey);
}
