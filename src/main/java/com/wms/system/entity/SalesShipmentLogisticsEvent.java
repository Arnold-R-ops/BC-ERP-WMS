package com.wms.system.entity;

import com.wms.system.entity.enums.SalesLogisticsStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Append-only audit evidence for every shipment logistics transition. */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "sales_shipment_logistics_events", indexes = {
    @Index(name = "idx_sales_logistics_events_shipment", columnList = "company_id,shipment_id,id")
})
public class SalesShipmentLogisticsEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private SalesLogisticsStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private SalesLogisticsStatus toStatus;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_by_name", nullable = false, length = 100)
    private String changedByName;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
