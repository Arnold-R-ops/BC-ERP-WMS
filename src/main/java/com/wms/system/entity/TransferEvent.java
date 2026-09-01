package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_order_id", nullable = false, updatable = false)
    private TransferOrder transferOrder;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payloadJson;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(name = "actor_name", nullable = false, length = 100, updatable = false)
    private String actorName;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
