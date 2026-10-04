package com.wms.system.commercial;
import com.wms.system.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="commercial_document_line") @Getter @Setter
public class CommercialDocumentLine extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="document_id", nullable=false) private Long documentId;
    @Column(nullable=false) private Integer position;
    @Column(name="product_sku_id", nullable=false) private Long productSkuId;
    @Column(name="sku_code", nullable=false, length=50) private String skuCode;
    @Column(nullable=false, length=300) private String description;
    @Column(nullable=false, length=20) private String unit;
    @Column(nullable=false) private Integer quantity;
    @Column(name="unit_price", nullable=false, precision=10, scale=2) private BigDecimal unitPrice;
    @Column(nullable=false, precision=15, scale=2) private BigDecimal amount;
}
