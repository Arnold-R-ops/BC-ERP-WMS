package com.wms.system.entity;

import com.wms.system.entity.enums.RmaTaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_return_task", uniqueConstraints =
    @UniqueConstraint(name = "uk_sales_return_task_company_rma", columnNames = {"company_id", "rma_id"}))
public class SalesReturnTask extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rma_id", nullable = false)
    private SalesReturnRma rma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "task_number", nullable = false, length = 40)
    private String taskNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RmaTaskStatus status;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 50;

    @Column(name = "expected_from")
    private LocalDateTime expectedFrom;

    @Column(name = "expected_to")
    private LocalDateTime expectedTo;

    @Column(name = "assignee", length = 100)
    private String assignee;

    @Column(name = "team_name", length = 100)
    private String teamName;

    @Column(name = "operation_area", length = 100)
    private String operationArea;

    @Column(name = "warehouse_remark", length = 500)
    private String warehouseRemark;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
