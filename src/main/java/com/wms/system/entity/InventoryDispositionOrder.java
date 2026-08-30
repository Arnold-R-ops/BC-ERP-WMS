package com.wms.system.entity;

import com.wms.system.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_disposition_order", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_disposition_company_number", columnNames = {"company_id", "disposition_number"}),
    @UniqueConstraint(name = "uk_inventory_disposition_company_idempotency", columnNames = {"company_id", "idempotency_key"})
})
public class InventoryDispositionOrder extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "disposition_number", nullable = false, length = 40)
    private String dispositionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition_type", nullable = false, length = 30)
    private InventoryDispositionType dispositionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryDispositionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode_snapshot", length = 30)
    private InventoryDispositionReviewMode reviewModeSnapshot;

    @Column(name = "reason_code", nullable = false, length = 80)
    private String reasonCode;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "external_reference", length = 120)
    private String externalReference;

    @Column(name = "recall_notice_number", length = 120)
    private String recallNoticeNumber;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_by_name", nullable = false, length = 100)
    private String requestedByName;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_name", length = 100)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_by_name", length = 100)
    private String cancelledByName;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @Builder.Default
    private List<InventoryDispositionLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @Builder.Default
    private List<InventoryDispositionExecution> executions = new ArrayList<>();
}
