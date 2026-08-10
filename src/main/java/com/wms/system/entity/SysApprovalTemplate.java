package com.wms.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Protected approval-template metadata. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_approval_template")
public class SysApprovalTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "object_type", nullable = false, length = 50)
    private String objectType;

    @Column(name = "approval_mode", nullable = false, length = 20)
    private String approvalMode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "system_defined", nullable = false)
    private Boolean systemDefined;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(length = 500)
    private String description;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;
}
