package com.wms.system.service;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DynamicPermissionService 单元测试
 *
 * 重点测试角色继承和权限解析功能
 *
 * 测试场景：
 * 1. 单角色权限查询
 * 2. 董事长角色继承（多重继承）
 * 3. 多级角色继承（递归）
 * 4. 权限去重
 * 5. 空角色处理
 *
 * @author WMS Team
 * @since 2026-01-18
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicPermissionService 单元测试")
@SuppressWarnings("unchecked")
class DynamicPermissionServiceTest {

    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private SysRoleInheritRepository roleInheritRepository;

    @Mock
    private SysRolePermissionRepository rolePermissionRepository;

    @Mock
    private SysPermissionRepository permissionRepository;

    @InjectMocks
    private DynamicPermissionService permissionService;

    private SysRole chairmanRole;
    private SysRole warehouseAdminRole;
    private SysRole buyerRole;
    private SysRole sellerRole;

    private SysPermission inventoryViewPermission;
    private SysPermission purchaseCreatePermission;
    private SysPermission salesViewPermission;
    private SysPermission globalViewPermission;

    @BeforeEach
    void setUp() {
        // 初始化测试角色
        chairmanRole = SysRole.builder()
                .id(1L)
                .roleCode("CHAIRMAN")
                .roleName("董事长")
                .status("ACTIVE")
                .build();

        warehouseAdminRole = SysRole.builder()
                .id(2L)
                .roleCode("WAREHOUSE_ADMIN")
                .roleName("仓库管理员")
                .status("ACTIVE")
                .build();

        buyerRole = SysRole.builder()
                .id(3L)
                .roleCode("BUYER")
                .roleName("采购员")
                .status("ACTIVE")
                .build();

        sellerRole = SysRole.builder()
                .id(4L)
                .roleCode("SELLER")
                .roleName("销售员")
                .status("ACTIVE")
                .build();

        // 初始化测试权限
        inventoryViewPermission = SysPermission.builder()
                .id(101L)
                .permissionCode("inventory:view")
                .permissionName("查看库存")
                .permissionType("API")
                .resourcePath("/api/inventory/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .sortOrder(1)
                .build();

        purchaseCreatePermission = SysPermission.builder()
                .id(102L)
                .permissionCode("purchase:create")
                .permissionName("创建采购订单")
                .permissionType("API")
                .resourcePath("/api/purchase")
                .httpMethod("POST")
                .status("ACTIVE")
                .sortOrder(2)
                .build();

        salesViewPermission = SysPermission.builder()
                .id(103L)
                .permissionCode("sales:view")
                .permissionName("查看销售")
                .permissionType("API")
                .resourcePath("/api/sales/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .sortOrder(3)
                .build();

        globalViewPermission = SysPermission.builder()
                .id(104L)
                .permissionCode("global:view")
                .permissionName("全局数据查看")
                .permissionType("API")
                .resourcePath("/api/reports/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .sortOrder(4)
                .build();
    }

    @Test
    @DisplayName("查询单角色用户权限 - 无继承")
    void getUserPermissions_SingleRole_NoInheritance() {
        // Given: 用户只有仓库管理员角色
        Long userId = 1L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L)); // WAREHOUSE_ADMIN

        // Given: 仓库管理员无父角色
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(2L))
                .thenReturn(Set.of());

        // Given: 仓库管理员有 1 个权限
        SysRolePermission rolePermission = new SysRolePermission();
        rolePermission.setRoleId(2L);
        rolePermission.setPermissionId(101L);
        rolePermission.setPermission(inventoryViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(Set.of(2L)))
                .thenReturn(List.of(rolePermission));

        // Given: 查询角色信息
        when(roleRepository.findByIdIn(Set.of(2L)))
                .thenReturn(List.of(warehouseAdminRole));

        // When: 获取用户权限
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 验证结果
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRoleIds()).containsExactly(2L);
        assertThat(result.getRoleCodes()).containsExactly("WAREHOUSE_ADMIN");
        assertThat(result.getEffectiveRoleIds()).containsExactly(2L);
        assertThat(result.getPermissions()).hasSize(1);
        assertThat(result.getPermissions().get(0).getPermissionCode()).isEqualTo("inventory:view");
        assertThat(result.getApiPermissions()).hasSize(1);
        assertThat(result.hasPermission("inventory:view")).isTrue();
        assertThat(result.hasPermission("purchase:create")).isFalse();
    }

    @Test
    @DisplayName("查询董事长权限 - 多重角色继承")
    void getUserPermissions_ChairmanRole_MultipleInheritance() {
        // Given: 用户是董事长
        Long userId = 2L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(1L)); // CHAIRMAN

        // Given: 董事长继承 3 个角色
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(1L))
                .thenReturn(Set.of(2L, 3L, 4L)); // WAREHOUSE_ADMIN, BUYER, SELLER

        // Given: 父角色无继承
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(2L))
                .thenReturn(Set.of());
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(3L))
                .thenReturn(Set.of());
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(4L))
                .thenReturn(Set.of());

        // Given: 各角色的权限
        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(1L);
        rp1.setPermissionId(104L);
        rp1.setPermission(globalViewPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(2L);
        rp2.setPermissionId(101L);
        rp2.setPermission(inventoryViewPermission);

        SysRolePermission rp3 = new SysRolePermission();
        rp3.setRoleId(3L);
        rp3.setPermissionId(102L);
        rp3.setPermission(purchaseCreatePermission);

        SysRolePermission rp4 = new SysRolePermission();
        rp4.setRoleId(4L);
        rp4.setPermissionId(103L);
        rp4.setPermission(salesViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(Set.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(rp1, rp2, rp3, rp4));

        // Given: 查询角色信息
        when(roleRepository.findByIdIn(Set.of(1L)))
                .thenReturn(List.of(chairmanRole));

        // When: 获取董事长权限
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 验证结果
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRoleIds()).containsExactly(1L);
        assertThat(result.getRoleCodes()).containsExactly("CHAIRMAN");
        assertThat(result.getEffectiveRoleIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);

        // Then: 验证权限汇总（应该有 4 个权限）
        assertThat(result.getPermissions()).hasSize(4);
        assertThat(result.getPermissionCodes()).containsExactlyInAnyOrder(
                "global:view", "inventory:view", "purchase:create", "sales:view"
        );

        // Then: 验证继承的权限
        assertThat(result.hasPermission("inventory:view")).isTrue(); // 继承自仓库管理员
        assertThat(result.hasPermission("purchase:create")).isTrue(); // 继承自采购员
        assertThat(result.hasPermission("sales:view")).isTrue(); // 继承自销售员
        assertThat(result.hasPermission("global:view")).isTrue(); // 董事长专属
    }

    @Test
    @DisplayName("递归角色继承 - 3 层继承")
    void getInheritedRoleIds_ThreeLevelInheritance() {
        // Given: A -> B -> C 三层继承
        // 角色A继承角色B，角色B继承角色C
        Long roleA = 10L;
        Long roleB = 20L;
        Long roleC = 30L;

        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleA))
                .thenReturn(Set.of(roleB));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleB))
                .thenReturn(Set.of(roleC));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleC))
                .thenReturn(Set.of());

        // When: 查询角色A的所有继承角色
        Set<Long> result = permissionService.getInheritedRoleIds(Set.of(roleA));

        // Then: 应该包含 A, B, C
        assertThat(result).containsExactlyInAnyOrder(roleA, roleB, roleC);

        // Then: 验证递归调用
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleA);
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleB);
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleC);
    }

    @Test
    @DisplayName("权限去重 - 多个角色有相同权限")
    void getUserPermissions_DuplicatePermissions() {
        // Given: 用户有 2 个角色，它们有重复权限
        Long userId = 3L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L, 3L)); // WAREHOUSE_ADMIN, BUYER

        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        // Given: 两个角色都有 inventory:view 权限
        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(2L);
        rp1.setPermissionId(101L);
        rp1.setPermission(inventoryViewPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(3L);
        rp2.setPermissionId(101L); // 同一个权限ID
        rp2.setPermission(inventoryViewPermission);

        SysRolePermission rp3 = new SysRolePermission();
        rp3.setRoleId(3L);
        rp3.setPermissionId(102L);
        rp3.setPermission(purchaseCreatePermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(Set.of(2L, 3L)))
                .thenReturn(List.of(rp1, rp2, rp3));

        when(roleRepository.findByIdIn(Set.of(2L, 3L)))
                .thenReturn(List.of(warehouseAdminRole, buyerRole));

        // When: 获取用户权限
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 权限应该去重，只有 2 个权限
        assertThat(result.getPermissions()).hasSize(2);
        assertThat(result.getPermissionCodes()).containsExactlyInAnyOrder(
                "inventory:view", "purchase:create"
        );
    }

    @Test
    @DisplayName("用户无角色 - 返回空权限")
    void getUserPermissions_NoRoles() {
        // Given: 用户没有角色
        Long userId = 4L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of());

        // When: 获取用户权限
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 应该返回空权限
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRoleIds()).isEmpty();
        assertThat(result.getPermissions()).isEmpty();
        assertThat(result.hasPermission("any:permission")).isFalse();

        // Then: 不应该查询权限
        verify(rolePermissionRepository, never()).findByRoleIdInWithPermission(any());
    }

    @Test
    @DisplayName("检查用户是否有特定权限")
    void hasPermission() {
        // Given: 模拟用户权限
        Long userId = 5L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        SysRolePermission rp = new SysRolePermission();
        rp.setRoleId(2L);
        rp.setPermissionId(101L);
        rp.setPermission(inventoryViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(any()))
                .thenReturn(List.of(rp));
        when(roleRepository.findByIdIn(any()))
                .thenReturn(List.of(warehouseAdminRole));

        // When & Then: 检查权限
        assertThat(permissionService.hasPermission(userId, "inventory:view")).isTrue();
        assertThat(permissionService.hasPermission(userId, "purchase:create")).isFalse();
    }

    @Test
    @DisplayName("检查用户是否有任意权限")
    void hasAnyPermission() {
        // Given: 模拟用户权限
        Long userId = 6L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        SysRolePermission rp = new SysRolePermission();
        rp.setRoleId(2L);
        rp.setPermissionId(101L);
        rp.setPermission(inventoryViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(any()))
                .thenReturn(List.of(rp));
        when(roleRepository.findByIdIn(any()))
                .thenReturn(List.of(warehouseAdminRole));

        // When & Then: 检查是否有任意权限
        assertThat(permissionService.hasAnyPermission(userId, "inventory:view", "purchase:create")).isTrue();
        assertThat(permissionService.hasAnyPermission(userId, "sales:view", "purchase:create")).isFalse();
    }

    @Test
    @DisplayName("检查用户是否有所有权限")
    void hasAllPermissions() {
        // Given: 模拟用户有 2 个权限
        Long userId = 7L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(2L);
        rp1.setPermissionId(101L);
        rp1.setPermission(inventoryViewPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(2L);
        rp2.setPermissionId(102L);
        rp2.setPermission(purchaseCreatePermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(any()))
                .thenReturn(List.of(rp1, rp2));
        when(roleRepository.findByIdIn(any()))
                .thenReturn(List.of(warehouseAdminRole));

        // When & Then: 检查是否有所有权限
        assertThat(permissionService.hasAllPermissions(userId, "inventory:view", "purchase:create")).isTrue();
        assertThat(permissionService.hasAllPermissions(userId, "inventory:view", "sales:view")).isFalse();
    }

    @Test
    @DisplayName("权限按类型分类 - MENU, API, BUTTON")
    void getUserPermissions_PermissionTypeClassification() {
        // Given: 用户有不同类型的权限
        Long userId = 8L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(1L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        // 创建不同类型的权限
        SysPermission menuPermission = SysPermission.builder()
                .id(201L)
                .permissionCode("menu:inventory")
                .permissionName("库存菜单")
                .permissionType("MENU")
                .status("ACTIVE")
                .sortOrder(1)
                .build();

        SysPermission buttonPermission = SysPermission.builder()
                .id(202L)
                .permissionCode("button:delete")
                .permissionName("删除按钮")
                .permissionType("BUTTON")
                .status("ACTIVE")
                .sortOrder(2)
                .build();

        SysRolePermission rp1 = new SysRolePermission();
        rp1.setPermission(menuPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setPermission(inventoryViewPermission); // API type

        SysRolePermission rp3 = new SysRolePermission();
        rp3.setPermission(buttonPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(any()))
                .thenReturn(List.of(rp1, rp2, rp3));
        when(roleRepository.findByIdIn(any()))
                .thenReturn(List.of(chairmanRole));

        // When: 获取用户权限
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 验证权限分类
        assertThat(result.getMenuPermissions()).hasSize(1);
        assertThat(result.getApiPermissions()).hasSize(1);
        assertThat(result.getButtonPermissions()).hasSize(1);
        assertThat(result.getPermissions()).hasSize(3);
    }
}
