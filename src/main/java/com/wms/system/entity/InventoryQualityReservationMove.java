package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_quality_reservation_move")
public class InventoryQualityReservationMove extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_line_id", nullable = false)
    private Long actionLineId;
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;
    @Column(name = "outbound_task_id", nullable = false)
    private Long outboundTaskId;
    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;
    @Column(name = "sales_order_item_id", nullable = false)
    private Long salesOrderItemId;
    @Column(name = "from_batch_id", nullable = false)
    private Long fromBatchId;
    @Column(name = "to_batch_id", nullable = false)
    private Long toBatchId;
    @Column(name = "from_location_id", nullable = false)
    private Long fromLocationId;
    @Column(name = "to_location_id", nullable = false)
    private Long toLocationId;
    @Column(nullable = false)
    private Integer quantity;
}
