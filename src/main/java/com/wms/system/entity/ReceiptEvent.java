package com.wms.system.entity;

import com.wms.system.entity.enums.ReceiptEventStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "receipt_event",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_receipt_event_company_number", columnNames = {"company_id", "event_number"}),
        @UniqueConstraint(name = "uk_receipt_event_company_idempotency", columnNames = {"company_id", "idempotency_key"})
    },
    indexes = @Index(name = "idx_receipt_event_task_time", columnList = "company_id,receiving_task_id,received_at")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_number", nullable = false, length = 50, updatable = false)
    private String eventNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_task_id", nullable = false, updatable = false)
    private ReceivingTask receivingTask;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptEventStatus status;

    @Column(name = "received_by", nullable = false, updatable = false)
    private Long receivedBy;

    @Column(name = "received_by_name", nullable = false, length = 100, updatable = false)
    private String receivedByName;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(length = 500)
    private String remark;

    @OneToMany(mappedBy = "receiptEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReceiptEventItem> items = new ArrayList<>();

    public void addItem(ReceiptEventItem item) {
        items.add(item);
        item.setReceiptEvent(this);
    }
}
