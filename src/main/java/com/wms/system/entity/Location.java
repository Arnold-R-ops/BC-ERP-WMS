package com.wms.system.entity;

import com.wms.system.entity.enums.Zone;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 库位实体�? * 记录仓库的物理存储位置，精确�?货架-位号"级别
 *
 * 业务说明�? * - 仓库编码 (warehouseCode)：标识不同仓库（支持多仓库管理）
 * - 区域 (zone)：按功能分区（A区高频、B区常规、C区低频等�? * - 货架�?(shelfNumber)：具体货架编号（如：A-01, B-12�? * - 位号 (positionNumber)：货架上的具体位置（如：001, 002�? *
 * 库位编码规则（推荐）�? * 格式：{仓库编码}-{区域}-{货架号}-{位号}
 * 示例：WH01-A-01-001（仓�?1, A�? 01号货�? 001号位置）
 *
 * ERP 扩展预留�? * - 未来可增加容量限制字段（maxCapacity, currentCapacity�? * - 可增加温度要求字段（temperatureMin, temperatureMax�? * - 可增加库位类型字段（NORMAL, COLD, DANGER�? *
 * @author WMS Team
 * @since 2025-01-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "locations",
    indexes = {
        @Index(name = "idx_location_warehouse_code", columnList = "warehouseCode"),
        @Index(name = "idx_zone", columnList = "zone"),
        @Index(name = "idx_shelf_position", columnList = "shelfNumber, positionNumber")
    },
    uniqueConstraints = {
        // 确保同一个仓库内，相同货架的相同位号不重�?        @UniqueConstraint(name = "uk_location", columnNames = {"warehouseCode", "zone", "shelfNumber", "positionNumber"})
    }
)
public class Location extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 仓库关系（Phase 3.4 新增�?     * 多对一关系：多个库位属于一个仓�?     *
     * 技术实现：
     * - 使用 LAZY 加载（避免性能问题�?     * - 使用 @JoinColumn 指定外键列名（warehouse_id�?     * - 使用 @ToString.Exclude 防止循环引用
     * - 外键约束：ON DELETE RESTRICT, ON UPDATE CASCADE
     *
     * 业务规则�?     * - 创建库位时必须指定仓�?     * - 仓库不能为空（optional = false�?     * - warehouseCode 字段会从 warehouse.code 自动同步（@PrePersist/@PreUpdate�?     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_location_warehouse"))
    @ToString.Exclude
    private Warehouse warehouse;

    /**
     * 仓库编码（冗余字段，Phase 3.4 保留用于性能优化�?     * 格式：字母开头，可包含字母、数字、短横线
     * 示例�?WH01", "MAIN-WH", "SH-WH-01"
     *
     * Phase 3.4 架构决策�?     * - 此字段从 warehouse.code 自动同步（@PrePersist/@PreUpdate�?     * - 保留原因�?     *   1. 性能优化：避免频�?JOIN 查询
     *   2. 索引效率：现有复合唯一约束依赖此字�?     *   3. 向后兼容：现有查询方法继续工�?     * - 不应手动设置此字段，�?JPA 生命周期回调自动维护
     *
     * 单仓库系统可固定�?"WH01"
     * 多仓库系统需要根据实际情况分配编�?     */
    @NotBlank(message = "仓库编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,19}$", message = "仓库编码格式不正确（字母开头，可含字母、数字、短横线，长�?-20�?)
    @Column(nullable = false, length = 20)
    private String warehouseCode;

    /**
     * 区域（枚举类型）
     * 按照商品特性和周转率分区：
     * - ZONE_A: 高频商品区（快速拣货）
     * - ZONE_B: 常规商品�?     * - ZONE_C: 低频商品�?     * - ZONE_D: 冷藏�?     * - ZONE_E: 危险品区
     * - ZONE_Q: 质检�?     * - ZONE_R: 退货区
     */
    @NotNull(message = "区域不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Zone zone;

    /**
     * 货架号（字母数字组合�?     * 格式：通常�?区域代码-数字编号"
     * 示例�?A-01", "B-12", "C-005"
     *
     * 建议命名规则�?     * - 同一区域内的货架按顺序编�?     * - 靠近出货口的货架号较小（便于优化拣货路径�?     */
    @NotBlank(message = "货架号不能为�?)
    @Size(max = 20, message = "货架号长度不能超�?20 个字�?)
    @Column(nullable = false, length = 20)
    private String shelfNumber;

    /**
     * 位号（具体位置编号）
     * 格式：通常�?3 位数字（001, 002, ...�?     * 示例�?001", "012", "099"
     *
     * 建议命名规则�?     * - 从下到上、从左到右依次编�?     * - 常用商品放在腰部高度（减少弯腰和爬高次数�?     */
    @NotBlank(message = "位号不能为空")
    @Size(max = 10, message = "位号长度不能超过 10 个字�?)
    @Column(nullable = false, length = 10)
    private String positionNumber;

    /**
     * 完整库位编码（自动生成，便于快速查询和展示�?     * 格式：{仓库编码}-{区域}-{货架号}-{位号}
     * 示例�?WH01-ZONE_A-A-01-001"
     *
     * 技术实现：
     * - 在保存实体前自动生成（通过 @PrePersist / @PreUpdate�?     * - 或者通过数据库触发器生成
     */
    @Column(unique = true, length = 100)
    private String locationCode;

    /**
     * 库位状态（是否可用�?     * - true: 正常使用
     * - false: 禁用（维修中、封存等�?     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 备注信息（可选）
     * 可记录特殊说明，如："靠近消防�?�?需要叉�?�?     */
    @Column(length = 500)
    private String remark;

    /**
     * JPA 生命周期回调：在持久化或更新前自动同�?warehouseCode 并生�?locationCode
     *
     * Phase 3.4 更新�?     * - Step 1: �?warehouse.code 同步�?warehouseCode（冗余字段自动维护）
     * - Step 2: 使用 warehouseCode 生成 locationCode（保持现有逻辑�?     */
    @PrePersist
    @PreUpdate
    private void syncWarehouseCodeAndGenerateLocationCode() {
        // Step 1: 同步 warehouse.code �?warehouseCode（冗余字段维护）
        if (warehouse != null && warehouse.getCode() != null) {
            this.warehouseCode = warehouse.getCode();
        }

        // Step 2: 生成 locationCode（现有逻辑�?        if (warehouseCode != null && zone != null &&
            shelfNumber != null && positionNumber != null) {
            this.locationCode = String.format("%s-%s-%s-%s",
                warehouseCode,
                zone.name(),
                shelfNumber,
                positionNumber
            );
        }
    }
}

