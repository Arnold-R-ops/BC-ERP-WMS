package com.wms.system.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置（P2 前端交接包）
 *
 * 用途：
 * - /v3/api-docs      机器可读契约（前端据此生成 TypeScript 类型与请求层）
 * - /swagger-ui.html  在线调试页（点 Authorize 填 JWT 即可试调所有接口）
 *
 * 安全：生产 profile 通过 springdoc.api-docs.enabled=false 整体关闭
 * （见 application-prod.yml），接口结构不对外暴露。
 *
 * @author WMS Team
 * @since 2026-07-12
 * @version P2-FE (Frontend Handover)
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI wmsOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("2G WMS API")
                .description("智能仓储管理系统接口契约。认证：POST /api/auth/login 获取 JWT，"
                    + "后续请求携带 Authorization: Bearer {token}。"
                    + "登录响应中 mustChangePassword=true 时，除认证与改密接口外全部被拦截。"
                    + "请求鉴权只使用 JWT 的 currentRole 及其启用的继承角色，不合并其他已分配角色。"
                    + "角色切换以及账号、密码、角色或权限变更会使旧 JWT 立即失效；"
                    + "客户端收到 401/403 后必须重新登录或使用角色切换返回的新令牌。")
                .version("P1"))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
