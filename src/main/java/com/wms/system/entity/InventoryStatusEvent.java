package com.wms.system.entity;

import com.wms.system.entity.enums.InventoryQualityStatus;
import jakarta.persistence.*;
import lombok.*;

/** Immutable evidence for a quality-state transition. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "inventory_status_event",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_inventory_status_event_company_idempotency",
            columnNames = {"company_id", "idempotency_key"}
        )
    },
    indexes = {
        @Index(
            name = "idx_inventory_status_event_batch_created",
            columnList = "company_id,inventory_batch_id,created_at"
        ),
        @Index(
            name = "idx_inventory_status_event_source",
            columnList = "company_id,source_type,source_id"
        )
    }
)
public class InventoryStatusEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_batch_id", nullable = false)
    private Long inventoryBatchId;

    @Column(name = "batch_code", nullable = false, length = 50)
    private String batchCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 30)
    private InventoryQualityStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private InventoryQualityStatus toStatus;

    @Column(name = "quantity_snapshot", nullable = false)
    private Integer quantitySnapshot;

    @Column(name = "source_type", nullable = false, length = 60)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @Column(name = "reason_code", nullable = false, length = 80)
    private String reasonCode;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
}
