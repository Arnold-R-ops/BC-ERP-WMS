package com.wms.platformverification;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.client.ClientHttpRequestFactory;
import java.util.*;

/** Runs before datasource creation; no product/default configuration is edited. */
public final class PlatformR2SpringInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static Map<String, Object> fixedProperties(Properties supplied) {
        var settings = PlatformR2Isolation.settings(supplied, false, PlatformR2Isolation.MAIN);
        Map<String, Object> values = new HashMap<>();
        values.put("spring.datasource.url", PlatformR2Isolation.url(settings.database()));
        values.put("spring.datasource.username", settings.role());
        values.put("spring.datasource.password", settings.password());
        values.put("spring.flyway.enabled", "false");
        values.put("spring.sql.init.mode", "never");
        values.put("spring.jpa.hibernate.ddl-auto", "validate");
        values.put("spring.devtools.restart.enabled", "false");
        values.put("spring.test.mockmvc.print", "NONE");
        values.put("spring.test.mockmvc.print-only-on-failure", "false");
        values.put("spring.jpa.open-in-view", "false");
        values.put("wms.platform-access.export-directory", "D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260919/r11/exports");
        return values;
    }
    @Override public void initialize(ConfigurableApplicationContext context) {
        // Reject a different externally injected destination, rather than silently masking it.
        for (String key : List.of("spring.datasource.url", "spring.flyway.url")) {
            String value = context.getEnvironment().getProperty(key);
            if (value != null && !PlatformR2Isolation.url(PlatformR2Isolation.MAIN).equals(value)) throw PlatformR2Isolation.refused();
        }
        if (context.getEnvironment().getProperty("spring.datasource.jndi-name") != null) throw PlatformR2Isolation.refused();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("r2-immutable-isolation", fixedProperties(PlatformR2Isolation.configuration())));
        context.addBeanFactoryPostProcessor(new BeanDefinitionRegistryPostProcessor() {
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                if (registry.containsBeanDefinition("dataSource")) registry.removeBeanDefinition("dataSource");
                var dataSource = new RootBeanDefinition(javax.sql.DataSource.class, PlatformR2Isolation::runtimeDataSource);
                dataSource.setPrimary(true); registry.registerBeanDefinition("dataSource", dataSource);
                String scheduled = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (registry.containsBeanDefinition(scheduled)) registry.removeBeanDefinition(scheduled);
            }
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) { }
        });
        // This regular factory processor also runs after configuration-class registry expansion.
        context.addBeanFactoryPostProcessor(factory -> {
            String scheduled = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
            if (factory instanceof BeanDefinitionRegistry registry && registry.containsBeanDefinition(scheduled)) registry.removeBeanDefinition(scheduled);
        });
        // No real SMTP/third-party HTTP. Mockito mail beans in existing cases remain usable for fault injection.
        context.getBeanFactory().addBeanPostProcessor(new org.springframework.beans.factory.config.BeanPostProcessor() {
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof org.springframework.web.client.RestTemplate template) {
                    template.setRequestFactory((uri, method) -> { throw new java.io.IOException("External HTTP disabled in R2 platform isolation"); });
                }
                if (bean instanceof org.springframework.mail.javamail.JavaMailSender && !org.mockito.Mockito.mockingDetails(bean).isMock()) {
                    return org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);
                }
                return bean;
            }
        });
    }
}
