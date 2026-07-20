package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 供应商实体
 *
 * 用于管理入库单的供应商信息
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "suppliers",
    indexes = {
        @Index(name = "idx_supplier_code", columnList = "code"),
        @Index(name = "idx_supplier_active", columnList = "is_active"),
        @Index(name = "idx_suppliers_company_active_deleted", columnList = "company_id,is_active,is_deleted")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_suppliers_company_code", columnNames = {"company_id", "code"})
    }
)
public class Supplier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 供应商编码（唯一）
     */
    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 50, message = "供应商编码长度不能超过50")
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /**
     * 供应商名称
     */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 200, message = "供应商名称长度不能超过200")
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * 联系人
     */
    @Size(max = 100, message = "联系人长度不能超过100")
    @Column(name = "contact", length = 100)
    private String contact;

    /**
     * 地址
     */
    @Size(max = 255, message = "地址长度不能超过255")
    @Column(name = "address", length = 255)
    private String address;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    @Column(name = "email", length = 100)
    private String email;

    /**
     * 电话
     */
    @Size(max = 50, message = "电话长度不能超过50")
    @Column(name = "phone", length = 50)
    private String phone;

    /**
     * 是否启用
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Optional master-data note.
     */
    @Size(max = 500, message = "供应商备注长度不能超过500")
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * Logical deletion is handled explicitly by SupplierService. The entity is
     * intentionally not globally restricted so historical inbound orders can
     * still resolve their supplier relationship for audit display.
     */
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
