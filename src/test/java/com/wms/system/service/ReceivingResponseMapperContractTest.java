package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.system.dto.receiving.ReceivingTaskResponse;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.ReceivingTaskItem;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivingResponseMapperContractTest {

    @Test
    void warehouseContractNeverLeaksProcurementOrShipmentSecrets() throws Exception {
        PurchaseOrder order = PurchaseOrder.builder()
            .id(1L).poNumber("PO-1").supplier("SECRET-SUPPLIER").build();
        ReceivingPlan plan = ReceivingPlan.builder().purchaseOrder(order).build();
        ReceivingTask task = ReceivingTask.builder()
            .id(2L).taskNumber("RCV-2").status(ReceivingTaskStatus.PENDING)
            .receivingPlan(plan)
            .warehouse(Warehouse.builder().id(3L).code("WH-1").name("Main").build())
            .build();
        ProductSku expiryManagedSku = ProductSku.builder()
            .id(4L).skuCode("SKU-4").name("Expiry managed")
            .expiryManagementEnabled(true).build();
        task.addItem(ReceivingTaskItem.builder()
            .id(5L).productSku(expiryManagedSku).expectedQuantity(2).build());
        ReceivingTaskResponse response = new ReceivingResponseMapper().toTask(task);
        String json = new ObjectMapper().registerModule(new JavaTimeModule())
            .writeValueAsString(response).toLowerCase();

        assertFalse(json.contains("supplier"));
        assertFalse(json.contains("price"));
        assertFalse(json.contains("carrier"));
        assertFalse(json.contains("tracking"));
        assertFalse(json.contains("asnnumber"));
        assertTrue(response.items().get(0).expiryManagementEnabled());
    }
}
