package com.wms.system.service;

import com.wms.system.entity.Product;
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
 * 批次码格式：{SPU}-{SKU}-{YYYYMMDD}
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

    /**
     * 生成唯一的 SPU-SKU-DATE 格式批次码
     *
     * @param product 产品对象
     * @param date 日期（通常是当前日期或生产日期）
     * @return 唯一的批次码
     * @throws BusinessException 如果批次码生成失败（超过最大序号）
     */
    public String generateUnique(Product product, LocalDate date) {
        // 获取 SPU 和 SKU
        String spu = getSpu(product);
        String sku = getSku(product);
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
    private String getSpu(Product product) {
        if (product.getSpu() != null && product.getSpu().getSpuCode() != null) {
            return product.getSpu().getSpuCode().trim();
        }
        return "SPU" + product.getId();
    }

    /**
     * 获取 SKU 编码
     * 使用产品的 barcode 或 skuName 作为 SKU 标识
     * 如果都没有，使用 "SKU" + 产品ID
     */
    private String getSku(Product product) {
        // 优先使用 barcode
        if (product.getBarcode() != null && !product.getBarcode().trim().isEmpty()) {
            return product.getBarcode().trim();
        }
        // 其次使用 skuName
        if (product.getSkuName() != null && !product.getSkuName().trim().isEmpty()) {
            return product.getSkuName().trim().replaceAll("[^A-Za-z0-9]", "");
        }
        return "SKU" + product.getId();
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
