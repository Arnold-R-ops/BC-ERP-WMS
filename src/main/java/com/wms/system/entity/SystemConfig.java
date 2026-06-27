package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 系统配置实体
 *
 * V3.7 架构：可配置化的系统参数
 *
 * 说明：
 * - 存储系统级别的配置参数
 * - 支持动态修改，无需重启应用
 * - 支持多种数据类型（DECIMAL, INTEGER, STRING, BOOLEAN）
 *
 * 核心配置项：
 * - sales.approval.amount_threshold: 销售订单审批金额阈值（默认 50000.00）
 *
 * 使用场景：
 * 1. 审批阈值配置
 * 2. 业务规则参数
 * 3. 系统开关控制
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "system_config",
    indexes = {
        @Index(name = "idx_system_config_key", columnList = "config_key")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_config_company_key", columnNames = {"company_id", "config_key"})
    }
)
public class SystemConfig extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 配置键（唯一标识）
     *
     * 命名规范：
     * - 使用点号分隔的层级结构
     * - 格式：domain.category.name
     *
     * 示例：
     * - sales.approval.amount_threshold
     * - inventory.warning.days_before_expiry
     * - system.feature.enable_auto_approval
     */
    @NotBlank(message = "配置键不能为空")
    @Size(max = 100, message = "配置键长度不能超过 100 个字符")
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    /**
     * 配置值
     *
     * 说明：
     * - 存储为字符串格式
     * - 根据 configType 进行类型转换
     *
     * 示例：
     * - DECIMAL: "50000.00"
     * - INTEGER: "90"
     * - STRING: "enabled"
     * - BOOLEAN: "true"
     */
    @NotBlank(message = "配置值不能为空")
    @Size(max = 500, message = "配置值长度不能超过 500 个字符")
    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    /**
     * 配置描述
     *
     * 说明：
     * - 用于前端显示和管理员理解
     * - 建议包含配置的用途和影响范围
     */
    @Size(max = 500, message = "配置描述长度不能超过 500 个字符")
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 配置类型
     *
     * 支持的类型：
     * - DECIMAL: 小数类型（如金额、比例）
     * - INTEGER: 整数类型（如天数、数量）
     * - STRING: 字符串类型（如名称、描述）
     * - BOOLEAN: 布尔类型（如开关、标志）
     */
    @NotBlank(message = "配置类型不能为空")
    @Size(max = 50, message = "配置类型长度不能超过 50 个字符")
    @Column(name = "config_type", nullable = false, length = 50)
    private String configType;

    /**
     * 配置类型枚举
     */
    public enum ConfigType {
        DECIMAL,
        INTEGER,
        STRING,
        BOOLEAN
    }
}
