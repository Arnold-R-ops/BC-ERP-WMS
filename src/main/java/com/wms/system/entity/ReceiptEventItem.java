package com.wms.system.entity;

import com.wms.system.entity.enums.InventoryQualityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
    name = "receipt_event_item",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_receipt_event_item_posting",
        columnNames = {"company_id", "posting_id"}
    ),
    indexes = {
        @Index(name = "idx_receipt_event_item_event", columnList = "company_id,receipt_event_id"),
        @Index(name = "idx_receipt_event_item_task_item", columnList = "company_id,receiving_task_item_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptEventItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_event_id", nullable = false, updatable = false)
    private ReceiptEvent receiptEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_task_item_id", nullable = false, updatable = false)
    private ReceivingTaskItem receivingTaskItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku_id", nullable = false, updatable = false)
    private ProductSku productSku;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false, updatable = false)
    private InventoryBatch inventoryBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "batch_code", nullable = false, length = 50, updatable = false)
    private String batchCode;

    @Column(name = "production_date", updatable = false)
    private LocalDate productionDate;

    @Column(name = "expiry_date", updatable = false)
    private LocalDate expiryDate;

    @Column(name = "external_batch_code", length = 100, updatable = false)
    private String externalBatchCode;

    @Column(name = "posting_id", nullable = false, length = 120, updatable = false)
    private String postingId;

    @Column(name = "inspection_required", nullable = false, updatable = false)
    @Builder.Default
    private Boolean inspectionRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status_at_receipt", nullable = false, length = 30, updatable = false)
    @Builder.Default
    private InventoryQualityStatus qualityStatusAtReceipt = InventoryQualityStatus.AVAILABLE;
}
