package com.wms.system.dto.customer;

import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户响应 DTO
 *
 * V3.7 架构：客户管理
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    /**
     * 客户ID
     */
    private Long id;

    /**
     * 客户编码
     */
    private String code;

    /**
     * 客户名称
     */
    private String name;

    private CustomerType customerType;

    private CustomerSource source;

    private String externalCustomerId;

    /**
     * 联系人
     */
    private String contact;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 电子邮箱
     */
    private String email;

    private String normalizedEmail;

    /**
     * 客户地址
     */
    private String address;

    /**
     * 信用额度
     */
    private BigDecimal creditLimit;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
