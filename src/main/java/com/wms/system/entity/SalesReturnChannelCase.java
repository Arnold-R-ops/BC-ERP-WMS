package com.wms.system.entity;

import com.wms.system.entity.enums.RmaChannelCaseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_return_channel_case", uniqueConstraints =
    @UniqueConstraint(name = "uk_sales_return_channel_case_external",
        columnNames = {"company_id", "channel", "store_identifier", "external_case_id"}),
    indexes = @Index(name = "idx_sales_return_channel_case_queue",
        columnList = "company_id,status,received_at,id"))
public class SalesReturnChannelCase extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(name = "store_identifier", nullable = false, length = 255)
    private String storeIdentifier;

    @Column(name = "external_case_id", nullable = false, length = 120)
    private String externalCaseId;

    @Column(name = "external_order_id", length = 120)
    private String externalOrderId;

    @Column(name = "raw_event_id", nullable = false)
    private Long rawEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RmaChannelCaseStatus status;

    @Column(name = "rma_id")
    private Long rmaId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;
}
