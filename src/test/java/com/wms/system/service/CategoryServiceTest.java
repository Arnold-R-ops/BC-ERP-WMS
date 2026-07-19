package com.wms.system.service;

import com.wms.system.dto.category.CategoryResponse;
import com.wms.system.dto.category.CreateCategoryRequest;
import com.wms.system.entity.Category;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createRootNormalizesCodeAndDefaults() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(10L);
            return category;
        });

        CategoryResponse response = categoryService.create(new CreateCategoryRequest(
            " food-dry ", "  干货  ", null, null, null
        ));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.categoryCode()).isEqualTo("FOOD-DRY");
        assertThat(response.categoryName()).isEqualTo("干货");
        assertThat(response.level()).isEqualTo(1);
        assertThat(response.sortOrder()).isZero();
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(categoryRepository.existsByCompanyIdAndCategoryCodeIgnoreCase(1L, "FOOD"))
            .thenReturn(true);

        assertBusinessError(
            () -> categoryService.create(new CreateCategoryRequest("FOOD", "食品", null, 0, null)),
            ErrorKeys.CATEGORY_ALREADY_EXISTS
        );
    }

    @Test
    void createSupportsThirdLevel() {
        Category root = category(1L, "ROOT", null, true);
        Category levelTwo = category(2L, "LEVEL_TWO", root, true);
        when(categoryRepository.findByIdAndCompanyId(2L, 1L)).thenReturn(Optional.of(levelTwo));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(3L);
            return category;
        });

        CategoryResponse response = categoryService.create(
            new CreateCategoryRequest("LEVEL3", "三级", 2L, 0, null)
        );

        assertThat(response.level()).isEqualTo(3);
        assertThat(response.parentId()).isEqualTo(2L);
    }

    @Test
    void moveRejectsMovingCategoryIntoItsDescendant() {
        Category category = category(1L, "BEVERAGE", null, true);
        Category newParent = category(2L, "FOOD", category, true);
        when(categoryRepository.findByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndCompanyId(2L, 1L)).thenReturn(Optional.of(newParent));

        assertBusinessError(() -> categoryService.move(1L, 2L), ErrorKeys.CATEGORY_CYCLE_DETECTED);
    }

    @Test
    void activateRejectsCategoryUnderDisabledParent() {
        Category parent = category(1L, "FOOD", null, false);
        Category child = category(2L, "DRINK", parent, false);
        when(categoryRepository.findByIdAndCompanyId(2L, 1L)).thenReturn(Optional.of(child));

        assertBusinessError(() -> categoryService.activate(2L), ErrorKeys.CATEGORY_PARENT_DISABLED);
    }

    @Test
    void deactivateAlsoDisablesAllDescendants() {
        Category parent = category(1L, "FOOD", null, true);
        Category child = category(2L, "DRINK", parent, true);
        Category grandchild = category(3L, "TEA", child, true);
        when(categoryRepository.findByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findAllDescendants(1L, 1L)).thenReturn(List.of(child, grandchild));
        when(categoryRepository.save(parent)).thenReturn(parent);

        categoryService.deactivate(1L);

        assertThat(parent.getEnabled()).isFalse();
        assertThat(child.getEnabled()).isFalse();
        assertThat(grandchild.getEnabled()).isFalse();
        verify(categoryRepository).saveAll(List.of(child, grandchild));
    }

    @Test
    void treeBuildsRootAndChildHierarchy() {
        Category root = category(1L, "FOOD", null, true);
        Category child = category(2L, "DRINK", root, true);
        when(categoryRepository.findAllByCompanyIdOrderBySortOrderAscIdAsc(1L))
            .thenReturn(List.of(root, child));

        List<CategoryResponse> tree = categoryService.tree(false);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).level()).isEqualTo(2);
    }

    @Test
    void deleteRejectsNonLeafCategory() {
        Category root = category(1L, "FOOD", null, true);
        when(categoryRepository.findByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(root));
        when(categoryRepository.existsByParent_Id(1L)).thenReturn(true);

        assertBusinessError(() -> categoryService.delete(1L), ErrorKeys.CATEGORY_HAS_CHILDREN);
    }

    private Category category(Long id, String code, Category parent, boolean enabled) {
        Category category = Category.builder()
            .id(id)
            .categoryCode(code)
            .categoryName(code)
            .parent(parent)
            .sortOrder(0)
            .enabled(enabled)
            .build();
        category.setCompanyId(1L);
        return category;
    }

    private void assertBusinessError(Runnable action, String errorKey) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey()).isEqualTo(errorKey));
    }
}
