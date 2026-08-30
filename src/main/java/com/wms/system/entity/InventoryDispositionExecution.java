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
@Table(name = "inventory_disposition_execution", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_disposition_execution_company_number", columnNames = {"company_id", "execution_number"}),
    @UniqueConstraint(name = "uk_inventory_disposition_execution_company_idempotency", columnNames = {"company_id", "idempotency_key"})
})
public class InventoryDispositionExecution extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disposition_order_id", nullable = false)
    private InventoryDispositionOrder order;

    @Column(name = "execution_number", nullable = false, length = 50)
    private String executionNumber;

    @Column(name = "executed_by", nullable = false)
    private Long executedBy;

    @Column(name = "executed_by_name", nullable = false, length = 100)
    private String executedByName;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "execution_note", length = 500)
    private String executionNote;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @Builder.Default
    private List<InventoryDispositionExecutionLine> lines = new ArrayList<>();
}
