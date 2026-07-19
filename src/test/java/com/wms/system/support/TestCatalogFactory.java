package com.wms.system.support;

import com.wms.system.entity.Category;
import com.wms.system.repository.CategoryRepository;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.concurrent.atomic.AtomicLong;

/** Test-only fixtures for the Category -> Product -> ProductSku hierarchy. */
public final class TestCatalogFactory {

    private static final AtomicLong SEQUENCE = new AtomicLong(90_000_000L);

    private TestCatalogFactory() {
    }

    public static String nextSkuCode() {
        return "SKU" + String.format("%08d", SEQUENCE.getAndIncrement());
    }

    public static Category saveLeafCategory(CategoryRepository repository) {
        long suffix = SEQUENCE.getAndIncrement();
        Category root = repository.save(rootCategory(suffix));
        return repository.save(leafCategory(suffix, root));
    }

    public static Category persistLeafCategory(TestEntityManager entityManager) {
        long suffix = SEQUENCE.getAndIncrement();
        Category root = rootCategory(suffix);
        entityManager.persist(root);
        Category leaf = leafCategory(suffix, root);
        entityManager.persist(leaf);
        return leaf;
    }

    private static Category rootCategory(long suffix) {
        return Category.builder()
            .categoryCode("TEST-ROOT-" + suffix)
            .categoryName("Test Root " + suffix)
            .enabled(true)
            .build();
    }

    private static Category leafCategory(long suffix, Category root) {
        return Category.builder()
            .categoryCode("TEST-LEAF-" + suffix)
            .categoryName("Test Leaf " + suffix)
            .parent(root)
            .enabled(true)
            .build();
    }
}
