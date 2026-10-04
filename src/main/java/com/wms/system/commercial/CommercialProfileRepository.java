package com.wms.system.commercial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CommercialProfileRepository extends JpaRepository<CommercialProfile,Long> {
    Optional<CommercialProfile> findByCompanyId(Long companyId);
}
