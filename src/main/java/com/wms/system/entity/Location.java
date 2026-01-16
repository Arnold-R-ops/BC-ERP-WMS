package com.wms.system.entity;

import com.wms.system.entity.enums.Zone;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 库位实体类
 * 记录仓库的物理存储位置，精确到"货架-位号"级别
 *
 * 业务说明：
 * - 仓库编码 (warehouseCode)：标识不同仓库（支持多仓库管理）
 * - 区域 (zone)：按功能分区（A区高频、B区常规、C区低频等）
 * - 货架号 (shelfNumber)：具体货架编号（如：A-01, B-12）
 * - 位号 (positionNumber)：货架上的具体位置（如：001, 002）
 *
 * 库位编码规则（推荐）：
 * 格式：{仓库编码}-{区域}-{货架号}-{位号}
 * 示例：WH01-A-01-001（仓库01, A区, 01号货架, 001号位置）
 *
 * ERP 扩展预留：
 * - 未来可增加容量限制字段（maxCapacity, currentCapacity）
 * - 可增加温度要求字段（temperatureMin, temperatureMax）
 * - 可增加库位类型字段（NORMAL, COLD, DANGER）
 *
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
        @Index(name = "idx_warehouse_code", columnList = "warehouseCode"),
        @Index(name = "idx_zone", columnList = "zone"),
        @Index(name = "idx_shelf_position", columnList = "shelfNumber, positionNumber")
    },
    uniqueConstraints = {
        // 确保同一个仓库内，相同货架的相同位号不重复
        @UniqueConstraint(name = "uk_location", columnNames = {"warehouseCode", "zone", "shelfNumber", "positionNumber"})
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
     * 仓库编码（支持多仓库管理）
     * 格式：字母开头，可包含字母、数字、短横线
     * 示例："WH01", "MAIN-WH", "SH-WH-01"
     *
     * 单仓库系统可固定为 "WH01"
     * 多仓库系统需要根据实际情况分配编码
     */
    @NotBlank(message = "仓库编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,19}$", message = "仓库编码格式不正确（字母开头，可含字母、数字、短横线，长度2-20）")
    @Column(nullable = false, length = 20)
    private String warehouseCode;

    /**
     * 区域（枚举类型）
     * 按照商品特性和周转率分区：
     * - ZONE_A: 高频商品区（快速拣货）
     * - ZONE_B: 常规商品区
     * - ZONE_C: 低频商品区
     * - ZONE_D: 冷藏区
     * - ZONE_E: 危险品区
     * - ZONE_Q: 质检区
     * - ZONE_R: 退货区
     */
    @NotNull(message = "区域不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Zone zone;

    /**
     * 货架号（字母数字组合）
     * 格式：通常为"区域代码-数字编号"
     * 示例："A-01", "B-12", "C-005"
     *
     * 建议命名规则：
     * - 同一区域内的货架按顺序编号
     * - 靠近出货口的货架号较小（便于优化拣货路径）
     */
    @NotBlank(message = "货架号不能为空")
    @Size(max = 20, message = "货架号长度不能超过 20 个字符")
    @Column(nullable = false, length = 20)
    private String shelfNumber;

    /**
     * 位号（具体位置编号）
     * 格式：通常为 3 位数字（001, 002, ...）
     * 示例："001", "012", "099"
     *
     * 建议命名规则：
     * - 从下到上、从左到右依次编号
     * - 常用商品放在腰部高度（减少弯腰和爬高次数）
     */
    @NotBlank(message = "位号不能为空")
    @Size(max = 10, message = "位号长度不能超过 10 个字符")
    @Column(nullable = false, length = 10)
    private String positionNumber;

    /**
     * 完整库位编码（自动生成，便于快速查询和展示）
     * 格式：{仓库编码}-{区域}-{货架号}-{位号}
     * 示例："WH01-ZONE_A-A-01-001"
     *
     * 技术实现：
     * - 在保存实体前自动生成（通过 @PrePersist / @PreUpdate）
     * - 或者通过数据库触发器生成
     */
    @Column(unique = true, length = 100)
    private String locationCode;

    /**
     * 库位状态（是否可用）
     * - true: 正常使用
     * - false: 禁用（维修中、封存等）
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 备注信息（可选）
     * 可记录特殊说明，如："靠近消防栓"、"需要叉车"等
     */
    @Column(length = 500)
    private String remark;

    /**
     * JPA 生命周期回调：在持久化或更新前自动生成 locationCode
     */
    @PrePersist
    @PreUpdate
    private void generateLocationCode() {
        this.locationCode = String.format("%s-%s-%s-%s",
            warehouseCode,
            zone.name(),
            shelfNumber,
            positionNumber
        );
    }
}
