package com.wms.system.entity;

import com.wms.system.entity.enums.ReservationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Inventory reservation ledger.
 *
 * Reservations separate "commercial approval" from "physical inventory
 * deduction": approval reserves stock, outbound confirmation consumes it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "inventory_reservations",
    indexes = {
        @Index(name = "idx_reservation_order", columnList = "sales_order_id"),
        @Index(name = "idx_reservation_item", columnList = "sales_order_item_id"),
        @Index(name = "idx_reservation_batch", columnList = "inventory_batch_id"),
        @Index(name = "idx_reservation_status", columnList = "status")
    }
)
public class InventoryReservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @NotNull
    @Column(name = "sales_order_item_id", nullable = false)
    private Long salesOrderItemId;

    @NotNull
    @Column(name = "inventory_batch_id", nullable = false)
    private Long inventoryBatchId;

    @NotNull
    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @NotNull
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @NotNull
    @Min(1)
    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty;

    @NotNull
    @Min(0)
    @Column(name = "consumed_qty", nullable = false)
    @Builder.Default
    private Integer consumedQty = 0;

    @NotNull
    @Min(0)
    @Column(name = "released_qty", nullable = false)
    @Builder.Default
    private Integer releasedQty = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "source_type", nullable = false, length = 40)
    @Builder.Default
    private String sourceType = "SALES_ORDER";

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    public int getOpenQty() {
        return reservedQty - consumedQty - releasedQty;
    }

    public boolean hasOpenQuantity() {
        return getOpenQty() > 0 && status != ReservationStatus.CONSUMED
            && status != ReservationStatus.RELEASED
            && status != ReservationStatus.EXPIRED;
    }

    public void consume(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Reservation consume quantity must be positive");
        }
        if (quantity > getOpenQty()) {
            throw new IllegalArgumentException("Reservation open quantity is insufficient");
        }
        this.consumedQty += quantity;
        this.status = getOpenQty() == 0
            ? ReservationStatus.CONSUMED
            : ReservationStatus.PARTIALLY_CONSUMED;
    }

    public int releaseRemaining() {
        int openQty = getOpenQty();
        if (openQty > 0) {
            this.releasedQty += openQty;
        }
        this.status = ReservationStatus.RELEASED;
        return openQty;
    }
}
