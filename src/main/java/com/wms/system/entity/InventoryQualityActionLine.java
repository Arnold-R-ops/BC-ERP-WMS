package com.wms.system.entity;

import com.wms.system.entity.enums.InventoryQualityStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_quality_action_line")
public class InventoryQualityActionLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false)
    private InventoryQualityAction action;

    @Column(name = "source_batch_id", nullable = false)
    private Long sourceBatchId;

    @Column(name = "result_batch_id")
    private Long resultBatchId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "product_sku_code", nullable = false, length = 100)
    private String productSkuCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "source_batch_code", nullable = false, length = 50)
    private String sourceBatchCode;

    @Column(name = "result_batch_code", length = 50)
    private String resultBatchCode;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "warehouse_name", nullable = false, length = 200)
    private String warehouseName;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "location_code", nullable = false, length = 50)
    private String locationCode;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "reallocated_reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reallocatedReservedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 30)
    private InventoryQualityStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private InventoryQualityStatus toStatus;
}
