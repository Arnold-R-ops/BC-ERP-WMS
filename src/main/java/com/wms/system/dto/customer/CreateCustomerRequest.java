package com.wms.system.dto.customer;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建客户请求 DTO
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
public class CreateCustomerRequest {

    /**
     * 客户编码（唯一标识）
     */
    @NotBlank(message = "客户编码不能为空")
    @Size(max = 50, message = "客户编码长度不能超过 50 个字符")
    private String code;

    /**
     * 客户名称
     */
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 200, message = "客户名称长度不能超过 200 个字符")
    private String name;

    /**
     * 联系人
     */
    @Size(max = 100, message = "联系人长度不能超过 100 个字符")
    private String contact;

    /**
     * 联系电话
     */
    @Size(max = 50, message = "联系电话长度不能超过 50 个字符")
    private String phone;

    /**
     * 电子邮箱
     */
    @Email(message = "电子邮箱格式不正确")
    @Size(max = 100, message = "电子邮箱长度不能超过 100 个字符")
    private String email;

    /**
     * 客户地址
     */
    @Size(max = 255, message = "客户地址长度不能超过 255 个字符")
    private String address;

    /**
     * 信用额度
     */
    @DecimalMin(value = "0.00", message = "信用额度不能为负数")
    private BigDecimal creditLimit;

    /**
     * 是否激活
     */
    private Boolean isActive;
}
