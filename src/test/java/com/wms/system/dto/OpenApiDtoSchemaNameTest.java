package com.wms.system.dto;

import com.wms.system.dto.integration.ShopifyReconciliationRepairResult;
import com.wms.system.dto.receiving.CreatePurchaseAsnRequest;
import com.wms.system.dto.receiving.InboundReferenceSkuResponse;
import com.wms.system.dto.receiving.InboundReferenceWarehouseResponse;
import com.wms.system.dto.receiving.PurchaseAsnResponse;
import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.ReceivingPlanResponse;
import com.wms.system.dto.receiving.ReceivingTaskResponse;
import com.wms.system.dto.receiving.ReceivingTaskLocationResponse;
import com.wms.system.dto.receiving.SavePurchaseOrderAllocationsRequest;
import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.dto.receiving.UpdatePurchaseAsnDraftRequest;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.sales.UpdateSalesOrderRequest;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDtoSchemaNameTest {

    @Test
    void nestedEndpointDtosHaveUniqueStableSchemaNamesAndReferences() {
        Map<String, Schema> schemas = new LinkedHashMap<>();
        for (Class<?> type : List.of(
            CreatePurchaseAsnRequest.class,
            InboundReferenceSkuResponse.class,
            InboundReferenceWarehouseResponse.class,
            UpdatePurchaseAsnDraftRequest.class,
            PurchaseAsnResponse.class,
            SavePurchaseOrderAllocationsRequest.class,
            ReceivingPlanResponse.class,
            ReceivingTaskResponse.class,
            ReceivingTaskLocationResponse.class,
            SubmitReceivingReceiptRequest.class,
            ReceiptEventResponse.class,
            ShopifyReconciliationRepairResult.class,
            CreateSalesOrderRequest.class,
            UpdateSalesOrderRequest.class
        )) {
            ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
            schemas.putAll(resolved.referencedSchemas);
        }

        assertThat(schemas).containsKeys(
            "CreatePurchaseAsnItemRequest",
            "UpdatePurchaseAsnDraftItemRequest",
            "PurchaseAsnItemResponse",
            "PurchaseOrderAllocationRowRequest",
            "ReceivingPlanAllocationItemResponse",
            "ReceivingTaskItemResponse",
            "ReceivingReceiptLineRequest",
            "ReceiptEventLineResponse",
            "ShopifyReconciliationRepairItem",
            "CreateSalesOrderItemRequest",
            "UpdateSalesOrderItemRequest"
        );
        assertThat(schemas).doesNotContainKeys("Item", "Line", "Row", "SalesOrderItemData");

        assertArrayItemReference(
            schemas.get("UpdatePurchaseAsnDraftRequest"), "items",
            "UpdatePurchaseAsnDraftItemRequest");
        assertArrayItemReference(
            schemas.get("SavePurchaseOrderAllocationsRequest"), "allocations",
            "PurchaseOrderAllocationRowRequest");

        assertArrayItemReference(
            schemas.get("PurchaseAsnResponse"), "items", "PurchaseAsnItemResponse");
        assertArrayItemReference(
            schemas.get("ReceivingPlanResponse"), "items",
            "ReceivingPlanAllocationItemResponse");
        assertArrayItemReference(
            schemas.get("ReceivingTaskResponse"), "items", "ReceivingTaskItemResponse");
        assertThat(schemas.get("ReceivingTaskItemResponse").getProperties())
            .containsKey("expiryManagementEnabled");
        assertArrayItemReference(
            schemas.get("SubmitReceivingReceiptRequest"), "lines",
            "ReceivingReceiptLineRequest");
        assertArrayItemReference(
            schemas.get("ReceiptEventResponse"), "lines", "ReceiptEventLineResponse");
    }

    private void assertArrayItemReference(
        Schema<?> owner,
        String property,
        String schemaName
    ) {
        assertThat(owner).isNotNull();
        assertThat(owner.getProperties()).containsKey(property);
        Schema<?> propertySchema = owner.getProperties().get(property);
        assertThat(propertySchema).isInstanceOf(ArraySchema.class);
        assertThat(((ArraySchema) propertySchema).getItems().get$ref())
            .isEqualTo("#/components/schemas/" + schemaName);
    }
}
