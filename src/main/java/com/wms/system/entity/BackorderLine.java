package com.wms.system.entity;

import com.wms.system.entity.enums.BackorderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "backorder_line",
    indexes = {
        @Index(name = "idx_backorder_product_status", columnList = "product_sku_id,status"),
        @Index(name = "idx_backorder_sales_order", columnList = "sales_order_id"),
        @Index(name = "idx_backorder_priority_created", columnList = "priority,created_at")
    }
)
public class BackorderLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "sales_order_item_id", nullable = false)
    private Long salesOrderItemId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "remaining_qty", nullable = false)
    private Integer remainingQty;

    @Column(name = "allocated_qty", nullable = false)
    @Builder.Default
    private Integer allocatedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BackorderStatus status = BackorderStatus.OPEN;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean isOpenForAllocation() {
        return status == BackorderStatus.OPEN || status == BackorderStatus.PARTIAL;
    }

    public void allocate(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            return;
        }
        int remaining = remainingQty == null ? 0 : remainingQty;
        if (quantity > remaining) {
            throw new IllegalArgumentException("Allocation exceeds backorder remaining quantity");
        }
        allocatedQty = (allocatedQty == null ? 0 : allocatedQty) + quantity;
        remainingQty = remaining - quantity;
        status = remainingQty == 0 ? BackorderStatus.FULFILLED : BackorderStatus.PARTIAL;
    }
}
