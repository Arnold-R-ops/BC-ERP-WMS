package com.wms.system.commercial;
import com.wms.system.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="commercial_document") @Getter @Setter
public class CommercialDocument extends BaseEntity {
    @Version @Column(nullable=false) private Long version;
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=12) private String kind;
    @Column(nullable=false, length=12) private String status;
    @Column(name="request_key", nullable=false, length=36) private String requestKey;
    @Column(name="request_hash", nullable=false, length=64) private String requestHash;
    @Column(name="customer_id", nullable=false) private Long customerId;
    @Column(name="sales_order_id") private Long salesOrderId;
    @Column(name="created_by", nullable=false) private Long createdBy;
    @Column(name="customer_name", nullable=false, length=200) private String customerName;
    @Column(name="billing_address", length=255) private String billingAddress;
    @Column(name="shipping_address", length=255) private String shippingAddress;
    @Column(name="company_name", nullable=false, length=200) private String companyName;
    @Column(name="company_address", length=255) private String companyAddress;
    @Column(name="company_phone", length=50) private String companyPhone;
    @Column(name="company_email", length=100) private String companyEmail;
    @Column(length=2000) private String terms;
    @Column(name="document_date", nullable=false) private LocalDate documentDate;
    @Column(name="due_date", nullable=false) private LocalDate dueDate;
    @Column(nullable=false, precision=15, scale=2) private BigDecimal total;
    @Column(name="close_reason", length=500) private String closeReason;
}
