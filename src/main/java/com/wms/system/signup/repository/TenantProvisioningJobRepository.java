package com.wms.system.signup.repository;

import com.wms.system.signup.model.TenantProvisioningJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantProvisioningJobRepository extends JpaRepository<TenantProvisioningJob, Long> {
    Optional<TenantProvisioningJob> findBySignupRequestId(Long signupRequestId);
}
