package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "customer_product_summary",
    indexes = {
        @Index(
            name = "idx_customer_product_summary_customer_rank",
            columnList = "company_id,customer_id,total_amount"
        ),
        @Index(name = "idx_customer_product_summary_sku", columnList = "company_id,product_sku_id"),
        @Index(name = "idx_customer_product_summary_refreshed", columnList = "refreshed_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_customer_product_summary_scope",
            columnNames = {"company_id", "customer_id", "product_sku_id"}
        )
    }
)
public class CustomerProductSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "total_order_count", nullable = false)
    @Builder.Default
    private Long totalOrderCount = 0L;

    @Column(name = "total_quantity", nullable = false)
    @Builder.Default
    private Long totalQuantity = 0L;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "first_order_date")
    private LocalDate firstOrderDate;

    @Column(name = "last_order_date")
    private LocalDate lastOrderDate;

    @Column(name = "average_interval_days", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal averageIntervalDays = BigDecimal.ZERO;

    @Column(name = "refreshed_at", nullable = false)
    private LocalDateTime refreshedAt;
}
