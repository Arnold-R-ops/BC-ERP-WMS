package com.wms.system.service;

import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.shopify.ShopifyLineItemDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.dto.shopify.ShopifyOrdersResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.integration.ShopifyApiClient;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shopify 闆嗘垚鏈嶅姟
 *
 * V3.9 鏋舵瀯锛歋hopify 璁㈠崟鍚屾
 *
 * 鏍稿績鑱岃矗锛?
 * 1. 浠?Shopify 鎷夊彇璁㈠崟锛堝畾鏃朵换鍔¤Е鍙戯級
 * 2. 璁㈠崟鍘婚噸妫€鏌ワ紙鍩轰簬 external_order_id锛?
 * 3. 瀹㈡埛鍖归厤/鍒涘缓锛堝熀浜庨偖绠憋級
 * 4. SKU 鍖归厤楠岃瘉锛堝熀浜?barcode锛?
 * 5. 鍒涘缓閿€鍞鍗曪紙璺宠繃瀹℃壒锛岀洿鎺ュ垎閰嶅簱瀛橈級
 * 6. 瑙﹀彂搴撳瓨鍒嗛厤
 *
 * 涓氬姟娴佺▼锛?
 * 1. 璇诲彇鍚敤鐨?Shopify 閰嶇疆
 * 2. 鎷夊彇璁㈠崟锛坰tatus=open&financial_status=paid锛?
 * 3. 寰幆澶勭悊姣忎釜璁㈠崟锛?
 *    a. 鍘婚噸妫€鏌ワ紙external_order_id锛?
 *    b. 瀹㈡埛鍖归厤/鍒涘缓锛坋mail锛?
 *    c. SKU 鍖归厤锛堥妫€鏌ワ級
 *    d. 鍒涘缓閿€鍞鍗?
 *    e. 璁剧疆娓犻亾淇℃伅锛坈hannel, external_order_id, external_order_no锛?
 *    f. 寮哄埗璁剧疆鐘舵€佷负 APPROVED_AWAITING_SHIPMENT
 *    g. 瑙﹀彂搴撳瓨鍒嗛厤
 * 4. 鏇存柊鍚屾鏃堕棿
 *
 * 寮傚父澶勭悊锛?
 * - API 璋冪敤澶辫触锛氳褰曟棩蹇楋紝缁х画澶勭悊鍏朵粬閰嶇疆
 * - SKU 鍖归厤澶辫触锛氳褰曟棩蹇楋紝璺宠繃璇ヨ鍗?
 * - 搴撳瓨涓嶈冻锛氳褰曟棩蹇楋紝璁㈠崟鍒涘缓澶辫触
 * - 瀹㈡埛鍒涘缓澶辫触锛氳褰曟棩蹇楋紝璺宠繃璇ヨ鍗?
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
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

    /**
     * 鍚屾 Shopify 璁㈠崟
     *
     * 涓氬姟娴佺▼锛?
     * 1. 璇诲彇閰嶇疆
     * 2. 鎷夊彇璁㈠崟
     * 3. 寰幆澶勭悊姣忎釜璁㈠崟
     * 4. 鏇存柊鍚屾鏃堕棿
     *
     * @return 鍚屾缁熻淇℃伅锛堟垚鍔熸暟銆佽烦杩囨暟銆佸け璐ユ暟锛?
     */
    @Transactional(rollbackFor = Exception.class)
    public SyncResult syncOrders() {
        log.info("========== 寮€濮嬪悓姝?Shopify 璁㈠崟 ==========");

        SyncResult result = new SyncResult();

        // 1. 璇诲彇閰嶇疆
        List<IntegrationConfig> configs = integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY");

        if (configs.isEmpty()) {
            log.info("娌℃湁鍚敤鐨?Shopify 閰嶇疆锛岃烦杩囧悓姝?");
            return result;
        }

        log.info("Found {} active Shopify configs", configs.size());

        // 2. 寰幆澶勭悊姣忎釜閰嶇疆
        for (IntegrationConfig config : configs) {
            try {
                log.info("寮€濮嬪悓姝?Shopify 璁㈠崟: storeUrl={}", config.getStoreUrl());

                // 鎷夊彇璁㈠崟
                ShopifyOrdersResponse response = shopifyApiClient.fetchOrders(config);
                List<ShopifyOrderDto> orders = response.getOrders();

                if (orders == null || orders.isEmpty()) {
                    log.info("娌℃湁闇€瑕佸悓姝ョ殑璁㈠崟: storeUrl={}", config.getStoreUrl());
                    continue;
                }

                log.info("鎷夊彇鍒?{} 涓鍗? storeUrl={}", orders.size(), config.getStoreUrl());

                // 3. 寰幆澶勭悊姣忎釜璁㈠崟
                for (ShopifyOrderDto order : orders) {
                    try {
                        processOrder(order, config);
                        result.incrementSuccess();
                    } catch (Exception e) {
                        log.error("澶勭悊璁㈠崟澶辫触: externalOrderNo={}, error={}", order.getName(), e.getMessage(), e);
                        result.incrementFailed();
                    }
                }

                // 4. 鏇存柊鍚屾鏃堕棿
                config.setLastSyncAt(LocalDateTime.now());
                integrationConfigRepository.save(config);

                log.info("Shopify 璁㈠崟鍚屾瀹屾垚: storeUrl={}, 鎴愬姛={}, 璺宠繃={}, 澶辫触={}",
                        config.getStoreUrl(), result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount());

            } catch (Exception e) {
                log.error("鍚屾 Shopify 閰嶇疆澶辫触: storeUrl={}, error={}", config.getStoreUrl(), e.getMessage(), e);
                result.incrementFailed();
            }
        }

        log.info("========== Shopify 璁㈠崟鍚屾瀹屾垚 ========== 鎬昏: 鎴愬姛={}, 璺宠繃={}, 澶辫触={}",
                result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount());

        return result;
    }

    /**
     * 澶勭悊鍗曚釜 Shopify 璁㈠崟
     *
     * @param order Shopify 璁㈠崟
     * @param config 闆嗘垚閰嶇疆
     */
    private void processOrder(ShopifyOrderDto order, IntegrationConfig config) {
        String externalOrderId = String.valueOf(order.getId());
        String externalOrderNo = order.getName();

        log.info("澶勭悊璁㈠崟: externalOrderNo={}, externalOrderId={}", externalOrderNo, externalOrderId);

        // a. 鍘婚噸妫€鏌?
        Optional<SalesOrder> existingOrder = salesOrderRepository.findByExternalOrderId(externalOrderId);
        if (existingOrder.isPresent()) {
            log.info("璁㈠崟宸插瓨鍦紝璺宠繃: externalOrderNo={}, salesOrderId={}", externalOrderNo, existingOrder.get().getId());
            throw new BusinessException(ErrorKeys.SHOPIFY_ORDER_ALREADY_SYNCED,
                    Map.of(
                            "externalOrderId", externalOrderId,
                            "externalOrderNo", externalOrderNo,
                            "salesOrderId", existingOrder.get().getId()
                    ));
        }

        // b. 瀹㈡埛鍖归厤/鍒涘缓
        Customer customer = matchOrCreateCustomer(order);

        // c. SKU 鍖归厤锛堥妫€鏌ワ級
        List<Product> products = validateAndMatchProducts(order);

        // d. 鏋勫缓璁㈠崟璇锋眰
        CreateSalesOrderRequest request = buildSalesOrderRequest(order, customer, products);

        // e. 鍒涘缓閿€鍞鍗?
        Long salesOrderId = createSalesOrderForShopify(request, order);

        // f. 瑙﹀彂搴撳瓨鍒嗛厤
        try {
            log.info("瑙﹀彂搴撳瓨鍒嗛厤: salesOrderId={}, externalOrderNo={}", salesOrderId, externalOrderNo);
            allocationService.allocateInventory(salesOrderId);
            log.info("鉁?搴撳瓨鍒嗛厤鎴愬姛: salesOrderId={}, externalOrderNo={}", salesOrderId, externalOrderNo);
        } catch (Exception e) {
            log.error("搴撳瓨鍒嗛厤澶辫触: salesOrderId={}, externalOrderNo={}, error={}",
                    salesOrderId, externalOrderNo, e.getMessage());
            throw e;
        }

        log.info("鉁?璁㈠崟澶勭悊鎴愬姛: externalOrderNo={}, salesOrderId={}", externalOrderNo, salesOrderId);
    }

    /**
     * 瀹㈡埛鍖归厤/鍒涘缓
     *
     * @param order Shopify 璁㈠崟
     * @return 瀹㈡埛瀹炰綋
     */
    private Customer matchOrCreateCustomer(ShopifyOrderDto order) {
        String email = order.getEmail();

        if (!StringUtils.hasText(email)) {
            log.error("璁㈠崟缂哄皯瀹㈡埛閭: externalOrderNo={}", order.getName());
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                            "field", "email",
                            "value", "null",
                            "constraint", "瀹㈡埛閭涓嶈兘涓虹┖"
                    ));
        }

        // 鏍规嵁閭鏌ヨ瀹㈡埛
        Optional<Customer> existingCustomer = customerRepository.findByEmail(email);

        if (existingCustomer.isPresent()) {
            log.info("鎵惧埌鍖归厤鐨勫鎴? email={}, customerId={}", email, existingCustomer.get().getId());
            return existingCustomer.get();
        }

        // 鍒涘缓鏂板鎴?
        log.info("鍒涘缓鏂板鎴? email={}", email);

        String firstName = order.getCustomer() != null ? order.getCustomer().getFirstName() : "";
        String lastName = order.getCustomer() != null ? order.getCustomer().getLastName() : "";
        String phone = order.getCustomer() != null ? order.getCustomer().getPhone() : "";
        Long shopifyCustomerId = order.getCustomer() != null ? order.getCustomer().getId() : order.getId();

        String customerName = (firstName + " " + lastName).trim();
        if (!StringUtils.hasText(customerName)) {
            customerName = email; // 濡傛灉娌℃湁鍚嶅瓧锛屼娇鐢ㄩ偖绠变綔涓哄悕瀛?
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
        log.info("鉁?鏂板鎴峰垱寤烘垚鍔? customerId={}, code={}, email={}", newCustomer.getId(), newCustomer.getCode(), email);

        return newCustomer;
    }

    /**
     * SKU 鍖归厤楠岃瘉锛堥妫€鏌ワ級
     *
     * @param order Shopify 璁㈠崟
     * @return 浜у搧鍒楄〃
     */
    private List<Product> validateAndMatchProducts(ShopifyOrderDto order) {
        List<Product> products = new ArrayList<>();

        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            log.error("璁㈠崟娌℃湁鏄庣粏: externalOrderNo={}", order.getName());
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                            "field", "lineItems",
                            "value", "empty",
                            "constraint", "璁㈠崟鏄庣粏涓嶈兘涓虹┖"
                    ));
        }

        for (ShopifyLineItemDto lineItem : order.getLineItems()) {
            String sku = lineItem.getSku();

            if (!StringUtils.hasText(sku)) {
                log.warn("璁㈠崟鏄庣粏缂哄皯 SKU: externalOrderNo={}, lineItemId={}", order.getName(), lineItem.getId());
                continue;
            }

            // 鏍规嵁 SKU 鏌ヨ浜у搧锛堜娇鐢?barcode 瀛楁锛?
            Optional<Product> product = productRepository.findByBarcode(sku);

            if (product.isEmpty()) {
                log.error("SKU 涓嶅瓨鍦? sku={}, externalOrderNo={}", sku, order.getName());
                throw new BusinessException(ErrorKeys.SHOPIFY_SKU_NOT_FOUND,
                        Map.of(
                                "sku", sku,
                                "externalOrderNo", order.getName(),
                                "externalOrderId", String.valueOf(order.getId())
                        ));
            }

            products.add(product.get());
            log.info("SKU 鍖归厤鎴愬姛: sku={}, productId={}, productName={}", sku, product.get().getId(), product.get().getName());
        }

        return products;
    }

    /**
     * 鏋勫缓閿€鍞鍗曡姹?
     *
     * @param order Shopify 璁㈠崟
     * @param customer 瀹㈡埛
     * @param products 浜у搧鍒楄〃
     * @return 鍒涘缓閿€鍞鍗曡姹?
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

            // 瑙ｆ瀽鍗曚环
            BigDecimal unitPrice = BigDecimal.ZERO;
            try {
                if (StringUtils.hasText(lineItem.getPrice())) {
                    unitPrice = new BigDecimal(lineItem.getPrice());
                }
            } catch (NumberFormatException e) {
                log.warn("鍗曚环瑙ｆ瀽澶辫触: price={}, 浣跨敤榛樿鍊?0", lineItem.getPrice());
            }
            itemData.setUnitPrice(unitPrice);

            // 榛樿涓嶆嫆缁濊繎鏁堟湡
            itemData.setRejectNearExpiry(false);

            // 涓嶆寚瀹氭壒娆?
            itemData.setSpecifiedBatchIds(null);

            // 澶囨敞
            itemData.setRemark("Shopify 璁㈠崟: " + order.getName());

            items.add(itemData);
        }

        request.setItems(items);

        return request;
    }

    /**
     * 涓?Shopify 璁㈠崟鍒涘缓閿€鍞鍗?
     *
     * @param request 鍒涘缓閿€鍞鍗曡姹?
     * @param order Shopify 璁㈠崟
     * @return 閿€鍞鍗?ID
     */
    private Long createSalesOrderForShopify(CreateSalesOrderRequest request, ShopifyOrderDto order) {
        // 璋冪敤 SalesSubmissionService 鍒涘缓璁㈠崟
        var response = salesSubmissionService.createSalesOrder(
                request,
                1L, // 绯荤粺鐢ㄦ埛 ID
                "Shopify Integration"
        );

        // 鑾峰彇鍒涘缓鐨勮鍗?
        SalesOrder salesOrder = salesOrderRepository.findById(response.getId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND,
                        Map.of("salesOrderId", response.getId())));

        // 璁剧疆娓犻亾淇℃伅
        salesOrder.setChannel("SHOPIFY");
        salesOrder.setExternalOrderId(String.valueOf(order.getId()));
        salesOrder.setExternalOrderNo(order.getName());

        // 寮哄埗璁剧疆鐘舵€佷负 APPROVED_AWAITING_SHIPMENT锛堣烦杩囧鎵癸級
        salesOrder.setStatus(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT);

        // 淇濆瓨璁㈠崟
        salesOrder = salesOrderRepository.save(salesOrder);

        log.info("鉁?閿€鍞鍗曞垱寤烘垚鍔? salesOrderId={}, orderNo={}, externalOrderNo={}",
                salesOrder.getId(), salesOrder.getOrderNo(), order.getName());

        return salesOrder.getId();
    }

    /**
     * 鍚屾缁撴灉缁熻
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




