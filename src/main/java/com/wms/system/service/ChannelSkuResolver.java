package com.wms.system.service;

import com.wms.system.entity.ChannelSkuMapping;
import com.wms.system.entity.Product;
import com.wms.system.repository.ChannelSkuMappingRepository;
import com.wms.system.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 渠道 SKU 解析漏斗（P1 批次2，四层机制的第二层）
 *
 * 解析顺序（行业标准漏斗）：
 * 1. 映射表原文精确命中（记忆层）
 * 2. 映射表归一化命中（大写去空白）
 * 3. 商品条码精确命中 → 自学写回映射表（source=AUTO）
 * 4. 商品条码归一化命中 → 同样自学
 * 全部未命中 → MISS，由调用方记入待映射队列
 *
 * 自学（auto-learn）让条码命中只发生一次：下一单直接走第 1 层。
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSkuResolver {

    private final ChannelSkuMappingRepository mappingRepository;
    private final ProductRepository productRepository;

    public enum ResolutionType { PRODUCT, VIRTUAL, MISS }

    /**
     * 解析结果：PRODUCT 带商品与数量换算；VIRTUAL 表示非库存行；MISS 未命中
     */
    @Getter
    public static class Resolution {
        private final ResolutionType type;
        private final Product product;
        private final int quantityRatio;

        private Resolution(ResolutionType type, Product product, int quantityRatio) {
            this.type = type;
            this.product = product;
            this.quantityRatio = quantityRatio;
        }

        static Resolution product(Product product, int ratio) {
            return new Resolution(ResolutionType.PRODUCT, product, ratio);
        }

        static Resolution virtual() {
            return new Resolution(ResolutionType.VIRTUAL, null, 1);
        }

        static Resolution miss() {
            return new Resolution(ResolutionType.MISS, null, 1);
        }
    }

    /**
     * 按漏斗顺序解析渠道 SKU（在调用方事务内执行）
     */
    public Resolution resolve(String channel, String storeIdentifier, String externalSku) {
        String normalized = ChannelSkuMapping.normalize(externalSku);

        // 1. 映射表原文精确命中
        Optional<ChannelSkuMapping> exact = mappingRepository
            .findByChannelAndExternalSkuAndStatus(channel, externalSku, ChannelSkuMapping.STATUS_ACTIVE);
        if (exact.isPresent()) {
            return toResolution(exact.get());
        }

        // 2. 映射表归一化命中
        Optional<ChannelSkuMapping> fuzzy = mappingRepository
            .findFirstByChannelAndNormalizedSkuAndStatus(channel, normalized, ChannelSkuMapping.STATUS_ACTIVE);
        if (fuzzy.isPresent()) {
            log.info("SKU 归一化命中映射: channel={}, externalSku='{}' → 映射 '{}'",
                channel, externalSku, fuzzy.get().getExternalSku());
            return toResolution(fuzzy.get());
        }

        // 3. 商品条码精确命中 → 自学
        Optional<Product> byBarcode = productRepository.findByBarcode(externalSku);
        if (byBarcode.isPresent()) {
            autoLearn(channel, storeIdentifier, externalSku, normalized, byBarcode.get());
            return Resolution.product(byBarcode.get(), 1);
        }

        // 4. 商品条码归一化命中 → 自学
        Optional<Product> byNormalizedBarcode = productRepository.findByNormalizedBarcode(normalized);
        if (byNormalizedBarcode.isPresent()) {
            log.info("SKU 归一化命中条码: channel={}, externalSku='{}' → 商品条码 '{}'",
                channel, externalSku, byNormalizedBarcode.get().getBarcode());
            autoLearn(channel, storeIdentifier, externalSku, normalized, byNormalizedBarcode.get());
            return Resolution.product(byNormalizedBarcode.get(), 1);
        }

        return Resolution.miss();
    }

    private Resolution toResolution(ChannelSkuMapping mapping) {
        if (ChannelSkuMapping.TYPE_VIRTUAL.equals(mapping.getMappingType())) {
            return Resolution.virtual();
        }
        return Resolution.product(mapping.getProduct(),
            mapping.getQuantityRatio() == null ? 1 : mapping.getQuantityRatio());
    }

    /**
     * 条码命中回写映射表：下一次直接走第一层。
     * 与订单同一事务——订单回滚则自学一并回滚，下轮重新学习即可。
     */
    private void autoLearn(String channel, String storeIdentifier, String externalSku,
                           String normalized, Product product) {
        if (mappingRepository.existsByChannelAndExternalSku(channel, externalSku)) {
            return; // 已有（可能 DISABLED），不覆盖人工决策
        }
        mappingRepository.save(ChannelSkuMapping.builder()
            .channel(channel)
            .storeIdentifier(storeIdentifier)
            .externalSku(externalSku)
            .normalizedSku(normalized)
            .mappingType(ChannelSkuMapping.TYPE_PRODUCT)
            .product(product)
            .quantityRatio(1)
            .status(ChannelSkuMapping.STATUS_ACTIVE)
            .source(ChannelSkuMapping.SOURCE_AUTO)
            .remark("Auto-learned from barcode match")
            .build());
        log.info("SKU 映射自学成功: channel={}, externalSku='{}' → productId={}",
            channel, externalSku, product.getId());
    }
}
