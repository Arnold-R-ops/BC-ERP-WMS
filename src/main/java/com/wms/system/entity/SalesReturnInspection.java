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
@Table(name = "sales_return_inspection", uniqueConstraints =
    @UniqueConstraint(name = "uk_sales_return_inspection_company_number", columnNames = {"company_id", "inspection_number"}))
public class SalesReturnInspection extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rma_id", nullable = false)
    private SalesReturnRma rma;

    @Column(name = "inspection_number", nullable = false, length = 40)
    private String inspectionNumber;

    @Column(name = "inspected_by", nullable = false)
    private Long inspectedBy;

    @Column(name = "inspected_by_name", nullable = false, length = 100)
    private String inspectedByName;

    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesReturnInspectionLine> lines = new ArrayList<>();

    public void addLine(SalesReturnInspectionLine line) {
        lines.add(line);
        line.setInspection(this);
    }
}
