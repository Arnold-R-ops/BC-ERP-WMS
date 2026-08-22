package com.wms.system.tenant.config;

import com.wms.system.security.JwtAuthenticationFilter;
import com.wms.system.tenant.web.TenantResolutionFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantFilterRegistrationConfig {

    @Bean
    FilterRegistrationBean<TenantResolutionFilter> disableTenantFilterContainerRegistration(
        TenantResolutionFilter filter
    ) {
        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterContainerRegistration(
        JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
