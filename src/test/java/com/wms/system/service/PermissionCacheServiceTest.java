package com.wms.system.service;

import com.wms.system.config.CacheConfig;
import com.wms.system.repository.SysUserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionCacheService Tests")
class PermissionCacheServiceTest {

    @Mock private CacheManager cacheManager;
    @Mock private SysUserRoleRepository userRoleRepository;

    @InjectMocks
    private PermissionCacheService permissionCacheService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(userRoleRepository.findUserIdsByCompanyIdAndRoleId(eq(1L), anyLong()))
            .thenAnswer(invocation -> userRoleRepository.findUserIdsByRoleId(invocation.getArgument(1)));
    }

    // ========== evictPermissionsForRole ==========

    @Test
    @DisplayName("evictPermissionsForRole - evicts cache for each user with the role")
    void testEvictPermissionsForRole() {
        Set<Long> userIds = Set.of(1L, 2L, 3L);
        when(userRoleRepository.findUserIdsByRoleId(10L)).thenReturn(userIds);

        permissionCacheService.evictPermissionsForRole(10L);

        // Verify user IDs were looked up
        verify(userRoleRepository).findUserIdsByRoleId(10L);
    }

    @Test
    @DisplayName("evictPermissionsForRole - no-op when role has no users")
    void testEvictPermissionsForRole_NoUsers() {
        when(userRoleRepository.findUserIdsByRoleId(10L)).thenReturn(Set.of());

        permissionCacheService.evictPermissionsForRole(10L);

        verify(userRoleRepository).findUserIdsByRoleId(10L);
    }

    // ========== evictPermissionsForRoles (batch) ==========

    @Test
    @DisplayName("evictPermissionsForRoles - evicts for all specified roles")
    void testEvictPermissionsForRoles() {
        Set<Long> roleIds = Set.of(1L, 2L);
        when(userRoleRepository.findUserIdsByRoleId(anyLong())).thenReturn(Set.of());

        permissionCacheService.evictPermissionsForRoles(roleIds);

        verify(userRoleRepository, times(2)).findUserIdsByRoleId(anyLong());
    }

    // ========== evictAllCaches ==========

    @Test
    @DisplayName("evictAllCaches - clears role permissions cache via cacheManager")
    void testEvictAllCaches() {
        Cache mockRolePermCache = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenAnswer(invocation ->
            CacheConfig.ROLE_PERMISSIONS_CACHE.equals(invocation.getArgument(0))
                ? mockRolePermCache
                : null);

        permissionCacheService.evictAllCaches();

        verify(mockRolePermCache).clear();
    }

    @Test
    @DisplayName("evictAllCaches - handles null cache gracefully")
    void testEvictAllCaches_NullCache() {
        when(cacheManager.getCache(anyString())).thenReturn(null);

        // Should not throw
        permissionCacheService.evictAllCaches();

        verify(cacheManager, atLeastOnce()).getCache(CacheConfig.ROLE_PERMISSIONS_CACHE);
    }

    @Test
    @DisplayName("evictUserPermissions - removes only the selected company's keys")
    void evictUserPermissions_DoesNotEvictAnotherCompany() {
        CaffeineCacheManager realManager = new CaffeineCacheManager(
            CacheConfig.USER_PERMISSIONS_CACHE);
        PermissionCacheService service = new PermissionCacheService(
            realManager, userRoleRepository);
        Cache cache = realManager.getCache(CacheConfig.USER_PERMISSIONS_CACHE);

        cache.put("all:10:7", "company-a-all");
        cache.put("active:10:7:ADMIN:1", "company-a-active");
        cache.put("all:20:7", "company-b-all");
        cache.put("active:20:7:ADMIN:1", "company-b-active");

        service.evictUserPermissions(10L, 7L);

        assertThat(cache.get("all:10:7")).isNull();
        assertThat(cache.get("active:10:7:ADMIN:1")).isNull();
        assertThat(cache.get("all:20:7", String.class)).isEqualTo("company-b-all");
        assertThat(cache.get("active:20:7:ADMIN:1", String.class))
            .isEqualTo("company-b-active");
    }

    // ========== getCacheStats ==========

    @Test
    @DisplayName("getCacheStats - returns non-null stats string")
    void testGetCacheStats() {
        when(cacheManager.getCache(anyString())).thenReturn(null);

        String stats = permissionCacheService.getCacheStats();

        assertThat(stats).isNotNull();
        assertThat(stats).contains("RBAC Cache Statistics");
        assertThat(stats).contains("User Permissions Cache");
        assertThat(stats).contains("Role Inheritance Cache");
        assertThat(stats).contains("Menu Tree Cache");
    }
}
