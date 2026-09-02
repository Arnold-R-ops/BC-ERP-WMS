package com.wms.system.controller;

import com.wms.system.service.InventoryReservationService;
import com.wms.system.service.WarehouseScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = InventoryReservationOpenApiContractTest.TestApplication.class,
    properties = "springdoc.api-docs.enabled=true"
)
@AutoConfigureMockMvc
class InventoryReservationOpenApiContractTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private InventoryReservationService reservationService;
    @MockBean private WarehouseScopeService warehouseScopeService;

    @Test
    void generatedOpenApiDoesNotPublishSingleReservationRelease() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/inventory/reservations/{reservationId}/release']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/inventory/reservations'].get").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/reservations/batch/{batchId}'].get").exists())
            .andExpect(jsonPath(
                "$.components.schemas.InventoryReservationResponse.properties.warehouseId"
            ).doesNotExist());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        SecurityAutoConfiguration.class
    })
    @org.springframework.context.annotation.Import(InventoryReservationController.class)
    static class TestApplication {
    }
}
