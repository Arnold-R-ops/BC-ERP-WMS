package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Excel 模板管理实体
 *
 * 用于管理系统中的 Excel 导入/导出模板
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
    name = "sys_excel_templates",
    indexes = {
        @Index(name = "idx_template_type_active", columnList = "template_type, is_active")
    }
)
public class SysExcelTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过100")
    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    /**
     * 模板类型（如 INBOUND_ORDER）
     */
    @NotBlank(message = "模板类型不能为空")
    @Size(max = 50, message = "模板类型长度不能超过50")
    @Column(name = "template_type", nullable = false, length = 50)
    private String templateType;

    /**
     * 模板文件 URL
     */
    @NotBlank(message = "模板文件URL不能为空")
    @Size(max = 500, message = "模板文件URL长度不能超过500")
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    /**
     * 是否启用
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * 版本号
     */
    @Size(max = 20, message = "版本号长度不能超过20")
    @Column(name = "version", length = 20)
    private String version;

    /**
     * 描述
     */
    @Size(max = 500, message = "描述长度不能超过500")
    @Column(name = "description", length = 500)
    private String description;
}
