package com.wms.system.entity;

import com.wms.system.entity.enums.ReceivingArrivalDisposition;
import com.wms.system.entity.enums.ReceivingArrivalExceptionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "receiving_arrival_exception")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingArrivalException extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exception_number", nullable = false, length = 40, updatable = false)
    private String exceptionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_task_id", nullable = false, updatable = false)
    private ReceivingTask receivingTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_sku_id", updatable = false)
    private ProductSku productSku;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 30, updatable = false)
    private ReceivingArrivalExceptionType exceptionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private ReceivingArrivalDisposition disposition;

    @Column(name = "observed_quantity", nullable = false, updatable = false)
    private Integer observedQuantity;

    @Column(name = "reason_detail", length = 500, updatable = false)
    private String reasonDetail;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "recorded_by_name", nullable = false, length = 100, updatable = false)
    private String recordedByName;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;
}
