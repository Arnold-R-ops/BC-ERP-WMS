package com.wms.system.commercial;
import com.wms.system.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="commercial_receipt") @Getter @Setter
public class CommercialReceipt extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="document_id", nullable=false) private Long documentId;
    @Column(nullable=false, length=12) private String kind;
    @Column(name="reverses_id") private Long reversesId;
    @Column(name="request_key", nullable=false, length=36) private String requestKey;
    @Column(name="request_hash", nullable=false, length=64) private String requestHash;
    @Column(nullable=false, precision=15, scale=2) private BigDecimal amount;
    @Column(name="payment_date", nullable=false) private LocalDate paymentDate;
    @Column(nullable=false, length=20) private String method;
    @Column(length=120) private String reference;
    @Column(length=500) private String note;
    @Column(name="created_by", nullable=false) private Long createdBy;
}
