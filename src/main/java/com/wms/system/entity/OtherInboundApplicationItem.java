package com.wms.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
    name = "other_inbound_application_item",
    indexes = @Index(name = "idx_other_inbound_item_sku", columnList = "company_id,product_sku_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherInboundApplicationItem extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private OtherInboundApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku_id", nullable = false)
    private ProductSku productSku;

    @Column(name = "planned_quantity", nullable = false)
    private Integer plannedQuantity;

    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "closed_quantity", nullable = false)
    @Builder.Default
    private Integer closedQuantity = 0;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "external_batch_code", length = 100)
    private String externalBatchCode;

    @Column(length = 500)
    private String remark;

    public int remainingQuantity() {
        return plannedQuantity - receivedQuantity - closedQuantity;
    }
}
