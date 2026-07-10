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
    name = "customer_fact_summary",
    indexes = {
        @Index(name = "idx_customer_fact_summary_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_fact_summary_refreshed", columnList = "refreshed_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_fact_summary_company_customer", columnNames = {"company_id", "customer_id"})
    }
)
public class CustomerFactSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "total_order_count", nullable = false)
    @Builder.Default
    private Long totalOrderCount = 0L;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "last_order_date")
    private LocalDate lastOrderDate;

    @Column(name = "average_interval_days", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal averageIntervalDays = BigDecimal.ZERO;

    @Column(name = "refreshed_at", nullable = false)
    private LocalDateTime refreshedAt;
}
