package com.wms.system.controller;

import com.wms.system.dto.category.CategoryResponse;
import com.wms.system.dto.category.CreateCategoryRequest;
import com.wms.system.dto.category.MoveCategoryRequest;
import com.wms.system.dto.category.UpdateCategoryRequest;
import com.wms.system.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('category:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<CategoryResponse>> list(
        @RequestParam(value = "enabledOnly", defaultValue = "false") boolean enabledOnly
    ) {
        return ResponseEntity.ok(categoryService.list(enabledOnly));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('category:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<CategoryResponse>> tree(
        @RequestParam(value = "enabledOnly", defaultValue = "false") boolean enabledOnly
    ) {
        return ResponseEntity.ok(categoryService.tree(enabledOnly));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('category:view', 'TENANT_ADMIN')")
    public ResponseEntity<CategoryResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(categoryService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('category:create', 'TENANT_ADMIN')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('category:update', 'TENANT_ADMIN')")
    public ResponseEntity<CategoryResponse> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @PutMapping("/{id}/move")
    @PreAuthorize("hasAnyAuthority('category:update', 'TENANT_ADMIN')")
    public ResponseEntity<CategoryResponse> move(
        @PathVariable("id") Long id,
        @RequestBody MoveCategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.move(id, request.parentId()));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyAuthority('category:update', 'TENANT_ADMIN')")
    public ResponseEntity<CategoryResponse> activate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(categoryService.activate(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('category:update', 'TENANT_ADMIN')")
    public ResponseEntity<CategoryResponse> deactivate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(categoryService.deactivate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('category:delete', 'TENANT_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
