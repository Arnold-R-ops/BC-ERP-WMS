package com.wms.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Immutable, migration-managed identity evidence for an historical test order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "historical_test_data_registry")
public class HistoricalTestDataRegistry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    @Column(name = "source_version", nullable = false, length = 30)
    private String sourceVersion;

    @Column(name = "registration_reason", nullable = false, length = 500)
    private String registrationReason;

    @Column(name = "registered_by", nullable = false, length = 100)
    private String registeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
