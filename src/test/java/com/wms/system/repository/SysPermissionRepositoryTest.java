package com.wms.system.repository;

import com.wms.system.entity.SysPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("SysPermissionRepository Tests")
class SysPermissionRepositoryTest {

    @Autowired private SysPermissionRepository sysPermissionRepository;
    @Autowired private TestEntityManager entityManager;

    private SysPermission menuInventory;
    private SysPermission menuOrders;
    private SysPermission btnAdjustStock;
    private SysPermission apiInventory;

    @BeforeEach
    void setUp() {
        // Root menu permissions
        menuInventory = SysPermission.builder()
                .permissionCode("MENU_INVENTORY")
                .permissionName("Inventory Management")
                .permissionType("MENU")
                .status("ACTIVE")
                .sortOrder(1)
                .menuUrl("/inventory")
                .parentId(null)
                .build();

        menuOrders = SysPermission.builder()
                .permissionCode("MENU_ORDERS")
                .permissionName("Order Management")
                .permissionType("MENU")
                .status("ACTIVE")
                .sortOrder(2)
                .menuUrl("/orders")
                .parentId(null)
                .build();

        entityManager.persist(menuInventory);
        entityManager.persist(menuOrders);
        entityManager.flush();

        // Child button under inventory menu
        btnAdjustStock = SysPermission.builder()
                .permissionCode("BTN_ADJUST_STOCK")
                .permissionName("Adjust Stock Button")
                .permissionType("BUTTON")
                .status("ACTIVE")
                .sortOrder(1)
                .parentId(menuInventory.getId())
                .build();

        // API permission (disabled)
        apiInventory = SysPermission.builder()
                .permissionCode("API_INVENTORY_ADJUST")
                .permissionName("Inventory Adjust API")
                .permissionType("API")
                .resourcePath("/api/inventory/adjust")
                .httpMethod("POST")
                .status("DISABLED")
                .sortOrder(1)
                .parentId(null)
                .build();

        entityManager.persist(btnAdjustStock);
        entityManager.persist(apiInventory);
        entityManager.flush();
    }

    // ========== findByPermissionCode ==========

    @Test
    @DisplayName("findByPermissionCode - returns permission when code exists")
    void testFindByPermissionCode_Found() {
        Optional<SysPermission> found = sysPermissionRepository.findByPermissionCode("MENU_INVENTORY");

        assertThat(found).isPresent();
        assertThat(found.get().getPermissionName()).isEqualTo("Inventory Management");
        assertThat(found.get().isMenuPermission()).isTrue();
    }

    @Test
    @DisplayName("findByPermissionCode - returns empty when code does not exist")
    void testFindByPermissionCode_NotFound() {
        Optional<SysPermission> found = sysPermissionRepository.findByPermissionCode("NONEXISTENT");

        assertThat(found).isEmpty();
    }

    // ========== existsByPermissionCode ==========

    @Test
    @DisplayName("existsByPermissionCode - returns true when code exists")
    void testExistsByPermissionCode_Exists() {
        boolean exists = sysPermissionRepository.existsByPermissionCode("MENU_INVENTORY");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByPermissionCode - returns false when code does not exist")
    void testExistsByPermissionCode_NotExists() {
        boolean exists = sysPermissionRepository.existsByPermissionCode("NONEXISTENT");

        assertThat(exists).isFalse();
    }

    // ========== findByPermissionType ==========

    @Test
    @DisplayName("findByPermissionType - returns only MENU permissions")
    void testFindByPermissionType_Menu() {
        List<SysPermission> menus = sysPermissionRepository.findByPermissionType("MENU");

        assertThat(menus).hasSize(2);
        assertThat(menus)
                .extracting(SysPermission::getPermissionCode)
                .containsExactlyInAnyOrder("MENU_INVENTORY", "MENU_ORDERS");
        assertThat(menus).allMatch(SysPermission::isMenuPermission);
    }

    @Test
    @DisplayName("findByPermissionType - returns only BUTTON permissions")
    void testFindByPermissionType_Button() {
        List<SysPermission> buttons = sysPermissionRepository.findByPermissionType("BUTTON");

        assertThat(buttons).hasSize(1);
        assertThat(buttons.get(0).getPermissionCode()).isEqualTo("BTN_ADJUST_STOCK");
        assertThat(buttons.get(0).isButtonPermission()).isTrue();
    }

    @Test
    @DisplayName("findByPermissionType - returns API permissions")
    void testFindByPermissionType_Api() {
        List<SysPermission> apis = sysPermissionRepository.findByPermissionType("API");

        assertThat(apis).hasSize(1);
        assertThat(apis.get(0).getPermissionCode()).isEqualTo("API_INVENTORY_ADJUST");
        assertThat(apis.get(0).isApiPermission()).isTrue();
    }

    // ========== findByParentId ==========

    @Test
    @DisplayName("findByParentId - returns child permissions under given parent")
    void testFindByParentId_Found() {
        List<SysPermission> children = sysPermissionRepository.findByParentId(menuInventory.getId());

        assertThat(children).hasSize(1);
        assertThat(children.get(0).getPermissionCode()).isEqualTo("BTN_ADJUST_STOCK");
    }

    @Test
    @DisplayName("findByParentId - returns empty when no children exist")
    void testFindByParentId_NoChildren() {
        List<SysPermission> children = sysPermissionRepository.findByParentId(menuOrders.getId());

        assertThat(children).isEmpty();
    }

    @Test
    @DisplayName("findByParentId(null) - returns root permissions")
    void testFindByParentId_Null() {
        List<SysPermission> roots = sysPermissionRepository.findByParentId(null);

        // menuInventory, menuOrders, apiInventory all have parentId=null
        assertThat(roots).hasSize(3);
        assertThat(roots)
                .extracting(SysPermission::getPermissionCode)
                .containsExactlyInAnyOrder("MENU_INVENTORY", "MENU_ORDERS", "API_INVENTORY_ADJUST");
    }

    // ========== findByPermissionTypeAndStatus ==========

    @Test
    @DisplayName("findByPermissionTypeAndStatus - returns active menu permissions")
    void testFindByPermissionTypeAndStatus_ActiveMenu() {
        List<SysPermission> results = sysPermissionRepository.findByPermissionTypeAndStatus("MENU", "ACTIVE");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(p -> "MENU".equals(p.getPermissionType()) && p.isActive());
    }

    @Test
    @DisplayName("findByPermissionTypeAndStatus - returns empty for disabled menu")
    void testFindByPermissionTypeAndStatus_DisabledMenu() {
        List<SysPermission> results = sysPermissionRepository.findByPermissionTypeAndStatus("MENU", "DISABLED");

        assertThat(results).isEmpty();
    }

    // ========== findAllActive (default method) ==========

    @Test
    @DisplayName("findAllActive - returns all active permissions")
    void testFindAllActive() {
        List<SysPermission> active = sysPermissionRepository.findAllActive();

        // menuInventory, menuOrders, btnAdjustStock are ACTIVE; apiInventory is DISABLED
        assertThat(active).hasSize(3);
        assertThat(active).allMatch(SysPermission::isActive);
        assertThat(active)
                .extracting(SysPermission::getPermissionCode)
                .doesNotContain("API_INVENTORY_ADJUST");
    }

    // ========== findAllActiveForTree ==========

    @Test
    @DisplayName("findAllActiveForTree - returns active permissions ordered for tree building")
    void testFindAllActiveForTree() {
        List<SysPermission> tree = sysPermissionRepository.findAllActiveForTree();

        assertThat(tree).hasSize(3);
        // All should be ACTIVE
        assertThat(tree).allMatch(SysPermission::isActive);
    }

    // ========== findRootPermissions ==========

    @Test
    @DisplayName("findRootPermissions - returns permissions with no parent")
    void testFindRootPermissions() {
        List<SysPermission> roots = sysPermissionRepository.findRootPermissions();

        // menuInventory, menuOrders, apiInventory all have parentId=null
        assertThat(roots).hasSize(3);
        assertThat(roots).allMatch(SysPermission::isRootPermission);
    }

    // ========== basic CRUD ==========

    @Test
    @DisplayName("save - persists new permission with audit timestamps")
    void testSave_NewPermission() {
        SysPermission newPerm = SysPermission.builder()
                .permissionCode("NEW_PERM")
                .permissionName("New Permission")
                .permissionType("BUTTON")
                .status("ACTIVE")
                .sortOrder(99)
                .build();

        SysPermission saved = sysPermissionRepository.save(newPerm);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(sysPermissionRepository.findByPermissionCode("NEW_PERM")).isPresent();
    }

    @Test
    @DisplayName("count - returns total number of permissions")
    void testCount() {
        long count = sysPermissionRepository.count();

        assertThat(count).isEqualTo(4);
    }
}
