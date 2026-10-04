package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "warehouse_stock_threshold", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "product_sku_id", "warehouse_id"}))
@Getter @Setter @NoArgsConstructor
public class WarehouseStockThreshold extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku_id", nullable = false, updatable = false)
    private ProductSku productSku;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;
    @Column(name = "min_stock", nullable = false) private Integer minStock;
}
