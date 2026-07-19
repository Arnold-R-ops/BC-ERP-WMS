package com.wms.system.service;

import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.InboundOrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * SPU-SKU-DATE 批次码生成器
 *
 * 批次码格式：{PRODUCT}-{SKU_CODE}-{YYYYMMDD}
 * 示例：SPU001-SKU123-20260125
 *
 * 如果同一天同一产品有多个批次，添加序号后缀：
 * SPU001-SKU123-20260125-01
 * SPU001-SKU123-20260125-02
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpuSkuDateBatchCodeGenerator {

    private final InboundOrderItemRepository inboundOrderItemRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_SEQUENCE = 99;
    private static final int PRODUCT_TOKEN_MAX_LENGTH = 20;
    private static final int SKU_TOKEN_MAX_LENGTH = 17;

    /**
     * 生成唯一的 SPU-SKU-DATE 格式批次码
     *
     * @param product 产品对象
     * @param date 日期（通常是当前日期或生产日期）
     * @return 唯一的批次码
     * @throws BusinessException 如果批次码生成失败（超过最大序号）
     */
    public String generateUnique(ProductSku product, LocalDate date) {
        // 获取 SPU 和 SKU
        String spu = normalizeToken(getSpu(product), "P" + product.getId(), PRODUCT_TOKEN_MAX_LENGTH);
        String sku = normalizeToken(getSku(product), "SKU" + product.getId(), SKU_TOKEN_MAX_LENGTH);
        String dateStr = date.format(DATE_FORMATTER);

        // 生成基础批次码
        String baseBatchCode = String.format("%s-%s-%s", spu, sku, dateStr);

        // 检查是否存在冲突
        if (!inboundOrderItemRepository.existsByBatchCode(baseBatchCode)) {
            log.info("Generated batch code: {}", baseBatchCode);
            return baseBatchCode;
        }

        // 如果存在冲突，添加序号后缀
        log.warn("Batch code collision detected for base code: {}, trying with sequence suffix", baseBatchCode);

        for (int sequence = 1; sequence <= MAX_SEQUENCE; sequence++) {
            String batchCodeWithSeq = String.format("%s-%02d", baseBatchCode, sequence);

            if (!inboundOrderItemRepository.existsByBatchCode(batchCodeWithSeq)) {
                log.info("Generated batch code with sequence: {}", batchCodeWithSeq);
                return batchCodeWithSeq;
            }
        }

        // 如果序号用尽，抛出异常
        log.error("Failed to generate batch code: exceeded maximum sequence number for base code: {}", baseBatchCode);
        throw new BusinessException(
            "BATCH_CODE_GENERATION_FAILED",
            Map.of("baseBatchCode", baseBatchCode, "maxSequence", MAX_SEQUENCE)
        );
    }

    /**
     * 获取 SPU 编码
     * 如果产品没有 SPU，使用 "SPU" + 产品ID
     */
    private String getSpu(ProductSku product) {
        if (product.getProduct() != null && product.getProduct().getProductCode() != null) {
            return product.getProduct().getProductCode().trim();
        }
        return "SPU" + product.getId();
    }

    /**
     * 获取 SKU 编码
     * 使用不可变的内部 skuCode，避免条码变更或长条码影响批次身份。
     */
    private String getSku(ProductSku product) {
        if (product.getSkuCode() != null && !product.getSkuCode().trim().isEmpty()) {
            return product.getSkuCode().trim();
        }
        return "SKU" + product.getId();
    }

    private String normalizeToken(String value, String fallback, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9]", "");
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    /**
     * 验证批次码格式是否正确
     *
     * @param batchCode 批次码
     * @return true 如果格式正确
     */
    public boolean isValidFormat(String batchCode) {
        if (batchCode == null || batchCode.trim().isEmpty()) {
            return false;
        }

        // 基本格式：SPU-SKU-YYYYMMDD 或 SPU-SKU-YYYYMMDD-NN
        String pattern = "^[A-Za-z0-9]+-[A-Za-z0-9]+-\\d{8}(-\\d{2})?$";
        return batchCode.matches(pattern);
    }

    /**
     * 从批次码中提取日期
     *
     * @param batchCode 批次码
     * @return 日期对象，如果解析失败返回 null
     */
    public LocalDate extractDate(String batchCode) {
        if (!isValidFormat(batchCode)) {
            return null;
        }

        try {
            // 提取日期部分（第三段）
            String[] parts = batchCode.split("-");
            if (parts.length >= 3) {
                String dateStr = parts[2];
                return LocalDate.parse(dateStr, DATE_FORMATTER);
            }
        } catch (Exception e) {
            log.warn("Failed to extract date from batch code: {}", batchCode, e);
        }

        return null;
    }
}
