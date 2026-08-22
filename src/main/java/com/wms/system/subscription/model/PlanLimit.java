package com.wms.system.subscription.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "plan_limits",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_plan_limits_plan_key",
        columnNames = {"plan_id", "limit_key"}
    )
)
public class PlanLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "limit_key", nullable = false, length = 80)
    private String limitKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_kind", nullable = false, length = 20)
    private LimitKind limitKind;

    @Column(name = "enabled_value")
    private Boolean enabledValue;

    @Column(name = "numeric_value")
    private Long numericValue;
}
