package com.wms.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.shopify.ShopifyLineItemDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.integration.ShopifyApiClient;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shopify 集成服务
 *
 * V3.9 起：Shopify 订单同步；P1 批次1 改造：
 * - 原始报文先落库再处理（channel_raw_events，可诊断可重放）
 * - 每个订单独立事务，失败互不影响，也不再污染其他订单的写入
 * - 去重下沉到报文留底层：已处理/已跳过的订单在后续轮询中静默跳过
 *
 * 业务流程：
 * 1. 读取启用的 Shopify 配置
 * 2. 拉取订单原始 JSON（status=open&financial_status=paid）
 * 3. 逐单：去重 → 留底 → 转换 → 创建销售订单（免审批）→ 库存分配
 * 4. 留底状态回写：PROCESSED / FAILED / SKIPPED
 *
 * 已知待办（批次2）：
 * - 无 SKU 行与商品列表按下标配对存在错位缺陷，将随 SKU 映射队列一并修复
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version P1-B1（原 V3.9）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyIntegrationService {

    private final IntegrationConfigRepository integrationConfigRepository;
    private final ShopifyApiClient shopifyApiClient;
    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SalesSubmissionService salesSubmissionService;
    private final AllocationService allocationService;
    private final ChannelRawEventService rawEventService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    private static final String CHANNEL = "SHOPIFY";

    /**
     * 同步 Shopify 订单（定时任务入口）
     *
     * @return 同步统计（成功数、跳过数、失败数）
     */
    public SyncResult syncOrders() {
        log.info("========== 开始同步 Shopify 订单 ==========");

        SyncResult result = new SyncResult();

        List<IntegrationConfig> configs = integrationConfigRepository.findByPlatformAndIsActiveTrue(CHANNEL);

        if (configs.isEmpty()) {
            log.info("没有启用的 Shopify 配置，跳过同步");
            return result;
        }

        log.info("Found {} active Shopify configs", configs.size());

        for (IntegrationConfig config : configs) {
            try {
                log.info("开始同步 Shopify 订单: storeUrl={}", config.getStoreUrl());

                // 拉取原始报文（P1-B1：先落库再处理）
                String rawJson = shopifyApiClient.fetchOrdersRaw(config);
                JsonNode ordersNode = objectMapper.readTree(rawJson).path("orders");

                if (!ordersNode.isArray() || ordersNode.isEmpty()) {
                    log.info("没有需要同步的订单: storeUrl={}", config.getStoreUrl());
                    continue;
                }

                log.info("拉取到 {} 个订单: storeUrl={}", ordersNode.size(), config.getStoreUrl());

                // 逐单处理：每单独立事务，失败互不影响
                for (JsonNode orderNode : ordersNode) {
                    processOrderNode(orderNode, config, result);
                }

                config.setLastSyncAt(LocalDateTime.now());
                integrationConfigRepository.save(config);

                log.info("Shopify 订单同步完成: storeUrl={}, 成功={}, 跳过={}, 失败={}",
                        config.getStoreUrl(), result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount());

            } catch (Exception e) {
                log.error("同步 Shopify 配置失败: storeUrl={}, error={}", config.getStoreUrl(), e.getMessage(), e);
                result.incrementFailed();
            }
        }

        log.info("========== Shopify 订单同步完成 ========== 总计: 成功={}, 跳过={}, 失败={}",
                result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount());

        return result;
    }

    /**
     * 处理单个订单节点（P1-B1 编排）：
     * 1. 去重——留底表已有 PROCESSED/SKIPPED 终态则静默跳过（不重复落库）
     * 2. 留底——新订单先存原始报文（独立事务，业务失败不回滚留底）
     * 3. 处理——订单转换+库存分配跑在自己的事务里，失败只回滚本单
     * 4. 结果回写留底状态
     */
    private void processOrderNode(JsonNode orderNode, IntegrationConfig config, SyncResult result) {
        String externalId = orderNode.path("id").asText(null);
        String externalOrderNo = orderNode.path("name").asText("(unknown)");

        if (externalId == null) {
            log.warn("订单报文缺少 id，计为失败: storeUrl={}", config.getStoreUrl());
            result.incrementFailed();
            return;
        }

        // 1. 去重：已有终态留底 → 静默跳过，避免每轮轮询重复写库
        Optional<ChannelRawEvent> latest =
            rawEventService.findLatest(CHANNEL, ChannelRawEvent.TYPE_ORDER, externalId);
        if (latest.isPresent()
                && (ChannelRawEvent.STATUS_PROCESSED.equals(latest.get().getStatus())
                    || ChannelRawEvent.STATUS_SKIPPED.equals(latest.get().getStatus()))) {
            result.incrementSkipped();
            return;
        }

        // 2. 留底：复用未终态的旧事件（FAILED 重试场景），否则新建
        ChannelRawEvent event = latest.orElseGet(() ->
            rawEventService.record(CHANNEL, config.getStoreUrl(), ChannelRawEvent.SOURCE_POLL,
                ChannelRawEvent.TYPE_ORDER, externalId, orderNode.toString()));

        // 3. 业务去重：批次1 之前同步过的历史订单补一个 SKIPPED 标记（一次性）
        if (salesOrderRepository.findByExternalOrderId(externalId).isPresent()) {
            rawEventService.markSkipped(event.getId(), "Order already synced (pre-existing sales order)");
            result.incrementSkipped();
            return;
        }

        // 4. 订单处理：独立事务，异常向外抛出触发本单回滚
        try {
            ShopifyOrderDto orderDto = objectMapper.treeToValue(orderNode, ShopifyOrderDto.class);
            transactionTemplate.executeWithoutResult(status -> processOrder(orderDto, config));
            rawEventService.markProcessed(event.getId());
            result.incrementSuccess();
        } catch (Exception e) {
            log.error("处理订单失败: externalOrderNo={}, error={}", externalOrderNo, e.getMessage(), e);
            rawEventService.markFailed(event.getId(), e.getMessage());
            result.incrementFailed();
        }
    }

    /**
     * 处理单个 Shopify 订单（在调用方的事务模板内执行）
     *
     * @param order Shopify 订单
     * @param config 集成配置
     */
    private void processOrder(ShopifyOrderDto order, IntegrationConfig config) {
        String externalOrderId = String.valueOf(order.getId());
        String externalOrderNo = order.getName();

        log.info("处理订单: externalOrderNo={}, externalOrderId={}", externalOrderNo, externalOrderId);

        // a. 去重检查（防御性保留；常规去重已在 processOrderNode 完成）
        Optional<SalesOrder> existingOrder = salesOrderRepository.findByExternalOrderId(externalOrderId);
        if (existingOrder.isPresent()) {
            log.info("订单已存在，跳过: externalOrderNo={}, salesOrderId={}", externalOrderNo, existingOrder.get().getId());
            throw new BusinessException(ErrorKeys.SHOPIFY_ORDER_ALREADY_SYNCED,
                    Map.of(
                            "externalOrderId", externalOrderId,
                            "externalOrderNo", externalOrderNo,
                            "salesOrderId", existingOrder.get().getId()
                    ));
        }

        // b. 客户匹配/创建
        Customer customer = matchOrCreateCustomer(order);

        // c. SKU 匹配（预检查）
        List<Product> products = validateAndMatchProducts(order);

        // d. 构建订单请求
        CreateSalesOrderRequest request = buildSalesOrderRequest(order, customer, products);

        // e. 创建销售订单
        Long salesOrderId = createSalesOrderForShopify(request, order);

        // f. 触发库存分配
        try {
            log.info("触发库存分配: salesOrderId={}, externalOrderNo={}", salesOrderId, externalOrderNo);
            allocationService.allocateInventory(salesOrderId);
            log.info("库存分配成功: salesOrderId={}, externalOrderNo={}", salesOrderId, externalOrderNo);
        } catch (Exception e) {
            log.error("库存分配失败: salesOrderId={}, externalOrderNo={}, error={}",
                    salesOrderId, externalOrderNo, e.getMessage());
            throw e;
        }

        log.info("订单处理成功: externalOrderNo={}, salesOrderId={}", externalOrderNo, salesOrderId);
    }

    /**
     * 客户匹配/创建
     *
     * @param order Shopify 订单
     * @return 客户实体
     */
    private Customer matchOrCreateCustomer(ShopifyOrderDto order) {
        String email = order.getEmail();

        if (!StringUtils.hasText(email)) {
            log.error("订单缺少客户邮箱: externalOrderNo={}", order.getName());
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                            "field", "email",
                            "value", "null",
                            "constraint", "客户邮箱不能为空"
                    ));
        }

        // 根据邮箱查询客户
        Optional<Customer> existingCustomer = customerRepository.findByEmail(email);

        if (existingCustomer.isPresent()) {
            log.info("找到匹配的客户: email={}, customerId={}", email, existingCustomer.get().getId());
            return existingCustomer.get();
        }

        // 创建新客户
        log.info("创建新客户: email={}", email);

        String firstName = "";
        String lastName = "";
        if (order.getCustomer() != null) {
            if (StringUtils.hasText(order.getCustomer().getFirstName())) {
                firstName = order.getCustomer().getFirstName().trim();
            }
            if (StringUtils.hasText(order.getCustomer().getLastName())) {
                lastName = order.getCustomer().getLastName().trim();
            }
        }
        String phone = order.getCustomer() != null ? order.getCustomer().getPhone() : "";
        Long shopifyCustomerId = order.getCustomer() != null ? order.getCustomer().getId() : order.getId();

        String customerName = (firstName + " " + lastName).trim();
        if (!StringUtils.hasText(customerName)) {
            customerName = email; // 如果没有名字，使用邮箱作为名字
        }

        Customer newCustomer = Customer.builder()
                .code("SHOPIFY_" + shopifyCustomerId)
                .name(customerName)
                .email(email)
                .phone(phone)
                .isActive(true)
                .creditLimit(BigDecimal.ZERO)
                .build();

        newCustomer = customerRepository.save(newCustomer);
        log.info("新客户创建成功: customerId={}, code={}, email={}", newCustomer.getId(), newCustomer.getCode(), email);

        return newCustomer;
    }

    /**
     * SKU 匹配验证（预检查）
     *
     * @param order Shopify 订单
     * @return 产品列表
     */
    private List<Product> validateAndMatchProducts(ShopifyOrderDto order) {
        List<Product> products = new ArrayList<>();

        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            log.error("订单没有明细: externalOrderNo={}", order.getName());
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                            "field", "lineItems",
                            "value", "empty",
                            "constraint", "订单明细不能为空"
                    ));
        }

        for (ShopifyLineItemDto lineItem : order.getLineItems()) {
            String sku = lineItem.getSku();

            if (!StringUtils.hasText(sku)) {
                log.warn("订单明细缺少 SKU: externalOrderNo={}, lineItemId={}", order.getName(), lineItem.getId());
                continue;
            }

            // 根据 SKU 查询产品（使用 barcode 字段）
            Optional<Product> product = productRepository.findByBarcode(sku);

            if (product.isEmpty()) {
                log.error("SKU 不存在: sku={}, externalOrderNo={}", sku, order.getName());
                throw new BusinessException(ErrorKeys.SHOPIFY_SKU_NOT_FOUND,
                        Map.of(
                                "sku", sku,
                                "externalOrderNo", order.getName(),
                                "externalOrderId", String.valueOf(order.getId())
                        ));
            }

            products.add(product.get());
            log.info("SKU 匹配成功: sku={}, productId={}, productName={}", sku, product.get().getId(), product.get().getName());
        }

        return products;
    }

    /**
     * 构建销售订单请求
     *
     * 注意（批次2 待修复）：validateAndMatchProducts 会跳过无 SKU 行，
     * 导致 products 与 lineItems 按下标配对错位。批次2 引入 SKU 映射
     * 队列时重构此处的行配对逻辑。
     *
     * @param order Shopify 订单
     * @param customer 客户
     * @param products 产品列表
     * @return 创建销售订单请求
     */
    private CreateSalesOrderRequest buildSalesOrderRequest(ShopifyOrderDto order, Customer customer, List<Product> products) {
        CreateSalesOrderRequest request = new CreateSalesOrderRequest();
        request.setCustomerId(customer.getId());

        List<CreateSalesOrderRequest.SalesOrderItemData> items = new ArrayList<>();

        for (int i = 0; i < order.getLineItems().size(); i++) {
            ShopifyLineItemDto lineItem = order.getLineItems().get(i);
            Product product = products.get(i);

            CreateSalesOrderRequest.SalesOrderItemData itemData = new CreateSalesOrderRequest.SalesOrderItemData();
            itemData.setProductId(product.getId());
            itemData.setQuantity(lineItem.getQuantity());

            // 解析单价
            BigDecimal unitPrice = BigDecimal.ZERO;
            try {
                if (StringUtils.hasText(lineItem.getPrice())) {
                    unitPrice = new BigDecimal(lineItem.getPrice());
                }
            } catch (NumberFormatException e) {
                log.warn("单价解析失败: price={}, 使用默认值 0", lineItem.getPrice());
            }
            itemData.setUnitPrice(unitPrice);

            // 默认不拒绝近效期
            itemData.setRejectNearExpiry(false);

            // 不指定批次
            itemData.setSpecifiedBatchIds(null);

            // 备注
            itemData.setRemark("Shopify 订单: " + order.getName());

            items.add(itemData);
        }

        request.setItems(items);

        return request;
    }

    /**
     * 为 Shopify 订单创建销售单
     *
     * @param request 创建销售订单请求
     * @param order Shopify 订单
     * @return 销售单 ID
     */
    private Long createSalesOrderForShopify(CreateSalesOrderRequest request, ShopifyOrderDto order) {
        // 调用 SalesSubmissionService 创建订单
        var response = salesSubmissionService.createSalesOrder(
                request,
                1L, // 系统用户 ID
                "Shopify Integration"
        );

        // 获取创建的订单
        SalesOrder salesOrder = salesOrderRepository.findById(response.getId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND,
                        Map.of("salesOrderId", response.getId())));

        // 设置渠道信息
        salesOrder.setChannel(CHANNEL);
        salesOrder.setExternalOrderId(String.valueOf(order.getId()));
        salesOrder.setExternalOrderNo(order.getName());

        // 强制设置状态为 APPROVED_AWAITING_SHIPMENT（跳过审批）
        salesOrder.setStatus(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT);

        // 保存订单
        salesOrder = salesOrderRepository.save(salesOrder);

        log.info("销售订单创建成功: salesOrderId={}, orderNo={}, externalOrderNo={}",
                salesOrder.getId(), salesOrder.getOrderNo(), order.getName());

        return salesOrder.getId();
    }

    /**
     * 同步结果统计
     */
    public static class SyncResult {
        private int successCount = 0;
        private int skippedCount = 0;
        private int failedCount = 0;

        public void incrementSuccess() {
            successCount++;
        }

        public void incrementSkipped() {
            skippedCount++;
        }

        public void incrementFailed() {
            failedCount++;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public int getFailedCount() {
            return failedCount;
        }
    }
}
