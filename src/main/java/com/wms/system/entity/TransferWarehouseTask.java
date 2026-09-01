package com.wms.system.entity;

import com.wms.system.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_warehouse_task")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferWarehouseTask extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_order_id", nullable = false, updatable = false)
    private TransferOrder transferOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Column(name = "task_number", nullable = false, length = 40, updatable = false)
    private String taskNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransferTaskDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransferTaskStatus status;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 50;

    @Column(name = "expected_from")
    private LocalDateTime expectedFrom;

    @Column(name = "expected_to")
    private LocalDateTime expectedTo;

    @Column(length = 100)
    private String assignee;

    @Column(name = "team_name", length = 100)
    private String teamName;

    @Column(name = "operation_area", length = 100)
    private String operationArea;

    @Column(name = "warehouse_remark", length = 500)
    private String warehouseRemark;
}
