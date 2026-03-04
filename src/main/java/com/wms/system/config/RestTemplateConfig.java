package com.wms.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 *
 * 用于 Shopify API 集成和其他 HTTP 客户端调用
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建 RestTemplate Bean
     *
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
