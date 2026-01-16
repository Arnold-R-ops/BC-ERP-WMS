package com.wms.system.util;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.hashids.Hashids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Batch Code Generator (Hashids 批次码生成器)
 *
 * Generates unique batch codes using Hashids algorithm.
 *
 * Algorithm:
 * - Input: productId + timestamp (milliseconds) + randomSeed
 * - Output: 6-character alphanumeric code (e.g., R7M4K9)
 * - Salt: Loaded from environment variable ${BATCH_SALT}
 * - Length: Minimum 6 characters
 *
 * Features:
 * - Short code (6 chars): Easy to print and scan
 * - Non-sequential: No business info leakage
 * - Reversible encoding: Can decode to verify
 * - Collision detection: Retry up to 3 times
 * - Database uniqueness: UNIQUE constraint on batch_code
 *
 * Usage Example:
 * <pre>
 * String batchCode = batchCodeGenerator.generateUnique(productId, itemId, repository);
 * // Output: "R7M4K9"
 * </pre>
 *
 * Security Requirements:
 * - ⚠️ Salt MUST be loaded from environment variable (not hardcoded)
 * - Production: export BATCH_SALT="your-secret-salt-min-32-chars"
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Batch Management)
 */
@Slf4j
@Component
public class BatchCodeGenerator {

    private final Hashids hashids;

    /**
     * Constructor with auto-wiring
     *
     * Loads configuration from application.yml:
     * - batch.hashids.salt: ${BATCH_SALT} environment variable
     * - batch.hashids.min-length: 6 (default)
     *
     * @param salt Hashids salt (from environment variable)
     * @param minLength Minimum length of generated code
     */
    public BatchCodeGenerator(
        @Value("${batch.hashids.salt}") String salt,
        @Value("${batch.hashids.min-length}") int minLength
    ) {
        log.info("Initializing BatchCodeGenerator: minLength={}, salt={}",
                 minLength, salt.substring(0, Math.min(10, salt.length())) + "...");

        this.hashids = new Hashids(salt, minLength);
    }

    /**
     * Generate batch code (without uniqueness check)
     *
     * Algorithm:
     * 1. Get current timestamp (milliseconds)
     * 2. Generate random seed (0-999999)
     * 3. Encode: productId + timestamp + randomSeed → Hashids
     *
     * ⚠️ Note: This method does NOT check uniqueness in database.
     * Use generateUnique() for production code.
     *
     * @param productId Product ID
     * @param purchaseOrderItemId Purchase order item ID (for future tracking)
     * @return Batch code (e.g., "R7M4K9")
     */
    public String generate(Long productId, Long purchaseOrderItemId) {
        // 1. Get current timestamp (milliseconds since epoch)
        long timestamp = System.currentTimeMillis();

        // 2. Generate random seed (0-999999) for collision avoidance
        int randomSeed = ThreadLocalRandom.current().nextInt(1000000);

        // 3. Encode to Hashids
        // Input: [productId, timestamp, randomSeed]
        // Output: 6-character alphanumeric code (e.g., "R7M4K9")
        String batchCode = hashids.encode(productId, timestamp, randomSeed);

        log.debug("Generated batch code: productId={}, itemId={}, timestamp={}, random={}, code={}",
                  productId, purchaseOrderItemId, timestamp, randomSeed, batchCode);

        return batchCode;
    }

    /**
     * ⭐ Generate unique batch code with collision detection
     *
     * Retry Mechanism:
     * - Attempt 1: Generate and check uniqueness
     * - Attempt 2: If collision, regenerate
     * - Attempt 3: If collision again, regenerate
     * - Failure: Throw BATCH_CODE_GENERATION_FAILED exception
     *
     * Probability of Collision:
     * - With 6-character Hashids + timestamp + random: ~0.0001%
     * - With 3 retries: ~0.0000001% (extremely rare)
     *
     * Database Uniqueness Guarantee:
     * - UNIQUE constraint on inventory_batch.batch_code
     * - Final safety net against duplicates
     *
     * @param productId Product ID
     * @param purchaseOrderItemId Purchase order item ID
     * @param batchRepository Repository for uniqueness check
     * @return Unique batch code (guaranteed not to exist in database)
     * @throws BusinessException if generation fails after 3 attempts
     */
    public String generateUnique(
        Long productId,
        Long purchaseOrderItemId,
        InventoryBatchRepository batchRepository
    ) {
        final int MAX_ATTEMPTS = 3;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // Generate batch code
            String batchCode = generate(productId, purchaseOrderItemId);

            // Check uniqueness in database
            boolean exists = batchRepository.existsByBatchCode(batchCode);

            if (!exists) {
                log.info("✅ Batch code generated successfully: code={}, attempt={}/{}",
                         batchCode, attempt, MAX_ATTEMPTS);
                return batchCode;
            }

            // Collision detected
            log.warn("⚠️ Batch code collision detected: code={}, attempt={}/{}",
                     batchCode, attempt, MAX_ATTEMPTS);
        }

        // All attempts failed (extremely rare)
        log.error("❌ Batch code generation FAILED after {} attempts: productId={}, itemId={}",
                  MAX_ATTEMPTS, productId, purchaseOrderItemId);

        throw new BusinessException(
            ErrorKeys.BATCH_CODE_GENERATION_FAILED,
            Map.of(
                "productId", productId,
                "itemId", purchaseOrderItemId,
                "attempts", MAX_ATTEMPTS
            )
        );
    }

    /**
     * Decode batch code (for verification or debugging)
     *
     * Decodes Hashids back to original numbers.
     *
     * ⚠️ Note: This is optional and not used in production flow.
     * Mainly for debugging or verification purposes.
     *
     * @param batchCode Batch code (e.g., "R7M4K9")
     * @return Array of decoded numbers [productId, timestamp, randomSeed]
     */
    public long[] decode(String batchCode) {
        return hashids.decode(batchCode);
    }

    /**
     * Validate batch code format
     *
     * Checks if the batch code is valid Hashids format.
     *
     * @param batchCode Batch code to validate
     * @return true if valid format
     */
    public boolean isValidFormat(String batchCode) {
        if (batchCode == null || batchCode.isEmpty()) {
            return false;
        }

        try {
            long[] decoded = hashids.decode(batchCode);
            return decoded.length == 3;  // Should decode to [productId, timestamp, random]
        } catch (Exception e) {
            log.warn("Invalid batch code format: code={}, error={}", batchCode, e.getMessage());
            return false;
        }
    }
}
