package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 仓库实体类
 * 记录仓库主数据信息，支持多仓库管理
 *
 * 业务说明：
 * - 仓库编码 (code)：唯一业务标识符（如：WH01, WH02）
 * - 仓库名称 (name)：仓库的显示名称
 * - 地址 (address)：仓库物理地址
 * - 联系方式 (contact)：联系人或电话
 * - 激活状态 (isActive)：是否启用该仓库
 *
 * 关系说明：
 * - 一个仓库包含多个库位（One-to-Many with Location）
 * - 库位通过 warehouse_id 外键关联仓库
 * - 库位的 warehouseCode 字段从 warehouse.code 自动同步（冗余设计，优化性能）
 *
 * Phase 3.4 架构决策：
 * - 使用 code 作为业务主键（唯一约束）
 * - 使用 id 作为数据库主键（自增）
 * - Location.warehouseCode 保留为冗余字段，通过 @PrePersist/@PreUpdate 自动同步
 * - 冗余设计优势：避免频繁 JOIN 查询，保持现有索引效率，向后兼容
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "locations")  // 防止循环引用
@Entity
@Table(
    name = "warehouses",
    indexes = {
        @Index(name = "idx_warehouse_code", columnList = "code"),
        @Index(name = "idx_warehouse_active", columnList = "isActive")
    }
)
public class Warehouse extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 仓库编码（业务主键，唯一标识符）
     * 格式：字母开头，可包含字母、数字、短横线
     * 示例：\"WH01\", \"WH02\", \"MAIN-WH\", \"SH-WH-01\"
     *
     * 技术约束：
     * - 必须唯一（数据库唯一约束）
     * - 长度：2-50 字符
     * - 格式：大写字母开头，可含大写字母、数字、短横线
     *
     * 业务规则：
     * - 一旦创建，不建议修改（会影响关联的库位数据）
     * - Location.warehouseCode 会自动同步此字段的值
     */
    @NotBlank(message = "仓库编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,19}$", message = "仓库编码格式不正确（大写字母开头，可含字母、数字、短横线，长度2-20）")
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /**
     * 仓库名称
     * 示例：\"上海主仓库\", \"北京分仓\", \"深圳冷链仓\"
     */
    @NotBlank(message = "仓库名称不能为空")
    @Size(max = 100, message = "仓库名称长度不能超过 100 个字符")
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 仓库地址（可选）
     * 示例：\"上海市浦东新区张江高科技园区XXX路XXX号\"
     */
    @Column(length = 255)
    private String address;

    /**
     * 联系方式（可选）
     * 可以是联系人姓名、电话、邮箱等
     * 示例：\"张三 13800138000\", \"warehouse@example.com\"
     */
    @Column(length = 50)
    private String contact;

    /**
     * 联系电话（可选）
     * 仓库负责人或值班电话
     */
    @Column(length = 20)
    private String phone;

    /**
     * 激活状态
     * - true: 仓库正常使用
     * - false: 仓库停用（不影响历史数据，但不能创建新库位）
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 关联的库位列表（一对多关系）
     *
     * 技术实现：
     * - 使用 LAZY 加载（避免性能问题）
     * - 使用 mappedBy 指定关系维护方（Location.warehouse）
     * - 使用 CascadeType.ALL 级联操作（删除仓库时级联删除库位）
     * - 使用 @ToString.Exclude 防止循环引用
     *
     * 业务规则：
     * - 删除仓库会级联删除所有关联的库位（谨慎操作）
     * - 建议先停用仓库（isActive=false），确认无业务影响后再删除
     */
    @OneToMany(mappedBy = "warehouse", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Location> locations = new ArrayList<>();

    /**
     * 辅助方法：添加库位到仓库
     * 维护双向关系的一致性
     *
     * @param location 要添加的库位
     */
    public void addLocation(Location location) {
        if (location != null) {
            locations.add(location);
            location.setWarehouse(this);
        }
    }

    /**
     * 辅助方法：从仓库移除库位
     * 维护双向关系的一致性
     *
     * @param location 要移除的库位
     */
    public void removeLocation(Location location) {
        if (location != null) {
            locations.remove(location);
            location.setWarehouse(null);
        }
    }
}
