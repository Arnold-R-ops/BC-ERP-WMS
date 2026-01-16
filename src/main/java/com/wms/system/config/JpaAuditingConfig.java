package com.wms.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 审计配置类
 * 启用 Spring Data JPA 的审计功能，自动管理实体的时间戳字段
 *
 * 功能说明：
 * - @EnableJpaAuditing: 启用 JPA 审计
 * - @CreatedDate: 实体首次保存时自动填充
 * - @LastModifiedDate: 实体每次更新时自动刷新
 *
 * 使用方式：
 * 1. 实体类继承 BaseEntity（包含 createdAt 和 updatedAt 字段）
 * 2. 实体类添加 @EntityListeners(AuditingEntityListener.class)
 * 3. 启动类扫描到此配置类后，审计功能自动生效
 *
 * 扩展功能（可选）：
 * - 如果需要自动记录"创建人"和"修改人"，可以实现 AuditorAware<String> 接口
 * - 然后在配置类中添加 @EnableJpaAuditing(auditorAwareRef = "auditorProvider")
 *
 * @author WMS Team
 * @since 2025-01-09
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // 配置类本身无需额外代码，@EnableJpaAuditing 注解会自动启用审计功能
}
