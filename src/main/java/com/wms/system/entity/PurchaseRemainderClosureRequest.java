package com.wms.system.entity;

import com.wms.system.entity.enums.PurchaseRemainderClosureStatus;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "purchase_remainder_closure_request",
    indexes = {
        @Index(
            name = "idx_purchase_remainder_closure_asn_status",
            columnList = "company_id,purchase_asn_id,status"
        ),
        @Index(
            name = "idx_purchase_remainder_closure_status_created",
            columnList = "company_id,status,created_at"
        )
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRemainderClosureRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_asn_id", nullable = false, updatable = false)
    private PurchaseAsn purchaseAsn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseRemainderClosureStatus status;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "requested_by_name", nullable = false, length = 100, updatable = false)
    private String requestedByName;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_name", length = 100)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;
}
