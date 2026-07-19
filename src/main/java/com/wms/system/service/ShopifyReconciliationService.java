package com.wms.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.integration.ShopifyReconciliationRepairResult;
import com.wms.system.dto.integration.ShopifyReconciliationReport;
import com.wms.system.dto.integration.ShopifyReconciliationRequest;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.sales.SalesOrderResponse;
import com.wms.system.dto.shopify.ShopifyLineItemDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.ChannelRawEvent;
import com.wms.system.entity.ChannelSkuMapping;
import com.wms.system.entity.Customer;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.integration.ShopifyApiClient;
import com.wms.system.repository.ChannelSkuMappingRepository;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.IntegrationConfigRepository;
import com.wms.system.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyReconciliationService {

    private static final String CHANNEL = "SHOPIFY";

    private final IntegrationConfigRepository integrationConfigRepository;
    private final ShopifyApiClient shopifyApiClient;
    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final ChannelSkuMappingRepository channelSkuMappingRepository;
    private final ChannelRawEventService rawEventService;
    private final SalesSubmissionService salesSubmissionService;
    private final ObjectMapper objectMapper;

    /**
     * Remote read + audit report only. No customer, product, reservation,
     * outbound task or Shopify data is changed by this operation.
     */
    public ShopifyReconciliationReport reconcile(ShopifyReconciliationRequest request) {
        IntegrationConfig config = requireActiveShopifyConfig(request.getConfigId());
        int days = request.getDays() == null ? 3 : request.getDays();
        OffsetDateTime updatedAtMin = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        JsonNode orders = readOrders(
            shopifyApiClient.fetchOrdersForReconciliationRaw(config, updatedAtMin)
        );

        List<JsonNode> remoteOrders = new ArrayList<>();
        orders.forEach(remoteOrders::add);
        List<String> remoteIds = remoteOrders.stream()
            .map(order -> order.path("id").asText(null))
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Set<String> existingIds = remoteIds.isEmpty()
            ? Set.of()
            : new HashSet<>(salesOrderRepository.findExistingExternalOrderIds(CHANNEL, remoteIds));

        List<ShopifyReconciliationReport.MissingOrder> missingOrders = remoteOrders.stream()
            .filter(order -> {
                String externalId = order.path("id").asText(null);
                return externalId != null && !existingIds.contains(externalId);
            })
            .map(order -> toMissingOrder(config, order))
            .toList();

        ShopifyReconciliationReport report = ShopifyReconciliationReport.builder()
            .configId(config.getId())
            .storeIdentifier(config.getStoreUrl())
            .windowDays(days)
            .updatedAtMin(updatedAtMin)
            .localOrderCount(salesOrderRepository.countByChannelAndExternalOrderIdIsNotNull(CHANNEL))
            .remoteOrderCount(remoteIds.size())
            .missingOrderCount(missingOrders.size())
            .reportOnly(true)
            .missingOrders(missingOrders)
            .build();

        try {
            ChannelRawEvent reportEvent = rawEventService.record(
                CHANNEL,
                config.getStoreUrl(),
                ChannelRawEvent.SOURCE_RECONCILE,
                ChannelRawEvent.TYPE_RECONCILE_REPORT,
                UUID.randomUUID().toString(),
                objectMapper.writeValueAsString(report)
            );
            rawEventService.markProcessed(reportEvent.getId());
            report.setReportEventId(reportEvent.getId());
        } catch (Exception e) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("message", "Unable to persist reconciliation report", "error", e.getMessage())
            );
        }

        return report;
    }

    public List<ShopifyReconciliationReport> reconcileAllActive(int days) {
        return integrationConfigRepository.findByPlatformAndIsActiveTrue(CHANNEL).stream()
            .map(config -> {
                ShopifyReconciliationRequest request = new ShopifyReconciliationRequest();
                request.setConfigId(config.getId());
                request.setDays(days);
                return reconcile(request);
            })
            .toList();
    }

    /**
     * Explicitly repairs selected reconciliation events. The capability is
     * intentionally narrow: existing customer + existing active SKU mappings
     * are required, and the resulting order is always PENDING_APPROVAL.
     */
    public ShopifyReconciliationRepairResult repair(
        List<Long> rawEventIds,
        Long operatorId,
        String operatorName
    ) {
        List<ShopifyReconciliationRepairResult.Item> items = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int blocked = 0;

        for (Long eventId : new LinkedHashSet<>(rawEventIds)) {
            ChannelRawEvent event;
            try {
                event = requireRepairCandidate(eventId);
            } catch (Exception e) {
                items.add(blockedRepairItem(eventId, e.getMessage()));
                blocked++;
                continue;
            }

            try {
                String externalId = event.getExternalId();
                Optional<com.wms.system.entity.SalesOrder> existing =
                    salesOrderRepository.findByExternalOrderId(externalId);
                if (existing.isPresent()) {
                    rawEventService.markSkipped(eventId, "Order already exists locally");
                    items.add(repairItem(event, "SKIPPED", existing.get().getId(), "订单已存在"));
                    skipped++;
                    continue;
                }

                JsonNode orderNode = objectMapper.readTree(event.getPayload());
                if (!isRepairEligible(orderNode)) {
                    throw new BusinessException(
                        ErrorKeys.OPERATION_NOT_ALLOWED,
                        Map.of("message", repairIneligibleReason(orderNode))
                    );
                }

                ShopifyOrderDto order = objectMapper.treeToValue(orderNode, ShopifyOrderDto.class);
                Customer customer = requireExistingCustomer(order);
                CreateSalesOrderRequest request = buildRestrictedOrderRequest(order, customer);
                SalesOrderResponse response = salesSubmissionService.createChannelOrderPendingApproval(
                    request,
                    operatorId,
                    operatorName,
                    CHANNEL,
                    externalId,
                    order.getName()
                );

                rawEventService.markProcessed(eventId);
                items.add(repairItem(event, "CREATED", response.getId(), "已创建待审批销售订单"));
                created++;
            } catch (Exception e) {
                rawEventService.markFailed(eventId, e.getMessage());
                items.add(repairItem(event, "BLOCKED", null, e.getMessage()));
                blocked++;
            }
        }

        return ShopifyReconciliationRepairResult.builder()
            .requested(new LinkedHashSet<>(rawEventIds).size())
            .created(created)
            .skipped(skipped)
            .blocked(blocked)
            .items(items)
            .build();
    }

    private ShopifyReconciliationRepairResult.Item blockedRepairItem(Long eventId, String message) {
        return ShopifyReconciliationRepairResult.Item.builder()
            .rawEventId(eventId)
            .status("BLOCKED")
            .message(message)
            .build();
    }

    private ShopifyReconciliationReport.MissingOrder toMissingOrder(
        IntegrationConfig config,
        JsonNode order
    ) {
        String externalId = order.path("id").asText();
        ChannelRawEvent event = rawEventService.findLatest(
                CHANNEL,
                config.getStoreUrl(),
                ChannelRawEvent.SOURCE_RECONCILE,
                ChannelRawEvent.TYPE_ORDER,
                externalId
            )
            .orElseGet(() -> rawEventService.record(
                CHANNEL,
                config.getStoreUrl(),
                ChannelRawEvent.SOURCE_RECONCILE,
                ChannelRawEvent.TYPE_ORDER,
                externalId,
                order.toString()
            ));

        return ShopifyReconciliationReport.MissingOrder.builder()
            .rawEventId(event.getId())
            .externalOrderId(externalId)
            .externalOrderNo(order.path("name").asText(null))
            .financialStatus(order.path("financial_status").asText(null))
            .fulfillmentStatus(order.path("fulfillment_status").asText(null))
            .repairEligible(isRepairEligible(order))
            .reason(repairIneligibleReason(order))
            .build();
    }

    private ChannelRawEvent requireRepairCandidate(Long eventId) {
        ChannelRawEvent event = rawEventService.requireById(eventId);
        if (!CHANNEL.equals(event.getChannel())
            || !ChannelRawEvent.SOURCE_RECONCILE.equals(event.getSource())
            || !ChannelRawEvent.TYPE_ORDER.equals(event.getEventType())) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("message", "Only Shopify order events created by reconciliation can be repaired")
            );
        }
        return event;
    }

    private Customer requireExistingCustomer(ShopifyOrderDto order) {
        if (!StringUtils.hasText(order.getEmail())) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("message", "订单没有客户邮箱，禁止自动创建客户")
            );
        }
        return customerRepository.findByEmail(order.getEmail())
            .filter(customer -> Boolean.TRUE.equals(customer.getIsActive()))
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("message", "未匹配到已存在且启用的客户，禁止自动创建客户", "email", order.getEmail())
            ));
    }

    private CreateSalesOrderRequest buildRestrictedOrderRequest(ShopifyOrderDto order, Customer customer) {
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("message", "订单没有商品明细"));
        }

        List<CreateSalesOrderRequest.SalesOrderItemData> items = new ArrayList<>();
        for (ShopifyLineItemDto lineItem : order.getLineItems()) {
            if (!StringUtils.hasText(lineItem.getSku())) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of("message", "订单包含无 SKU 明细，补单不允许创建商品或映射", "lineItem", lineItem.getName())
                );
            }

            ChannelSkuMapping mapping = findExistingMapping(lineItem.getSku());
            if (ChannelSkuMapping.TYPE_VIRTUAL.equals(mapping.getMappingType())) {
                continue;
            }

            ProductSku sku = mapping.getProductSku();
            if (sku == null || !Boolean.TRUE.equals(sku.getEnabled())) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of("message", "SKU 映射未指向启用的既有商品", "externalSku", lineItem.getSku())
                );
            }

            int ratio = mapping.getQuantityRatio() == null ? 1 : mapping.getQuantityRatio();
            int channelQuantity = lineItem.getQuantity() == null ? 0 : lineItem.getQuantity();
            if (ratio <= 0 || channelQuantity <= 0) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of("message", "SKU 数量或换算比无效", "externalSku", lineItem.getSku())
                );
            }

            BigDecimal channelPrice = parsePrice(lineItem.getPrice());
            CreateSalesOrderRequest.SalesOrderItemData item = new CreateSalesOrderRequest.SalesOrderItemData();
            item.setProductSkuId(sku.getId());
            item.setQuantity(channelQuantity * ratio);
            item.setUnitPrice(ratio == 1
                ? channelPrice
                : channelPrice.divide(BigDecimal.valueOf(ratio), 2, RoundingMode.HALF_UP));
            item.setRejectNearExpiry(false);
            item.setRemark("Shopify 对账补单: " + order.getName());
            items.add(item);
        }

        if (items.isEmpty()) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("message", "订单没有可履约的既有商品明细")
            );
        }

        CreateSalesOrderRequest request = new CreateSalesOrderRequest();
        request.setCustomerId(customer.getId());
        request.setChannel(CHANNEL);
        request.setItems(items);
        return request;
    }

    private ChannelSkuMapping findExistingMapping(String externalSku) {
        Optional<ChannelSkuMapping> exact = channelSkuMappingRepository
            .findByChannelAndExternalSkuAndStatus(CHANNEL, externalSku, ChannelSkuMapping.STATUS_ACTIVE);
        if (exact.isPresent()) {
            return exact.get();
        }
        return channelSkuMappingRepository
            .findFirstByChannelAndNormalizedSkuAndStatus(
                CHANNEL,
                ChannelSkuMapping.normalize(externalSku),
                ChannelSkuMapping.STATUS_ACTIVE
            )
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SKU_MAPPING_PENDING,
                Map.of("externalSku", externalSku, "message", "补单只允许使用已确认的 SKU 映射")
            ));
    }

    private boolean isRepairEligible(JsonNode order) {
        String financialStatus = order.path("financial_status").asText("");
        String fulfillmentStatus = order.path("fulfillment_status").asText("");
        boolean cancelled = !order.path("cancelled_at").isMissingNode()
            && !order.path("cancelled_at").isNull();
        return "paid".equalsIgnoreCase(financialStatus)
            && !"fulfilled".equalsIgnoreCase(fulfillmentStatus)
            && !cancelled;
    }

    private String repairIneligibleReason(JsonNode order) {
        if (isRepairEligible(order)) {
            return null;
        }
        if (!order.path("cancelled_at").isMissingNode() && !order.path("cancelled_at").isNull()) {
            return "渠道订单已取消，只报告不补录";
        }
        if (!"paid".equalsIgnoreCase(order.path("financial_status").asText(""))) {
            return "渠道订单未支付，只报告不补录";
        }
        if ("fulfilled".equalsIgnoreCase(order.path("fulfillment_status").asText(""))) {
            return "渠道订单已履约，只报告不补录";
        }
        return "订单不满足安全补录条件";
    }

    private JsonNode readOrders(String rawJson) {
        try {
            JsonNode orders = objectMapper.readTree(rawJson).path("orders");
            if (!orders.isArray()) {
                throw new IllegalArgumentException("orders must be an array");
            }
            return orders;
        } catch (Exception e) {
            throw new BusinessException(
                ErrorKeys.SHOPIFY_API_ERROR,
                Map.of("message", "Unable to parse reconciliation response", "error", e.getMessage())
            );
        }
    }

    private IntegrationConfig requireActiveShopifyConfig(Long configId) {
        IntegrationConfig config = integrationConfigRepository.findById(configId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.INTEGRATION_CONFIG_NOT_FOUND,
                Map.of("configId", configId)
            ));
        if (!CHANNEL.equalsIgnoreCase(config.getPlatform()) || !Boolean.TRUE.equals(config.getIsActive())) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("message", "Shopify reconciliation requires an active Shopify configuration", "configId", configId)
            );
        }
        return config;
    }

    private BigDecimal parsePrice(String price) {
        try {
            return StringUtils.hasText(price) ? new BigDecimal(price) : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("message", "Invalid Shopify line item price", "price", String.valueOf(price))
            );
        }
    }

    private ShopifyReconciliationRepairResult.Item repairItem(
        ChannelRawEvent event,
        String status,
        Long salesOrderId,
        String message
    ) {
        return ShopifyReconciliationRepairResult.Item.builder()
            .rawEventId(event.getId())
            .externalOrderId(event.getExternalId())
            .status(status)
            .salesOrderId(salesOrderId)
            .message(message)
            .build();
    }
}
