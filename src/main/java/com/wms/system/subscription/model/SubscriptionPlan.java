package com.wms.system.subscription.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "subscription_plans",
    uniqueConstraints = @UniqueConstraint(name = "uk_subscription_plans_code", columnNames = "plan_code")
)
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 30)
    private PlanCode planCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "trial_days")
    private Integer trialDays;

    @Column(name = "fallback_plan_code", length = 30)
    private String fallbackPlanCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void initializeTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void refreshUpdatedAt() {
        updatedAt = OffsetDateTime.now();
    }
}
