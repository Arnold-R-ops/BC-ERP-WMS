package com.wms.system.identity.repository;

import com.wms.system.identity.model.MembershipStatus;
import com.wms.system.identity.model.TenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, Long> {
    Optional<TenantMembership> findByIdentityIdAndTenantId(Long identityId, Long tenantId);

    List<TenantMembership> findByIdentityIdAndStatusIn(
        Long identityId,
        List<MembershipStatus> statuses
    );
}
