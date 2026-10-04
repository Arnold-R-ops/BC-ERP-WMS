package com.wms.system.uat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP/security workflow for the governed A2 tenant.
 *
 * <p>The production tenancy switch intentionally remains locked. This test
 * supplies the same server-owned tenant context that the future trusted Host
 * resolver supplies, then exercises authentication, authorization, RLS,
 * purchase approval, ASN, unified receiving, inspection review and controlled
 * other inbound against the isolated PostgreSQL database.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "A2_UAT_HTTP", matches = "true")
class A2UatGovernedBusinessFlowTest {

    private static final long TENANT_A_ID = 1L;
    private static final long TENANT_B_ID = 2L;
    private static final String TENANT_A_SLUG = "a2-ordinary";
    private static final String TENANT_B_SLUG = "a2-optional";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureIsolatedDatabase(DynamicPropertyRegistry registry) {
        String url = required("A2_UAT_DB_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/wms_a2_uat_[0-9]{8}.*")) {
            throw new IllegalStateException("A2 HTTP UAT refuses a non-isolated database URL");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> required("A2_UAT_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("A2_UAT_DB_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("jwt.tenant-secret", () ->
            "dGVzdC10ZW5hbnQtand0LXNlY3JldC1rZXktZm9yLWludGVncmF0aW9uLXRlc3Rpbmctb25seS0yMDI2");
        registry.add("jwt.platform-secret", () ->
            "dGVzdC1wbGF0Zm9ybS1qd3Qtc2VjcmV0LWtleS1mb3ItaW50ZWdyYXRpb24tdGVzdGluZy1vbmx5LTIwMjY=");
        registry.add("batch.hashids.salt", () -> "A2_ISOLATED_UAT_BATCH_SALT");
        registry.add("wms.tenancy.enabled", () -> "false");
        registry.add("wms.tenancy.require-safe-database-role", () -> "true");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("logging.level.org.hibernate.SQL", () -> "OFF");
        registry.add("logging.level.com.wms.system.security.DynamicAuthorizationManager", () -> "WARN");
        registry.add("spring.test.mockmvc.print", () -> "NONE");
    }

    @Test
    void governedTenantCompletesPurchaseInspectionAndOtherInboundWithoutCrossTenantAccess()
            throws Exception {
        String purchaser = login(TENANT_B_ID, TENANT_B_SLUG, "a2b-purchaser", "PURCHASER");
        String worker = login(TENANT_B_ID, TENANT_B_SLUG, "a2b-worker", "WAREHOUSE_STAFF");
        String reviewer = login(TENANT_B_ID, TENANT_B_SLUG, "a2b-reviewer", "QUALITY_REVIEWER");

        status(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/purchase-orders", worker), 403);
        status(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/suppliers", worker), 403);

        JsonNode supplier = first(request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/suppliers", purchaser), 200));
        JsonNode skus = request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/purchase-orders/reference/skus", purchaser), 200);
        JsonNode warehouses = request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/purchase-orders/reference/warehouses", purchaser), 200);
        JsonNode inspectionSku = find(skus, "skuCode", "A2-INSPECTION");
        JsonNode normalSku = find(skus, "skuCode", "A2-NORMAL-VISUAL");
        JsonNode warehouse = first(warehouses);

        Map<String, Object> purchaseBody = new LinkedHashMap<>();
        purchaseBody.put("supplierId", supplier.path("id").asLong());
        purchaseBody.put("expectedDate", LocalDate.now().plusDays(7).toString());
        purchaseBody.put("remark", "A2-2 governed purchase " + UUID.randomUUID());
        purchaseBody.put("items", List.of(Map.of(
            "productSkuId", inspectionSku.path("id").asLong(),
            "orderedQuantity", 10,
            "unitCost", 9.50,
            "externalBatchCode", "A2B-INSPECTION-001",
            "remark", "inspection review path")));
        JsonNode purchase = request(TENANT_B_ID, TENANT_B_SLUG,
            json(MockMvcRequestBuilders.post("/api/purchase-orders"), purchaser, purchaseBody), 201);
        assertThat(purchase.path("status").asText()).isEqualTo("DRAFT");

        JsonNode purchaseItem = first(purchase.path("items"));
        Map<String, Object> allocationBody = Map.of(
            "purchaseOrderVersion", purchase.path("version").asLong(),
            "allocations", List.of(Map.of(
                "purchaseOrderItemId", purchaseItem.path("id").asLong(),
                "warehouseId", warehouse.path("id").asLong(),
                "plannedQuantity", 10)));
        request(TENANT_B_ID, TENANT_B_SLUG,
            json(MockMvcRequestBuilders.put("/api/purchase-orders/{id}/allocations",
                purchase.path("id").asLong()), purchaser, allocationBody), 200);
        purchase = request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/purchase-orders/" + purchase.path("id").asLong(), purchaser), 200);
        JsonNode submitted = request(TENANT_B_ID, TENANT_B_SLUG,
            json(MockMvcRequestBuilders.post("/api/purchase-orders/{id}/submit",
                purchase.path("id").asLong()), purchaser,
                Map.of("version", purchase.path("version").asLong(), "reason", "A2 governed submit")), 200);
        assertThat(submitted.path("status").asText()).isEqualTo("PENDING_APPROVAL");

        JsonNode approved = request(TENANT_B_ID, TENANT_B_SLUG,
            json(MockMvcRequestBuilders.post("/api/purchase-orders/{id}/approve",
                purchase.path("id").asLong()), reviewer,
                Map.of("version", submitted.path("version").asLong(), "reason", "A2 governed approval")), 200);
        assertThat(approved.path("status").asText()).isEqualTo("AWAITING_SHIPMENT");

        Map<String, Object> asnBody = new LinkedHashMap<>();
        asnBody.put("warehouseId", warehouse.path("id").asLong());
        asnBody.put("supplierShipmentNo", "A2B-SHIP-" + UUID.randomUUID());
        asnBody.put("expectedArrivalDate", LocalDate.now().plusDays(1).toString());
        asnBody.put("remark", "A2 governed quick confirm");
        asnBody.put("items", List.of(Map.of(
            "purchaseOrderItemId", purchaseItem.path("id").asLong(),
            "shippedQuantity", 10,
            "externalBatchCode", "A2B-INSPECTION-001",
            "remark", "inspection line")));
        JsonNode asn = request(TENANT_B_ID, TENANT_B_SLUG,
            json(MockMvcRequestBuilders.post("/api/purchase-orders/{id}/asns/quick-confirm",
                purchase.path("id").asLong()), purchaser, asnBody), 201);
        assertThat(asn.path("status").asText()).isEqualTo("CONFIRMED");

        JsonNode tasks = request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/receiving-tasks", worker), 200);
        JsonNode task = find(tasks, "purchaseOrderId", purchase.path("id").asLong());
        assertThat(task.path("warehouseId").asLong()).isEqualTo(warehouse.path("id").asLong());
        assertThat(task.has("supplier")).isFalse();
        JsonNode taskLine = first(task.path("items"));
        JsonNode location = first(request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/receiving-tasks/" + task.path("id").asLong() + "/locations", worker), 200));

        String receiptKey = "a2-b-receipt-" + UUID.randomUUID();
        Map<String, Object> receiptBody = Map.of(
            "remark", "A2 governed receipt",
            "lines", List.of(Map.of(
                "receivingTaskItemId", taskLine.path("id").asLong(),
                "locationId", location.path("id").asLong(),
                "quantity", 10,
                "externalBatchCode", "A2B-INSPECTION-001")));
        JsonNode receipt = request(TENANT_B_ID, TENANT_B_SLUG,
            idempotentJson(MockMvcRequestBuilders.post("/api/receiving-tasks/{id}/receipts",
                task.path("id").asLong()), worker, receiptKey, receiptBody), 200);
        JsonNode replay = request(TENANT_B_ID, TENANT_B_SLUG,
            idempotentJson(MockMvcRequestBuilders.post("/api/receiving-tasks/{id}/receipts",
                task.path("id").asLong()), worker, receiptKey, receiptBody), 200);
        assertThat(replay.path("id").asLong()).isEqualTo(receipt.path("id").asLong());
        assertThat(first(receipt.path("lines")).path("qualityStatusAtReceipt").asText())
            .isEqualTo("PENDING_INSPECTION");

        JsonNode inspections = request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/receiving-inspections", worker), 200);
        JsonNode inspection = find(inspections, "sourceReference", purchase.path("poNumber").asText());
        JsonNode inspectionLine = first(inspection.path("lines"));
        Map<String, Object> inspectionBody = Map.of(
            "inspectionMethod", "FULL",
            "defectQuantity", 0,
            "reasonCode", "INSPECTION_PASSED",
            "reasonDetail", "A2 governed full inspection passed",
            "lines", List.of(Map.of(
                "inspectionTaskLineId", inspectionLine.path("id").asLong(),
                "passedQuantity", 10,
                "quarantinedQuantity", 0)));
        JsonNode inspectionResult = request(TENANT_B_ID, TENANT_B_SLUG,
            idempotentJson(MockMvcRequestBuilders.post("/api/receiving-inspections/{id}/results",
                inspection.path("id").asLong()), worker,
                "a2-b-inspect-" + UUID.randomUUID(), inspectionBody), 201);
        assertThat(inspectionResult.path("status").asText()).isEqualTo("PENDING_REVIEW");
        JsonNode reviewed = request(TENANT_B_ID, TENANT_B_SLUG,
            idempotentJson(MockMvcRequestBuilders.post("/api/receiving-inspections/results/{id}/approve",
                inspectionResult.path("id").asLong()), reviewer,
                "a2-b-inspect-review-" + UUID.randomUUID(),
                Map.of("comment", "A2 governed inspection approved")), 200);
        assertThat(reviewed.path("status").asText()).isEqualTo("APPROVED");

        JsonNode otherInboundPolicy = request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/other-inbound-policy", purchaser), 200);
        assertThat(otherInboundPolicy.path("enabled").asBoolean()).isTrue();
        Map<String, Object> otherInboundBody = Map.of(
            "businessType", "FREE_SAMPLE",
            "sourceReference", "A2B-SAMPLE-" + UUID.randomUUID(),
            "businessReason", "A2 governed free-sample UAT",
            "warehouseId", warehouse.path("id").asLong(),
            "remark", "A2-2 other inbound",
            "items", List.of(Map.of(
                "productSkuId", normalSku.path("id").asLong(),
                "plannedQuantity", 5,
                "externalBatchCode", "A2B-SAMPLE-001",
                "remark", "controlled non-purchase receipt")));
        JsonNode application = request(TENANT_B_ID, TENANT_B_SLUG,
            json(MockMvcRequestBuilders.post("/api/other-inbound-applications"),
                purchaser, otherInboundBody), 201);
        JsonNode confirmedApplication = request(TENANT_B_ID, TENANT_B_SLUG,
            json(MockMvcRequestBuilders.post("/api/other-inbound-applications/{id}/confirm",
                application.path("id").asLong()), purchaser,
                Map.of("version", application.path("version").asLong())), 200);
        assertThat(confirmedApplication.path("status").asText()).isEqualTo("CONFIRMED");
        JsonNode otherTask = find(request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/receiving-tasks", worker), 200),
            "otherInboundApplicationId", application.path("id").asLong());
        JsonNode otherLine = first(otherTask.path("items"));
        request(TENANT_B_ID, TENANT_B_SLUG,
            idempotentJson(MockMvcRequestBuilders.post("/api/receiving-tasks/{id}/receipts",
                otherTask.path("id").asLong()), worker,
                "a2-b-other-receipt-" + UUID.randomUUID(),
                Map.of("remark", "A2 governed other inbound receipt", "lines", List.of(Map.of(
                    "receivingTaskItemId", otherLine.path("id").asLong(),
                    "locationId", location.path("id").asLong(),
                    "quantity", 5,
                    "externalBatchCode", "A2B-SAMPLE-001")))), 200);
        JsonNode completedApplication = request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/other-inbound-applications/" + application.path("id").asLong(), purchaser), 200);
        assertThat(completedApplication.path("status").asText()).isEqualTo("COMPLETED");

        JsonNode ownPurchases = request(TENANT_B_ID, TENANT_B_SLUG,
            get("/api/purchase-orders", purchaser), 200);
        assertThat(values(ownPurchases, "id")).contains(purchase.path("id").asLong());
        status(TENANT_A_ID, TENANT_A_SLUG,
            get("/api/purchase-orders", purchaser), 401);
    }

    @Test
    void ordinarySalesUserCanListOnlyOwnedCustomers() throws Exception {
        String sales = login(TENANT_A_ID, TENANT_A_SLUG, "a2a-sales", "SALESPERSON");

        JsonNode customers = request(TENANT_A_ID, TENANT_A_SLUG,
            get("/api/customers?activeOnly=true", sales), 200);

        assertThat(customers.isArray()).isTrue();
        assertThat(customers.size()).isEqualTo(1);
        assertThat(customers.get(0).path("code").asText()).isEqualTo("A2-CUSTOMER");
    }

    private String login(long companyId, String slug, String username, String role) throws Exception {
        JsonNode response = request(companyId, slug,
            json(MockMvcRequestBuilders.post("/api/auth/login"), null,
                Map.of("username", username,
                    "password", required("A2_UAT_STAFF_PASSWORD_PREFIX") + username)), 200);
        assertThat(response.path("currentRole").asText()).isEqualTo(role);
        assertThat(response.path("mustChangePassword").asBoolean()).isFalse();
        assertThat(response.path("permissionCodes").isArray()).isTrue();
        return response.path("token").asText();
    }

    private JsonNode request(long companyId, String slug, RequestBuilder builder, int expectedStatus)
            throws Exception {
        TenantContextHolder.set(context(companyId, slug));
        try {
            String json = mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
            return json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void status(long companyId, String slug, RequestBuilder builder, int expectedStatus)
            throws Exception {
        request(companyId, slug, builder, expectedStatus);
    }

    private MockHttpServletRequestBuilder get(String path, String token) {
        return MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(body));
    }

    private MockHttpServletRequestBuilder idempotentJson(
            MockHttpServletRequestBuilder builder, String token, String key, Object body) throws Exception {
        return json(builder.header("Idempotency-Key", key), token, body);
    }

    private TenantContext context(long id, String slug) {
        return new TenantContext(id, slug, slug + ".bcwms.com", RequestSurface.TENANT,
            TenantStatus.ACTIVE);
    }

    private JsonNode first(JsonNode array) {
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isPositive();
        return array.get(0);
    }

    private JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) return node;
        }
        throw new AssertionError("No row with " + field + "=" + value + " in " + array);
    }

    private JsonNode find(JsonNode array, String field, long value) {
        for (JsonNode node : array) {
            if (node.path(field).asLong(Long.MIN_VALUE) == value) return node;
        }
        throw new AssertionError("No row with " + field + "=" + value + " in " + array);
    }

    private List<Long> values(JsonNode array, String field) {
        List<Long> result = new ArrayList<>();
        array.forEach(node -> result.add(node.path(field).asLong()));
        return result;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for explicit A2 UAT");
        }
        return value;
    }
}
