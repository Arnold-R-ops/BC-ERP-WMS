package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transfer_receipt")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferReceipt extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_order_id", nullable = false, updatable = false)
    private TransferOrder transferOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private TransferWarehouseTask task;

    @Column(name = "receipt_number", nullable = false, length = 40, updatable = false)
    private String receiptNumber;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "received_by", nullable = false, updatable = false)
    private Long receivedBy;

    @Column(name = "received_by_name", nullable = false, length = 100, updatable = false)
    private String receivedByName;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(length = 500, updatable = false)
    private String remark;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransferReceiptLine> lines = new ArrayList<>();

    public void addLine(TransferReceiptLine line) {
        lines.add(line);
        line.setReceipt(this);
    }
}
