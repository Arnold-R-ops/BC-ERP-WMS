package com.wms.system.service;

import com.wms.system.dto.category.CategoryResponse;
import com.wms.system.dto.category.CreateCategoryRequest;
import com.wms.system.dto.category.UpdateCategoryRequest;
import com.wms.system.entity.Category;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CategoryRepository;
import com.wms.system.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final long DEFAULT_COMPANY_ID = 1L;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean enabledOnly) {
        return loadCategories(enabledOnly).stream()
            .map(category -> toResponse(category, List.of()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> tree(boolean enabledOnly) {
        List<Category> categories = loadCategories(enabledOnly);
        Map<Long, List<Category>> childrenByParent = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();

        for (Category category : categories) {
            if (category.getParent() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParent().getId(), ignored -> new ArrayList<>())
                    .add(category);
            }
        }

        return roots.stream()
            .map(root -> toTreeResponse(root, childrenByParent))
            .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return toResponse(requireCategory(id), List.of());
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String code = normalizeCode(request.categoryCode());
        String name = normalizeName(request.categoryName());

        if (categoryRepository.existsByCompanyIdAndCategoryCodeIgnoreCase(DEFAULT_COMPANY_ID, code)) {
            throw conflict(ErrorKeys.CATEGORY_ALREADY_EXISTS, "categoryCode", code);
        }

        Category parent = resolveParent(request.parentId());
        ensureSiblingNameUnique(parent, name, null);

        Category category = Category.builder()
            .categoryCode(code)
            .categoryName(name)
            .parent(parent)
            .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
            .enabled(true)
            .description(normalizeOptional(request.description()))
            .build();
        category.setCompanyId(DEFAULT_COMPANY_ID);

        return toResponse(categoryRepository.save(category), List.of());
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = requireCategory(id);

        if (request.categoryName() != null) {
            String name = normalizeName(request.categoryName());
            ensureSiblingNameUnique(category.getParent(), name, id);
            category.setCategoryName(name);
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.description() != null) {
            category.setDescription(normalizeOptional(request.description()));
        }

        return toResponse(categoryRepository.save(category), List.of());
    }

    @Transactional
    public CategoryResponse move(Long id, Long parentId) {
        Category category = requireCategory(id);
        Category newParent = resolveParent(parentId);

        if (newParent != null && isSelfOrDescendant(newParent, category.getId())) {
            throw conflict(ErrorKeys.CATEGORY_CYCLE_DETECTED, "categoryId", id);
        }

        ensureSiblingNameUnique(newParent, category.getCategoryName(), id);
        category.setParent(newParent);
        return toResponse(categoryRepository.save(category), List.of());
    }

    @Transactional
    public CategoryResponse activate(Long id) {
        Category category = requireCategory(id);
        Category disabledAncestor = findDisabledAncestor(category.getParent());
        if (disabledAncestor != null) {
            throw new BusinessException(
                ErrorKeys.CATEGORY_PARENT_DISABLED,
                Map.of("categoryId", id, "parentId", disabledAncestor.getId())
            );
        }
        category.setEnabled(true);
        return toResponse(categoryRepository.save(category), List.of());
    }

    @Transactional
    public CategoryResponse deactivate(Long id) {
        Category category = requireCategory(id);
        category.setEnabled(false);
        List<Category> descendants = categoryRepository.findAllDescendants(DEFAULT_COMPANY_ID, id);
        descendants.forEach(descendant -> descendant.setEnabled(false));
        if (!descendants.isEmpty()) {
            categoryRepository.saveAll(descendants);
        }
        return toResponse(categoryRepository.save(category), List.of());
    }

    @Transactional
    public void delete(Long id) {
        Category category = requireCategory(id);
        if (categoryRepository.existsByParent_Id(id)) {
            throw conflict(ErrorKeys.CATEGORY_HAS_CHILDREN, "categoryId", id);
        }
        if (productRepository.countByCategoryId(id) > 0) {
            throw conflict(ErrorKeys.CATEGORY_IN_USE, "categoryId", id);
        }
        categoryRepository.delete(category);
    }

    private List<Category> loadCategories(boolean enabledOnly) {
        return enabledOnly
            ? categoryRepository.findAllByCompanyIdAndEnabledTrueOrderBySortOrderAscIdAsc(DEFAULT_COMPANY_ID)
            : categoryRepository.findAllByCompanyIdOrderBySortOrderAscIdAsc(DEFAULT_COMPANY_ID);
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        Category parent = requireCategory(parentId);
        if (!Boolean.TRUE.equals(parent.getEnabled())) {
            throw new BusinessException(ErrorKeys.CATEGORY_PARENT_DISABLED, Map.of("parentId", parentId));
        }
        return parent;
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findByIdAndCompanyId(id, DEFAULT_COMPANY_ID)
            .orElseThrow(() -> new BusinessException(ErrorKeys.CATEGORY_NOT_FOUND, Map.of("categoryId", id)));
    }

    private void ensureSiblingNameUnique(Category parent, String name, Long excludeId) {
        Long parentId = parent == null ? null : parent.getId();
        if (categoryRepository.existsSiblingName(DEFAULT_COMPANY_ID, parentId, name, excludeId)) {
            throw new BusinessException(
                ErrorKeys.CATEGORY_ALREADY_EXISTS,
                Map.of("categoryName", name, "parentId", parentId == null ? 0L : parentId)
            );
        }
    }

    private CategoryResponse toTreeResponse(Category category, Map<Long, List<Category>> childrenByParent) {
        List<CategoryResponse> children = childrenByParent.getOrDefault(category.getId(), List.of()).stream()
            .map(child -> toTreeResponse(child, childrenByParent))
            .toList();
        return toResponse(category, children);
    }

    private CategoryResponse toResponse(Category category, List<CategoryResponse> children) {
        return new CategoryResponse(
            category.getId(),
            category.getCompanyId(),
            category.getParent() == null ? null : category.getParent().getId(),
            category.getCategoryCode(),
            category.getCategoryName(),
            calculateLevel(category),
            category.getSortOrder(),
            category.getEnabled(),
            category.getDescription(),
            category.getCreatedAt(),
            category.getUpdatedAt(),
            children
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int calculateLevel(Category category) {
        int level = 1;
        Category current = category.getParent();
        while (current != null) {
            level++;
            current = current.getParent();
        }
        return level;
    }

    private boolean isSelfOrDescendant(Category candidate, Long categoryId) {
        Category current = candidate;
        while (current != null) {
            if (categoryId.equals(current.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private Category findDisabledAncestor(Category parent) {
        Category current = parent;
        while (current != null) {
            if (!Boolean.TRUE.equals(current.getEnabled())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private BusinessException conflict(String errorKey, String key, Object value) {
        return new BusinessException(errorKey, Map.of(key, value));
    }
}
