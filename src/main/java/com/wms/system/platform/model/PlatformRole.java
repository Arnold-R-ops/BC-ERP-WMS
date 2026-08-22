package com.wms.system.platform.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "platform_roles",
    uniqueConstraints = @UniqueConstraint(name = "uk_platform_roles_code", columnNames = "role_code")
)
public class PlatformRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
}
