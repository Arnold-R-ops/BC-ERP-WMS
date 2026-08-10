package com.wms.system.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.security.AuthUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class WarehouseScopeService {

    private final UserWarehouseService userWarehouseService;

    public boolean isWarehouseStaff(Authentication authentication) {
        return UserWarehouseService.WAREHOUSE_STAFF.equals(
            AuthUserResolver.resolveCurrentRole(authentication)
        );
    }

    @Transactional(readOnly = true)
    public void requireAccess(
        Authentication authentication,
        Collection<Long> resourceWarehouseIds,
        String resourceType,
        Object resourceId
    ) {
        if (!isWarehouseStaff(authentication)) {
            return;
        }
        Long userId = AuthUserResolver.resolveUserId(authentication);
        Set<Long> assignedWarehouseIds = userWarehouseService.getAssignedWarehouseIds(userId);
        Set<Long> requiredWarehouseIds = resourceWarehouseIds == null
            ? Set.of()
            : resourceWarehouseIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toSet());
        if (requiredWarehouseIds.isEmpty() || !assignedWarehouseIds.containsAll(requiredWarehouseIds)) {
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_SCOPE_DENIED,
                Map.of(
                    "resourceType", resourceType,
                    "resourceId", resourceId,
                    "requiredWarehouseIds", requiredWarehouseIds,
                    "assignedWarehouseIds", assignedWarehouseIds
                )
            );
        }
    }

    @Transactional(readOnly = true)
    public <T> List<T> filterAccessible(
        Authentication authentication,
        List<T> resources,
        Function<T, Collection<Long>> warehouseIdsExtractor
    ) {
        if (!isWarehouseStaff(authentication)) {
            return resources;
        }
        Long userId = AuthUserResolver.resolveUserId(authentication);
        Set<Long> assignedWarehouseIds = userWarehouseService.getAssignedWarehouseIds(userId);
        return resources.stream()
            .filter(resource -> {
                Collection<Long> extracted = warehouseIdsExtractor.apply(resource);
                Set<Long> required = extracted == null
                    ? Set.of()
                    : extracted.stream()
                        .filter(id -> id != null && id > 0)
                        .collect(java.util.stream.Collectors.toSet());
                return !required.isEmpty() && assignedWarehouseIds.containsAll(required);
            })
            .toList();
    }
}
