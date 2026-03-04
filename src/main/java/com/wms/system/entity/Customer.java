package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * 客户实体
 *
 * V3.7 架构：客户管理模块
 *
 * 说明：
 * - 存储客户基本信息
 * - 支持信用额度管理
 * - 支持客户启用/禁用
 *
 * 核心字段：
 * - code: 客户编码（唯一标识）
 * - name: 客户名称
 * - creditLimit: 信用额度（预留 ERP 扩展）
 * - isActive: 是否激活
 *
 * 业务场景：
 * 1. 销售订单关联客户
 * 2. 客户信用额度检查（预留）
 * 3. 客户统计分析（预留）
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
@SQLDelete(sql = "UPDATE customers SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Table(
    name = "customers",
    indexes = {
        @Index(name = "idx_customer_code", columnList = "code", unique = true),
        @Index(name = "idx_customer_active", columnList = "is_active"),
        @Index(name = "idx_customer_owner", columnList = "owner_id")
    }
)
public class Customer extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 客户编码（唯一标识）
     *
     * 说明：
     * - 用于快速识别客户
     * - 可以是自定义编码或系统生成
     *
     * 示例：
     * - CUST001
     * - C20260128001
     */
    @NotBlank(message = "客户编码不能为空")
    @Size(max = 50, message = "客户编码长度不能超过 50 个字符")
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * 客户名称
     */
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 200, message = "客户名称长度不能超过 200 个字符")
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 联系人
     */
    @Size(max = 100, message = "联系人长度不能超过 100 个字符")
    @Column(length = 100)
    private String contact;

    /**
     * 联系电话
     */
    @Size(max = 50, message = "联系电话长度不能超过 50 个字符")
    @Column(length = 50)
    private String phone;

    /**
     * 电子邮箱
     */
    @Email(message = "电子邮箱格式不正确")
    @Size(max = 100, message = "电子邮箱长度不能超过 100 个字符")
    @Column(length = 100)
    private String email;

    /**
     * 客户地址
     */
    @Size(max = 255, message = "客户地址长度不能超过 255 个字符")
    @Column(length = 255)
    private String address;

    /**
     * 信用额度（预留 ERP 扩展）
     *
     * 说明：
     * - 客户可赊账的最大金额
     * - 用于信用额度检查（未来扩展）
     * - 默认值：0.00（不允许赊账）
     *
     * 示例：
     * - VIP 客户：creditLimit = 100000.00
     * - 普通客户：creditLimit = 50000.00
     * - 新客户：creditLimit = 0.00（现金交易）
     */
    @DecimalMin(value = "0.00", message = "信用额度不能为负数")
    @Column(name = "credit_limit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    /**
     * 是否激活
     *
     * 说明：
     * - true: 正常客户，可以创建销售订单
     * - false: 已禁用，不允许创建销售订单
     *
     * 使用场景：
     * - 客户违约或欠款时，可以禁用客户
     * - 客户注销时，可以禁用客户
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 客户归属人ID（V4.1 数据安全）
     *
     * 说明：
     * - 记录客户归属的销售员或创建人
     * - 用于行级隔离（Row-Level Security）
     * - 销售员只能查看和管理自己的客户
     * - 管理员和经理可以查看所有客户
     *
     * 业务场景：
     * - 销售员创建客户时，自动设置为当前用户
     * - 管理员可以将客户分配给其他销售员
     * - 保护客户资产，防止数据泄露
     *
     * @since V4.1 (Customer Data Security)
     */
    @Column(name = "owner_id")
    private Long ownerId;

    // ========== V4.2 逻辑删除 (Soft Delete) ==========

    /**
     * 逻辑删除标记（V4.2 新增）
     *
     * 说明：
     * - true: 已逻辑删除（不会出现在任何查询结果中）
     * - false: 正常状态
     *
     * 技术实现：
     * - @SQLDelete 拦截 JPA delete 操作，改写为 UPDATE SET is_deleted = true
     * - @SQLRestriction 自动在所有查询中追加 WHERE is_deleted = false
     * - 数据永久保留于数据库，支持 BI 分析和数据恢复
     *
     * @since V4.2 (AI Foundation Patch)
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;
}
