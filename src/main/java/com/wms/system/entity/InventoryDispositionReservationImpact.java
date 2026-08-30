package com.wms.system.entity;

import com.wms.system.entity.enums.InventoryDispositionReservationImpactType;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_disposition_reservation_impact")
public class InventoryDispositionReservationImpact extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disposition_line_id", nullable = false)
    private Long dispositionLineId;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "outbound_task_id")
    private Long outboundTaskId;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "sales_order_item_id", nullable = false)
    private Long salesOrderItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false, length = 30)
    private InventoryDispositionReservationImpactType impactType;

    @Column(name = "from_batch_id", nullable = false)
    private Long fromBatchId;

    @Column(name = "to_batch_id")
    private Long toBatchId;

    @Column(nullable = false)
    private Integer quantity;
}
