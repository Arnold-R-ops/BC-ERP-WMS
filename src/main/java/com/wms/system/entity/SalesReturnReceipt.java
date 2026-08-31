package com.wms.system.entity;

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
@Table(name = "sales_return_receipt", uniqueConstraints =
    @UniqueConstraint(name = "uk_sales_return_receipt_company_number", columnNames = {"company_id", "receipt_number"}))
public class SalesReturnReceipt extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rma_id", nullable = false)
    private SalesReturnRma rma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private SalesReturnTask task;

    @Column(name = "receipt_number", nullable = false, length = 40)
    private String receiptNumber;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Column(name = "received_by_name", nullable = false, length = 100)
    private String receivedByName;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(length = 500)
    private String remark;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesReturnReceiptLine> lines = new ArrayList<>();

    public void addLine(SalesReturnReceiptLine line) {
        lines.add(line);
        line.setReceipt(this);
    }
}
