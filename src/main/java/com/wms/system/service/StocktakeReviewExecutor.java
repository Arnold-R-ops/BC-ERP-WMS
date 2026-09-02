package com.wms.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.stocktake.ReviewStocktakeRequest;
import com.wms.system.dto.stocktake.StocktakeTaskResponse;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits a stocktake review, all inventory postings and its replay marker atomically. */
@Service
@RequiredArgsConstructor
public class StocktakeReviewExecutor {

    private final StocktakeService stocktakeService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public StocktakeTaskResponse executeAtomic(
        IdempotencyRequest claim,
        Long taskId,
        ReviewStocktakeRequest request,
        Long reviewedBy,
        String reviewedByName,
        String idempotencyKey
    ) {
        StocktakeTaskResponse response = stocktakeService.reviewStocktake(
            taskId,
            request,
            reviewedBy,
            reviewedByName,
            idempotencyKey
        );
        idempotencyService.markSucceeded(claim, 200, serialize(response));
        return response;
    }

    private String serialize(StocktakeTaskResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorKeys.INTERNAL_SERVER_ERROR);
        }
    }
}
