package com.wms.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.shopify.ShopifyLineItemDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.*;
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
import java.math.RoundingMode;
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
 * 3. 逐单：去重 → 留底 → 匹配既有客户/SKU → 创建待审批销售订单
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
    private final ShopifyConsumerIdentityService consumerIdentityService;
    private final SalesSubmissionService salesSubmissionService;
    private final ChannelRawEventService rawEventService;
    private final ChannelSkuResolver skuResolver;
    private final PendingSkuMappingService pendingSkuMappingService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    private static final String CHANNEL = "SHOPIFY";

    /**
     * 无 SKU 行处理策略配置键（V4_16 播种，运行时可改）：PENDING / SKIP
     */
    static final String NO_SKU_POLICY_KEY = "integration.no_sku_line_policy";
    static final String NO_SKU_POLICY_PENDING = "PENDING";
    static final String NO_SKU_POLICY_SKIP = "SKIP";

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
                    processOrderNode(orderNode, config, result, ChannelRawEvent.SOURCE_POLL);
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
     * Webhook 新订单入口（P1-B3）：与轮询共用同一处理管道
     * （终态去重、报文留底、SKU 漏斗、独立事务、失败重试语义完全一致）
     */
    public SyncResult processWebhookOrder(JsonNode orderNode, IntegrationConfig config) {
        SyncResult result = new SyncResult();
        processOrderNode(orderNode, config, result, ChannelRawEvent.SOURCE_WEBHOOK);
        return result;
    }

    /**
     * 处理单个订单节点（P1-B1 编排）：
     * 1. 去重——留底表已有 PROCESSED/SKIPPED 终态则静默跳过（不重复落库）
     * 2. 留底——新订单先存原始报文（独立事务，业务失败不回滚留底）
     * 3. 处理——订单转换+库存分配跑在自己的事务里，失败只回滚本单
     * 4. 结果回写留底状态
     */
    private void processOrderNode(JsonNode orderNode, IntegrationConfig config, SyncResult result, String source) {
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
            rawEventService.record(CHANNEL, config.getStoreUrl(), source,
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

        // b. 只匹配既有客户；渠道导入无权创建客户主数据
        Customer customer = consumerIdentityService.resolveForAutomaticIngestion(order, config);

        // c+d. 行解析（P1-B2 四层 SKU 漏斗）并构建订单请求
        //      未知 SKU 进待映射队列并阻断本单（报文保留 FAILED，映射后自动重试放行）
        CreateSalesOrderRequest request = buildSalesOrderRequest(order, customer, config);

        // e. 创建销售订单
        Long salesOrderId = createSalesOrderForShopify(request, order);

        log.info("订单补录成功并进入待审批: externalOrderNo={}, salesOrderId={}", externalOrderNo, salesOrderId);
    }

    /**
     * 行解析 + 构建销售订单请求（P1-B2）
     *
     * 每一行独立走四层 SKU 解析漏斗，商品与明细一一对应地构建——
     * 修复了旧实现"跳过无 SKU 行导致下标错位"的缺陷。
     *
     * 行处理规则：
     * - PRODUCT 命中：按数量换算比转换（数量 ×ratio，单价 ÷ratio）
     * - VIRTUAL 命中：非库存行，跳过履约（金额不进 WMS 订单）
     * - 未命中：记入待映射队列并阻断本单（SKU_MAPPING_PENDING）；
     *   无 SKU 行按策略 PENDING（默认，同上）或 SKIP（丢行保单）
     *
     * @param order Shopify 订单
     * @param customer 客户
     * @param config 集成配置（提供店铺标识）
     * @return 创建销售订单请求
     */
    private CreateSalesOrderRequest buildSalesOrderRequest(ShopifyOrderDto order, Customer customer,
                                                           IntegrationConfig config) {
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            log.error("订单没有明细: externalOrderNo={}", order.getName());
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                            "field", "lineItems",
                            "value", "empty",
                            "constraint", "订单明细不能为空"
                    ));
        }

        String noSkuPolicy = readNoSkuPolicy();
        List<CreateSalesOrderRequest.SalesOrderItemData> items = new ArrayList<>();
        List<String> pendingSkus = new ArrayList<>();

        for (ShopifyLineItemDto lineItem : order.getLineItems()) {
            boolean hasSku = StringUtils.hasText(lineItem.getSku());
            // 无 SKU 行用合成键，也走映射表：人工可将其映射为商品或 VIRTUAL
            String effectiveSku = hasSku ? lineItem.getSku() : noSkuKey(lineItem);

            ChannelSkuResolver.Resolution resolution =
                skuResolver.resolve(CHANNEL, config.getStoreUrl(), effectiveSku);

            switch (resolution.getType()) {
                case PRODUCT -> {
                    items.add(toItemData(lineItem, resolution, order, effectiveSku));
                    log.info("SKU 解析成功: sku='{}' → productSkuId={}, ratio={}",
                        effectiveSku, resolution.getProductSku().getId(), resolution.getQuantityRatio());
                }
                case VIRTUAL -> log.info("虚拟行跳过履约: externalOrderNo={}, sku='{}', title={}",
                    order.getName(), effectiveSku, lineItem.getName());
                case MISS -> {
                    if (!hasSku && NO_SKU_POLICY_SKIP.equalsIgnoreCase(noSkuPolicy)) {
                        log.warn("无 SKU 行按策略跳过: externalOrderNo={}, title={}",
                            order.getName(), lineItem.getName());
                    } else {
                        // REQUIRES_NEW：本单随后被阻断回滚，但"卡过单"的事实留存
                        pendingSkuMappingService.recordMiss(CHANNEL, config.getStoreUrl(), effectiveSku,
                            lineItem.getName(), parsePrice(lineItem.getPrice()), order.getName());
                        pendingSkus.add(effectiveSku);
                    }
                }
            }
        }

        if (!pendingSkus.isEmpty()) {
            log.warn("订单被未知 SKU 阻断，已入待映射队列: externalOrderNo={}, skus={}",
                order.getName(), pendingSkus);
            throw new BusinessException(ErrorKeys.SKU_MAPPING_PENDING,
                    Map.of(
                            "externalOrderNo", order.getName(),
                            "pendingSkus", String.join(", ", pendingSkus)
                    ));
        }

        if (items.isEmpty()) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                            "field", "lineItems",
                            "value", "all skipped",
                            "constraint", "订单没有可履约的商品行（全部为虚拟行或被跳过）"
                    ));
        }

        CreateSalesOrderRequest request = new CreateSalesOrderRequest();
        request.setCustomerId(customer.getId());
        request.setItems(items);
        ShopifyOrderSnapshotMapper.applyShippingSnapshot(order, request);
        return request;
    }

    /**
     * 单行转换：应用数量换算比（数量 ×ratio，单价 ÷ratio，四舍五入到分）
     */
    private CreateSalesOrderRequest.SalesOrderItemData toItemData(ShopifyLineItemDto lineItem,
                                                                  ChannelSkuResolver.Resolution resolution,
                                                                  ShopifyOrderDto order,
                                                                  String effectiveSku) {
        int ratio = resolution.getQuantityRatio();
        BigDecimal channelPrice = parsePrice(lineItem.getPrice());

        CreateSalesOrderRequest.SalesOrderItemData itemData = new CreateSalesOrderRequest.SalesOrderItemData();
        itemData.setProductSkuId(resolution.getProductSku().getId());
        itemData.setQuantity(lineItem.getQuantity() * ratio);
        itemData.setUnitPrice(ratio == 1
            ? channelPrice
            : channelPrice.divide(BigDecimal.valueOf(ratio), 2, RoundingMode.HALF_UP));
        itemData.setRejectNearExpiry(false);
        itemData.setSpecifiedBatchIds(null);

        String remark = "Shopify 订单: " + order.getName();
        if (ratio > 1) {
            remark += String.format("（渠道SKU '%s' ×%d 换算，渠道单价 %s）",
                effectiveSku, ratio, lineItem.getPrice());
        }
        itemData.setRemark(remark);
        return itemData;
    }

    /**
     * 无 SKU 行的合成映射键：NOSKU:: + 行标题（截断）
     */
    private String noSkuKey(ShopifyLineItemDto lineItem) {
        String title = StringUtils.hasText(lineItem.getName()) ? lineItem.getName().trim() : "UNTITLED";
        String key = PendingSkuMapping.NO_SKU_PREFIX + title;
        return key.length() > 200 ? key.substring(0, 200) : key;
    }

    private BigDecimal parsePrice(String price) {
        try {
            if (StringUtils.hasText(price)) {
                return new BigDecimal(price);
            }
        } catch (NumberFormatException e) {
            log.warn("单价解析失败: price={}, 使用默认值 0", price);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 读取无 SKU 行策略（配置缺失时安全回退到 PENDING）
     */
    private String readNoSkuPolicy() {
        try {
            return systemConfigService.getConfigValue(NO_SKU_POLICY_KEY);
        } catch (Exception e) {
            return NO_SKU_POLICY_PENDING;
        }
    }

    /**
     * 为 Shopify 订单创建销售单
     *
     * @param request 创建销售订单请求
     * @param order Shopify 订单
     * @return 销售单 ID
     */
    private Long createSalesOrderForShopify(CreateSalesOrderRequest request, ShopifyOrderDto order) {
        var response = salesSubmissionService.createChannelOrderPendingApproval(
                request,
                1L, // 系统用户 ID
                "Shopify Integration",
                CHANNEL,
                String.valueOf(order.getId()),
                order.getName()
        );

        log.info("销售订单创建成功: salesOrderId={}, orderNo={}, externalOrderNo={}",
                response.getId(), response.getOrderNo(), order.getName());

        return response.getId();
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
