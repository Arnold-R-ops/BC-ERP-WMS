package com.wms.system.repository;

import com.wms.system.entity.SysRole;
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
@DisplayName("SysRoleRepository Tests")
class SysRoleRepositoryTest {

    @Autowired private SysRoleRepository sysRoleRepository;
    @Autowired private TestEntityManager entityManager;

    private SysRole superAdmin;
    private SysRole chairman;
    private SysRole customRole;
    private SysRole disabledRole;

    @BeforeEach
    void setUp() {
        superAdmin = SysRole.builder()
                .roleCode("TENANT_ADMIN")
                .roleName("Super Administrator")
                .roleType("SYSTEM")
                .status("ACTIVE")
                .sortOrder(1)
                .description("Full system access")
                .build();

        chairman = SysRole.builder()
                .roleCode("CHAIRMAN")
                .roleName("Chairman")
                .roleType("SYSTEM")
                .status("ACTIVE")
                .sortOrder(2)
                .description("Chairman access")
                .build();

        customRole = SysRole.builder()
                .roleCode("WAREHOUSE_STAFF")
                .roleName("Warehouse Staff")
                .roleType("CUSTOM")
                .status("ACTIVE")
                .sortOrder(10)
                .description("Warehouse staff role")
                .build();

        disabledRole = SysRole.builder()
                .roleCode("LEGACY_ROLE")
                .roleName("Legacy Role")
                .roleType("CUSTOM")
                .status("DISABLED")
                .sortOrder(99)
                .description("Deprecated role")
                .build();

        entityManager.persist(superAdmin);
        entityManager.persist(chairman);
        entityManager.persist(customRole);
        entityManager.persist(disabledRole);
        entityManager.flush();
    }

    // ========== findByRoleCode ==========

    @Test
    @DisplayName("findByRoleCode - returns role when code exists")
    void testFindByRoleCode_Found() {
        Optional<SysRole> found = sysRoleRepository.findByRoleCode("TENANT_ADMIN");

        assertThat(found).isPresent();
        assertThat(found.get().getRoleName()).isEqualTo("Super Administrator");
        assertThat(found.get().getRoleType()).isEqualTo("SYSTEM");
        assertThat(found.get().isSystemRole()).isTrue();
    }

    @Test
    @DisplayName("findByRoleCode - returns empty when code does not exist")
    void testFindByRoleCode_NotFound() {
        Optional<SysRole> found = sysRoleRepository.findByRoleCode("NONEXISTENT_ROLE");

        assertThat(found).isEmpty();
    }

    // ========== existsByRoleCode ==========

    @Test
    @DisplayName("existsByRoleCode - returns true when code exists")
    void testExistsByRoleCode_Exists() {
        boolean exists = sysRoleRepository.existsByRoleCode("TENANT_ADMIN");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByRoleCode - returns false when code does not exist")
    void testExistsByRoleCode_NotExists() {
        boolean exists = sysRoleRepository.existsByRoleCode("NONEXISTENT_ROLE");

        assertThat(exists).isFalse();
    }

    // ========== findByRoleType ==========

    @Test
    @DisplayName("findByRoleType - returns only SYSTEM roles")
    void testFindByRoleType_System() {
        List<SysRole> systemRoles = sysRoleRepository.findByRoleType("SYSTEM");

        assertThat(systemRoles).hasSize(2);
        assertThat(systemRoles)
                .extracting(SysRole::getRoleCode)
                .containsExactlyInAnyOrder("TENANT_ADMIN", "CHAIRMAN");
        assertThat(systemRoles).allMatch(SysRole::isSystemRole);
    }

    @Test
    @DisplayName("findByRoleType - returns only CUSTOM roles")
    void testFindByRoleType_Custom() {
        List<SysRole> customRoles = sysRoleRepository.findByRoleType("CUSTOM");

        assertThat(customRoles).hasSize(2);
        assertThat(customRoles)
                .extracting(SysRole::getRoleCode)
                .containsExactlyInAnyOrder("WAREHOUSE_STAFF", "LEGACY_ROLE");
    }

    // ========== findByStatus ==========

    @Test
    @DisplayName("findByStatus - returns only ACTIVE roles")
    void testFindByStatus_Active() {
        List<SysRole> active = sysRoleRepository.findByStatus("ACTIVE");

        assertThat(active).hasSize(3);
        assertThat(active)
                .extracting(SysRole::getRoleCode)
                .containsExactlyInAnyOrder("TENANT_ADMIN", "CHAIRMAN", "WAREHOUSE_STAFF");
    }

    @Test
    @DisplayName("findByStatus - returns only DISABLED roles")
    void testFindByStatus_Disabled() {
        List<SysRole> disabled = sysRoleRepository.findByStatus("DISABLED");

        assertThat(disabled).hasSize(1);
        assertThat(disabled.get(0).getRoleCode()).isEqualTo("LEGACY_ROLE");
    }

    // ========== findAllActive (default method) ==========

    @Test
    @DisplayName("findAllActive - returns all active roles via default method")
    void testFindAllActive() {
        List<SysRole> active = sysRoleRepository.findAllActive();

        assertThat(active).hasSize(3);
        assertThat(active).allMatch(SysRole::isActive);
    }

    // ========== findAllSystemRoles (default method) ==========

    @Test
    @DisplayName("findAllSystemRoles - returns all system roles via default method")
    void testFindAllSystemRoles() {
        List<SysRole> systemRoles = sysRoleRepository.findAllSystemRoles();

        assertThat(systemRoles).hasSize(2);
        assertThat(systemRoles).allMatch(SysRole::isSystemRole);
    }

    // ========== findByRoleNameContaining ==========

    @Test
    @DisplayName("findByRoleNameContaining - finds roles matching keyword")
    void testFindByRoleNameContaining_Found() {
        List<SysRole> results = sysRoleRepository.findByRoleNameContaining("Admin");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRoleCode()).isEqualTo("TENANT_ADMIN");
    }

    @Test
    @DisplayName("findByRoleNameContaining - returns all roles matching Staff")
    void testFindByRoleNameContaining_Staff() {
        List<SysRole> results = sysRoleRepository.findByRoleNameContaining("Staff");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRoleCode()).isEqualTo("WAREHOUSE_STAFF");
    }

    @Test
    @DisplayName("findByRoleNameContaining - returns empty when no match")
    void testFindByRoleNameContaining_NoMatch() {
        List<SysRole> results = sysRoleRepository.findByRoleNameContaining("ZZZNOMATCH");

        assertThat(results).isEmpty();
    }

    // ========== findByRoleTypeAndStatus ==========

    @Test
    @DisplayName("findByRoleTypeAndStatus - returns active system roles")
    void testFindByRoleTypeAndStatus() {
        List<SysRole> results = sysRoleRepository.findByRoleTypeAndStatus("SYSTEM", "ACTIVE");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> "SYSTEM".equals(r.getRoleType()) && "ACTIVE".equals(r.getStatus()));
    }

    // ========== basic CRUD ==========

    @Test
    @DisplayName("save - persists new role with audit timestamps")
    void testSave_NewRole() {
        SysRole newRole = SysRole.builder()
                .roleCode("NEW_ROLE")
                .roleName("New Role")
                .roleType("CUSTOM")
                .status("ACTIVE")
                .sortOrder(50)
                .build();

        SysRole saved = sysRoleRepository.save(newRole);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(sysRoleRepository.findByRoleCode("NEW_ROLE")).isPresent();
    }

    @Test
    @DisplayName("count - returns total number of roles")
    void testCount() {
        long count = sysRoleRepository.count();

        assertThat(count).isEqualTo(4);
    }
}
