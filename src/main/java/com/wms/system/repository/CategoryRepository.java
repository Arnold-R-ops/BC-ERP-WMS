package com.wms.system.repository;

import com.wms.system.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndCompanyId(Long id, Long companyId);

    List<Category> findAllByCompanyIdOrderBySortOrderAscIdAsc(Long companyId);

    List<Category> findAllByCompanyIdAndEnabledTrueOrderBySortOrderAscIdAsc(Long companyId);

    List<Category> findAllByCompanyIdAndParent_IdOrderBySortOrderAscIdAsc(Long companyId, Long parentId);

    boolean existsByCompanyIdAndCategoryCodeIgnoreCase(Long companyId, String categoryCode);

    boolean existsByParent_Id(Long parentId);

    @Query(value = """
        WITH RECURSIVE category_descendants AS (
            SELECT c.*
            FROM categories c
            WHERE c.company_id = :companyId AND c.parent_id = :rootId
            UNION ALL
            SELECT child.*
            FROM categories child
            JOIN category_descendants parent ON child.parent_id = parent.id
            WHERE child.company_id = :companyId
        )
        SELECT * FROM category_descendants
        ORDER BY id
        """, nativeQuery = true)
    List<Category> findAllDescendants(
        @Param("companyId") Long companyId,
        @Param("rootId") Long rootId
    );

    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM Category c
        WHERE c.companyId = :companyId
          AND LOWER(c.categoryName) = LOWER(:categoryName)
          AND ((:parentId IS NULL AND c.parent IS NULL) OR c.parent.id = :parentId)
          AND (:excludeId IS NULL OR c.id <> :excludeId)
        """)
    boolean existsSiblingName(
        @Param("companyId") Long companyId,
        @Param("parentId") Long parentId,
        @Param("categoryName") String categoryName,
        @Param("excludeId") Long excludeId
    );
}
