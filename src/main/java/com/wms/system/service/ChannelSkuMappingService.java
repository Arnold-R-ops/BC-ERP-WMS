package com.wms.system.service;

import com.wms.system.dto.integration.ChannelSkuMappingRequest;
import com.wms.system.dto.integration.ChannelSkuMappingResponse;
import com.wms.system.entity.ChannelSkuMapping;
import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.ChannelSkuMappingRepository;
import com.wms.system.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 渠道 SKU 映射管理服务（P1 批次2，人工维护入口）
 *
 * 面向运营的映射表 CRUD：手工建映射（含 Excel 初始化前置）、调整
 * 换算比、停用/启用。日常新 SKU 走待映射队列（PendingSkuMappingService），
 * 这里是直接维护入口。
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSkuMappingService {

    private final ChannelSkuMappingRepository mappingRepository;
    private final ProductSkuRepository productSkuRepository;

    @Transactional(readOnly = true)
    public List<ChannelSkuMappingResponse> list(String channel) {
        String effectiveChannel = StringUtils.hasText(channel) ? channel : "SHOPIFY";
        return mappingRepository.findByChannelOrderByIdDesc(effectiveChannel).stream()
            .map(ChannelSkuMappingResponse::from)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelSkuMappingResponse create(ChannelSkuMappingRequest request) {
        String channel = StringUtils.hasText(request.getChannel()) ? request.getChannel() : "SHOPIFY";

        if (mappingRepository.existsByChannelAndExternalSku(channel, request.getExternalSku())) {
            throw new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "Create SKU mapping",
                       "reason", "Mapping already exists for this channel + externalSku; update it instead"));
        }

        ChannelSkuMapping mapping = ChannelSkuMapping.builder()
            .channel(channel)
            .storeIdentifier(request.getStoreIdentifier())
            .externalSku(request.getExternalSku())
            .normalizedSku(ChannelSkuMapping.normalize(request.getExternalSku()))
            .status(ChannelSkuMapping.STATUS_ACTIVE)
            .source(ChannelSkuMapping.SOURCE_MANUAL)
            .remark(request.getRemark())
            .build();
        applyTypeAndProduct(mapping, request.getMappingType(), request.getProductSkuId(), request.getQuantityRatio());

        mapping = mappingRepository.save(mapping);
        log.info("SKU 映射创建: channel={}, externalSku='{}', type={}, productSkuId={}",
            channel, mapping.getExternalSku(), mapping.getMappingType(),
            mapping.getProductSku() == null ? null : mapping.getProductSku().getId());
        return ChannelSkuMappingResponse.from(mapping);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelSkuMappingResponse update(Long id, ChannelSkuMappingRequest request) {
        ChannelSkuMapping mapping = mappingRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "ChannelSkuMapping", "resourceId", String.valueOf(id))));

        if (StringUtils.hasText(request.getMappingType()) || request.getProductSkuId() != null
                || request.getQuantityRatio() != null) {
            String type = StringUtils.hasText(request.getMappingType())
                ? request.getMappingType() : mapping.getMappingType();
            Long productSkuId = request.getProductSkuId() != null
                ? request.getProductSkuId()
                : (mapping.getProductSku() == null ? null : mapping.getProductSku().getId());
            Integer ratio = request.getQuantityRatio() != null
                ? request.getQuantityRatio() : mapping.getQuantityRatio();
            applyTypeAndProduct(mapping, type, productSkuId, ratio);
        }
        if (StringUtils.hasText(request.getStatus())) {
            mapping.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            mapping.setRemark(request.getRemark());
        }
        mapping.setSource(ChannelSkuMapping.SOURCE_MANUAL);

        mapping = mappingRepository.save(mapping);
        log.info("SKU 映射更新: id={}, externalSku='{}', status={}", id, mapping.getExternalSku(), mapping.getStatus());
        return ChannelSkuMappingResponse.from(mapping);
    }

    private void applyTypeAndProduct(ChannelSkuMapping mapping, String mappingType, Long productSkuId, Integer ratio) {
        String type = StringUtils.hasText(mappingType) ? mappingType.toUpperCase() : ChannelSkuMapping.TYPE_PRODUCT;

        if (ChannelSkuMapping.TYPE_VIRTUAL.equals(type)) {
            mapping.setMappingType(ChannelSkuMapping.TYPE_VIRTUAL);
            mapping.setProductSku(null);
            mapping.setQuantityRatio(1);
            return;
        }

        if (productSkuId == null) {
            throw new BusinessException(ErrorKeys.PARAMETER_REQUIRED, Map.of("parameter", "productSkuId"));
        }
        ProductSku product = productSkuRepository.findById(productSkuId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                Map.of("productSkuId", productSkuId)));

        mapping.setMappingType(ChannelSkuMapping.TYPE_PRODUCT);
        mapping.setProductSku(product);
        mapping.setQuantityRatio(ratio == null || ratio < 1 ? 1 : ratio);
    }
}
