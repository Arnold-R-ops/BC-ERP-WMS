package com.wms.system.service;

import com.wms.system.config.CacheConfig;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

/**
 * Permission Cache Service
 *
 * Manages cache invalidation for the RBAC permission system.
 * Ensures cache consistency when roles or permissions change.
 *
 * Cache Eviction Strategies:
 * 1. User-specific eviction: When a user's roles change
 * 2. Role-wide eviction: When a role's permissions change
 * 3. Global eviction: When permission definitions change
 *
 * Cache Eviction Triggers:
 * - User role assignment/removal → Evict user cache
 * - Role permission assignment/removal → Evict all user caches (affected role)
 * - Role inheritance change → Evict all user caches (affected roles)
 * - Permission status change → Evict all user caches
 *
 * Performance Notes:
 * - Global eviction is expensive (clears all user caches)
 * - Use selective eviction when possible
 * - Consider async eviction for non-critical updates
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final CacheManager cacheManager;
    private final SysUserRoleRepository userRoleRepository;

    /**
     * Evict permissions cache for a specific user
     *
     * Use cases:
     * - User role assignment changed
     * - User role removed
     *
     * @param userId User ID
     */
    public void evictUserPermissions(Long userId) {
        evictUserPermissions(CompanyScope.currentCompanyId(), userId);
    }

    public void evictUserPermissions(Long companyId, Long userId) {
        var cache = cacheManager.getCache(CacheConfig.USER_PERMISSIONS_CACHE);
        if (cache != null) {
            evictKeysWithPrefix(cache, "all:" + companyId + ":" + userId);
            evictKeysWithPrefix(cache, "active:" + companyId + ":" + userId + ":");
        }
        log.info("Evicting permission cache for company {}, user {}", companyId, userId);
    }

    /**
     * Evict permissions cache for all users with a specific role
     *
     * Use cases:
     * - Role permissions changed
     * - Role status changed (ACTIVE/DISABLED)
     * - Role inheritance changed
     *
     * Warning: This can be expensive if many users have the role.
     * Consider async execution for non-critical updates.
     *
     * @param roleId Role ID
     */
    public void evictPermissionsForRole(Long roleId) {
        Long companyId = CompanyScope.currentCompanyId();
        log.info("Evicting permission cache for all users with role ID: {}", roleId);

        // Get all user IDs with this role
        Set<Long> userIds = userRoleRepository
            .findUserIdsByCompanyIdAndRoleId(companyId, roleId);

        // Evict cache for each user
        userIds.forEach(userId -> evictUserPermissions(companyId, userId));

        log.info("Evicted permission cache for {} users with role ID: {}", userIds.size(), roleId);
    }

    /**
     * Evict permissions cache for all users with any of the specified roles
     *
     * Use cases:
     * - Multiple roles changed simultaneously
     * - Batch role updates
     *
     * @param roleIds Set of role IDs
     */
    public void evictPermissionsForRoles(Set<Long> roleIds) {
        log.info("Evicting permission cache for users with {} roles", roleIds.size());

        roleIds.forEach(this::evictPermissionsForRole);
    }

    /**
     * Evict ALL user permissions cache (global eviction)
     *
     * Use cases:
     * - System permission definitions changed
     * - Permission table modified
     * - Global permission reconfiguration
     *
     * Warning: This clears ALL user permission caches.
     * Only use when necessary (e.g., admin changed permission structure).
     *
     */
    public void evictAllUserPermissions() {
        clearCache(CacheConfig.USER_PERMISSIONS_CACHE);
        log.warn("Evicting ALL user permission caches (global eviction)");
    }

    public void evictCompanyUserPermissions(Long companyId) {
        var cache = cacheManager.getCache(CacheConfig.USER_PERMISSIONS_CACHE);
        if (cache != null) {
            evictKeysWithPrefix(cache, "all:" + companyId + ":");
            evictKeysWithPrefix(cache, "active:" + companyId + ":");
        }
        log.info("Evicting permission caches for company {}", companyId);
    }

    /**
     * Evict role inheritance cache
     *
     * Use cases:
     * - Role inheritance relationship added/removed
     * - Role hierarchy changed
     *
     * This cache stores the resolved inheritance tree.
     * Evict it when inheritance structure changes.
     */
    public void evictRoleInheritCache() {
        Long companyId = CompanyScope.currentCompanyId();
        var cache = cacheManager.getCache(CacheConfig.ROLE_INHERIT_CACHE);
        if (cache != null) {
            evictKeysWithPrefix(cache, companyId + ":");
        }
        log.info("Evicting role inheritance cache");
    }

    /**
     * Evict menu tree cache
     *
     * Use cases:
     * - Menu permissions added/removed/modified
     * - Menu structure changed
     */
    public void evictMenuTreeCache() {
        clearCache(CacheConfig.MENU_TREE_CACHE);
        log.info("Evicting menu tree cache");
    }

    /**
     * Evict all RBAC caches (complete reset)
     *
     * Use cases:
     * - System initialization
     * - Admin requested cache clear
     * - Emergency cache reset
     *
     * Warning: This clears ALL RBAC caches.
     * Performance will be degraded until caches warm up again.
     */
    public void evictAllCaches() {
        log.warn("Evicting ALL RBAC caches (complete reset)");

        evictAllUserPermissions();
        evictRoleInheritCache();
        evictMenuTreeCache();

        // Also evict role permissions cache if exists
        var rolePermCache = cacheManager.getCache(CacheConfig.ROLE_PERMISSIONS_CACHE);
        if (rolePermCache != null) {
            rolePermCache.clear();
        }

        log.warn("All RBAC caches cleared");
    }

    /**
     * Get cache statistics (for monitoring)
     *
     * @return Cache statistics summary
     */
    public String getCacheStats() {
        StringBuilder stats = new StringBuilder("RBAC Cache Statistics:\n");

        stats.append("- User Permissions Cache: ")
             .append(getCacheSize(CacheConfig.USER_PERMISSIONS_CACHE))
             .append(" entries\n");

        stats.append("- Role Inheritance Cache: ")
             .append(getCacheSize(CacheConfig.ROLE_INHERIT_CACHE))
             .append(" entries\n");

        stats.append("- Menu Tree Cache: ")
             .append(getCacheSize(CacheConfig.MENU_TREE_CACHE))
             .append(" entries\n");

        stats.append("- Role Permissions Cache: ")
             .append(getCacheSize(CacheConfig.ROLE_PERMISSIONS_CACHE))
             .append(" entries\n");

        return stats.toString();
    }

    /**
     * Get cache size for a specific cache
     *
     * @param cacheName Cache name
     * @return Number of entries (or "N/A" if not supported)
     */
    private String getCacheSize(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return "N/A (cache not found)";
        }

        // Try to get native cache statistics
        try {
            Object nativeCache = cache.getNativeCache();
            if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
                com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache =
                    (com.github.benmanes.caffeine.cache.Cache<?, ?>) nativeCache;
                return String.valueOf(caffeineCache.estimatedSize());
            }
        } catch (Exception e) {
            log.warn("Failed to get cache size for {}: {}", cacheName, e.getMessage());
        }

        return "N/A (statistics not available)";
    }

    private void evictKeysWithPrefix(
            org.springframework.cache.Cache cache, String prefix) {
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeine) {
            caffeine.asMap().keySet().stream()
                .filter(key -> String.valueOf(key).startsWith(prefix))
                .toList()
                .forEach(key -> caffeine.asMap().remove(key));
        } else {
            cache.clear();
        }
    }

    // ========== Convenience Methods ==========

    /**
     * Handle user role assignment (evict user cache)
     *
     * @param userId User ID
     */
    public void onUserRoleAssigned(Long userId) {
        evictUserPermissions(userId);
        log.debug("User {} role assigned, cache evicted", userId);
    }

    /**
     * Handle user role removal (evict user cache)
     *
     * @param userId User ID
     */
    public void onUserRoleRemoved(Long userId) {
        evictUserPermissions(userId);
        log.debug("User {} role removed, cache evicted", userId);
    }

    /**
     * Handle user information update (evict user cache)
     *
     * Called when user basic information is updated (display name, enabled status, etc.)
     * Although permissions may not have changed, we evict cache for consistency.
     *
     * @param userId User ID
     */
    public void onUserUpdated(Long userId) {
        evictUserPermissions(userId);
        log.debug("User {} updated, cache evicted", userId);
    }

    /**
     * Handle user deletion (evict user cache)
     *
     * Called when a user account is deleted.
     * Ensures no stale cache entries remain for deleted users.
     *
     * @param userId User ID
     */
    public void onUserDeleted(Long userId) {
        evictUserPermissions(userId);
        log.debug("User {} deleted, cache evicted", userId);
    }

    /**
     * Handle role permission change (evict all users with this role)
     *
     * @param roleId Role ID
     */
    public void onRolePermissionChanged(Long roleId) {
        evictPermissionsForRole(roleId);
        evictMenuTreeCache(); // Menu might have changed
        log.debug("Role {} permissions changed, affected user caches evicted", roleId);
    }

    /**
     * Handle role inheritance change (evict affected roles)
     *
     * @param childRoleId Child role ID
     * @param parentRoleId Parent role ID
     */
    public void onRoleInheritanceChanged(Long childRoleId, Long parentRoleId) {
        evictRoleInheritCache();
        evictPermissionsForRoles(Set.of(childRoleId, parentRoleId));
        log.debug("Role inheritance changed (child: {}, parent: {}), caches evicted",
                childRoleId, parentRoleId);
    }

    /**
     * Handle permission change (global eviction)
     *
     * @param permissionId Permission ID
     */
    public void onPermissionChanged(Long permissionId) {
        evictCompanyUserPermissions(CompanyScope.currentCompanyId());
        evictMenuTreeCache();
        log.warn("Permission {} changed, current company caches evicted", permissionId);
    }

    private void clearCache(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
