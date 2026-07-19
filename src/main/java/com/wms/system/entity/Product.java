package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Product master (SPU). Inventory and transactions never reference this table
 * directly; they reference {@link ProductSku}.
 */
@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_products_company_category", columnList = "company_id,category_id"),
        @Index(name = "idx_products_company_name", columnList = "company_id,product_name")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_products_company_code", columnNames = {"company_id", "product_code"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"category", "skus"})
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @NotBlank
    @Size(max = 50)
    @Column(name = "product_code", nullable = false, length = 50, updatable = false)
    private String productCode;

    @NotBlank
    @Size(max = 200)
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_products_category"))
    private Category category;

    @Size(max = 100)
    @Column(length = 100)
    private String brand;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @Builder.Default
    private List<ProductSku> skus = new ArrayList<>();

    public void addSku(ProductSku sku) {
        skus.add(sku);
        sku.setProduct(this);
    }

    public void removeSku(ProductSku sku) {
        skus.remove(sku);
        sku.setProduct(null);
    }

    public long getEnabledSkuCount() {
        return skus.stream().filter(ProductSku::getEnabled).count();
    }

    public boolean isAvailable() {
        return Boolean.TRUE.equals(enabled) && getEnabledSkuCount() > 0;
    }
}
