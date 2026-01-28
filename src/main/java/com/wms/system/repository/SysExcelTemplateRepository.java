package com.wms.system.repository;

import com.wms.system.entity.SysExcelTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Excel 模板数据访问接口
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Repository
public interface SysExcelTemplateRepository extends JpaRepository<SysExcelTemplate, Long> {

    /**
     * 根据模板类型查询启用的模板
     */
    Optional<SysExcelTemplate> findByTemplateTypeAndIsActiveTrue(String templateType);

    /**
     * 根据模板类型查询所有模板
     */
    List<SysExcelTemplate> findByTemplateType(String templateType);

    /**
     * 查询所有启用的模板
     */
    List<SysExcelTemplate> findByIsActiveTrue();
}
