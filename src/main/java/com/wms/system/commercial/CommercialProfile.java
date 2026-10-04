package com.wms.system.commercial;
import com.wms.system.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="commercial_profile") @Getter @Setter
public class CommercialProfile extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=200) private String name;
    @Column(length=255) private String address;
    @Column(length=50) private String phone;
    @Column(length=100) private String email;
    @Column(length=2000) private String terms;
}
