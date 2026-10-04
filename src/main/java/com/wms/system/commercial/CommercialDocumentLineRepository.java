package com.wms.system.commercial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CommercialDocumentLineRepository extends JpaRepository<CommercialDocumentLine,Long> {
    List<CommercialDocumentLine> findByDocumentIdOrderByPosition(Long documentId);
    void deleteByDocumentId(Long documentId);
}
