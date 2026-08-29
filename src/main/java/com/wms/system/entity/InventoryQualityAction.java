package com.wms.system.entity;

import com.wms.system.entity.enums.InventoryQualityActionStatus;
import com.wms.system.entity.enums.InventoryQualityActionType;
import com.wms.system.entity.enums.InventoryQualityHoldType;
import com.wms.system.entity.enums.InventoryQualityReleaseMode;
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
@Table(name = "inventory_quality_action", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_quality_action_company_number", columnNames = {"company_id", "action_number"}),
    @UniqueConstraint(name = "uk_inventory_quality_action_company_idempotency", columnNames = {"company_id", "idempotency_key"})
})
public class InventoryQualityAction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "action_number", nullable = false, length = 40)
    private String actionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private InventoryQualityActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_type", length = 20)
    private InventoryQualityHoldType holdType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryQualityActionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_mode_snapshot", length = 30)
    private InventoryQualityReleaseMode releaseModeSnapshot;

    @Column(name = "reason_code", nullable = false, length = 80)
    private String reasonCode;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "recall_notice_number", length = 120)
    private String recallNoticeNumber;

    @Column(name = "external_reference", length = 120)
    private String externalReference;

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

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @OneToMany(mappedBy = "action", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<InventoryQualityActionLine> lines = new ArrayList<>();
}
