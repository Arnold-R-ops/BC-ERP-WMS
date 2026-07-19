package com.wms.system.entity;

import com.wms.system.entity.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(
    name = "sales_order_shipments",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_sales_order_shipment_tracking",
        columnNames = {"company_id", "sales_order_id", "tracking_no"}
    ),
    indexes = @Index(
        name = "idx_sales_order_shipment_order",
        columnList = "company_id,sales_order_id,status"
    )
)
public class SalesOrderShipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "tracking_no", nullable = false, length = 100)
    private String trackingNo;

    @Column(length = 50)
    private String carrier;

    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ShipmentStatus status = ShipmentStatus.ACTIVE;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    @Column(length = 500)
    private String remark;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
