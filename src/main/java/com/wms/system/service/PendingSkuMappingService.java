package com.wms.system.service;

import com.wms.system.entity.ChannelSkuMapping;
import com.wms.system.entity.PendingSkuMapping;
import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.ChannelSkuMappingRepository;
import com.wms.system.repository.PendingSkuMappingRepository;
import com.wms.system.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 待映射 SKU 队列服务（P1 批次2）
 *
 * - recordMiss 用 REQUIRES_NEW：订单因未知 SKU 失败回滚时，
 *   "这个 SKU 卡过单"这个事实必须留存
 * - resolve 完成人工确认：写入映射表 + 关闭队列项；被卡订单由
 *   批次1 的 FAILED 报文重试机制在下一轮同步自动放行
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingSkuMappingService {

    private final PendingSkuMappingRepository pendingRepository;
    private final ChannelSkuMappingRepository mappingRepository;
    private final ProductSkuRepository productSkuRepository;

    public static final String ACTION_MAP = "MAP";
    public static final String ACTION_VIRTUAL = "VIRTUAL";
    public static final String ACTION_IGNORE = "IGNORE";

    /**
     * 记录一次未命中（独立事务，upsert：同 SKU 累加计数）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMiss(String channel, String storeIdentifier, String externalSku,
                           String externalTitle, BigDecimal sampleUnitPrice, String externalOrderNo) {
        PendingSkuMapping pending = pendingRepository
            .findByChannelAndExternalSku(channel, externalSku)
            .orElse(null);

        if (pending == null) {
            pendingRepository.save(PendingSkuMapping.builder()
                .channel(channel)
                .storeIdentifier(storeIdentifier)
                .externalSku(externalSku)
                .externalTitle(externalTitle)
                .sampleUnitPrice(sampleUnitPrice)
                .sampleExternalOrderNo(externalOrderNo)
                .occurrenceCount(1)
                .lastSeenAt(LocalDateTime.now())
                .status(PendingSkuMapping.STATUS_PENDING)
                .build());
            log.info("新增待映射 SKU: channel={}, externalSku='{}', 来源订单={}", channel, externalSku, externalOrderNo);
            return;
        }

        pending.setOccurrenceCount(pending.getOccurrenceCount() + 1);
        pending.setLastSeenAt(LocalDateTime.now());
        if (StringUtils.hasText(externalTitle)) {
            pending.setExternalTitle(externalTitle);
        }
        // RESOLVED 后又漏进来 = 对应映射被停用/失效，需要重新引起注意；
        // IGNORED 是人工明确的决定，只累计数不打扰
        if (PendingSkuMapping.STATUS_RESOLVED.equals(pending.getStatus())) {
            pending.setStatus(PendingSkuMapping.STATUS_PENDING);
            log.warn("已解决的 SKU 再次未命中（映射可能被停用）: channel={}, externalSku='{}'", channel, externalSku);
        }
        pendingRepository.save(pending);
    }

    @Transactional(readOnly = true)
    public List<PendingSkuMapping> list(String channel, String status) {
        String effectiveStatus = StringUtils.hasText(status) ? status : PendingSkuMapping.STATUS_PENDING;
        if (StringUtils.hasText(channel)) {
            return pendingRepository.findByChannelAndStatusOrderByOccurrenceCountDesc(channel, effectiveStatus);
        }
        return pendingRepository.findByStatusOrderByOccurrenceCountDesc(effectiveStatus);
    }

    /**
     * 人工确认：MAP（映射到商品，可带数量换算）/ VIRTUAL（非库存行）/ IGNORE（忽略）
     *
     * MAP 与 VIRTUAL 会写入（或更新）channel_sku_mapping；被该 SKU 卡住的
     * 订单在下一轮同步中自动重试放行。
     */
    @Transactional(rollbackFor = Exception.class)
    public PendingSkuMapping resolve(Long pendingId, String action, Long productSkuId,
                                     Integer quantityRatio, Long operatorId) {
        PendingSkuMapping pending = pendingRepository.findById(pendingId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "PendingSkuMapping", "resourceId", String.valueOf(pendingId))));

        switch (action == null ? "" : action.toUpperCase()) {
            case ACTION_MAP -> {
                if (productSkuId == null) {
                    throw new BusinessException(ErrorKeys.PARAMETER_REQUIRED, Map.of("parameter", "productSkuId"));
                }
                ProductSku product = productSkuRepository.findById(productSkuId)
                    .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                        Map.of("productSkuId", productSkuId)));
                int ratio = (quantityRatio == null || quantityRatio < 1) ? 1 : quantityRatio;
                upsertMapping(pending, ChannelSkuMapping.TYPE_PRODUCT, product, ratio);
                closePending(pending, PendingSkuMapping.RESOLUTION_MAPPED, operatorId);
            }
            case ACTION_VIRTUAL -> {
                upsertMapping(pending, ChannelSkuMapping.TYPE_VIRTUAL, null, 1);
                closePending(pending, PendingSkuMapping.RESOLUTION_VIRTUAL, operatorId);
            }
            case ACTION_IGNORE -> {
                pending.setStatus(PendingSkuMapping.STATUS_IGNORED);
                pending.setResolution(PendingSkuMapping.RESOLUTION_IGNORED);
                pending.setResolvedBy(operatorId);
                pending.setResolvedAt(LocalDateTime.now());
            }
            default -> throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "action", "value", String.valueOf(action),
                       "constraint", "action must be MAP / VIRTUAL / IGNORE"));
        }

        PendingSkuMapping saved = pendingRepository.save(pending);
        log.info("待映射 SKU 已处理: id={}, externalSku='{}', action={}, operatorId={}",
            pendingId, pending.getExternalSku(), action, operatorId);
        return saved;
    }

    /**
     * 候选推荐：取 SKU 首个字母数字片段（如 "TOP0002 - 2-20KG" → "TOP0002"）
     * 做条码前缀/名称关键词匹配
     */
    @Transactional(readOnly = true)
    public List<ProductSku> suggestions(Long pendingId) {
        PendingSkuMapping pending = pendingRepository.findById(pendingId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "PendingSkuMapping", "resourceId", String.valueOf(pendingId))));

        String keyword = firstToken(pending.getExternalSku(), pending.getExternalTitle());
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        return productSkuRepository
            .findTop10ByBarcodeStartingWithIgnoreCaseOrSkuNameContainingIgnoreCase(keyword, keyword);
    }

    private void upsertMapping(PendingSkuMapping pending, String type, ProductSku product, int ratio) {
        ChannelSkuMapping mapping = mappingRepository
            .findByChannelAndExternalSkuAndStatus(pending.getChannel(), pending.getExternalSku(),
                ChannelSkuMapping.STATUS_ACTIVE)
            .orElseGet(() -> ChannelSkuMapping.builder()
                .channel(pending.getChannel())
                .storeIdentifier(pending.getStoreIdentifier())
                .externalSku(pending.getExternalSku())
                .normalizedSku(ChannelSkuMapping.normalize(pending.getExternalSku()))
                .status(ChannelSkuMapping.STATUS_ACTIVE)
                .build());

        mapping.setMappingType(type);
        mapping.setProductSku(product);
        mapping.setQuantityRatio(ratio);
        mapping.setSource(ChannelSkuMapping.SOURCE_MANUAL);
        mappingRepository.save(mapping);
    }

    private void closePending(PendingSkuMapping pending, String resolution, Long operatorId) {
        pending.setStatus(PendingSkuMapping.STATUS_RESOLVED);
        pending.setResolution(resolution);
        pending.setResolvedBy(operatorId);
        pending.setResolvedAt(LocalDateTime.now());
    }

    private String firstToken(String sku, String title) {
        String source = StringUtils.hasText(sku) && !sku.startsWith(PendingSkuMapping.NO_SKU_PREFIX)
            ? sku
            : (title == null ? "" : title);
        for (String part : source.split("[\\s\\-_/]+")) {
            if (part.matches("[A-Za-z0-9]{3,}")) {
                return part;
            }
        }
        return source.length() >= 3 ? source.substring(0, Math.min(source.length(), 20)) : null;
    }
}
