package com.wms.system.repository;

import com.wms.system.entity.Location;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
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
@DisplayName("WarehouseRepository Tests")
class WarehouseRepositoryTest {

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private TestEntityManager entityManager;

    private Warehouse mainWarehouse;
    private Warehouse secondaryWarehouse;
    private Warehouse inactiveWarehouse;

    @BeforeEach
    void setUp() {
        mainWarehouse = Warehouse.builder()
                .code("WH01")
                .name("Main Warehouse")
                .address("123 Main St")
                .contact("Alice")
                .isActive(true)
                .build();

        secondaryWarehouse = Warehouse.builder()
                .code("WH02")
                .name("Secondary Warehouse")
                .address("456 Second Ave")
                .contact("Bob")
                .isActive(true)
                .build();

        inactiveWarehouse = Warehouse.builder()
                .code("WH03")
                .name("Old Warehouse")
                .address("789 Old Rd")
                .contact("Carol")
                .isActive(false)
                .build();

        entityManager.persist(mainWarehouse);
        entityManager.persist(secondaryWarehouse);
        entityManager.persist(inactiveWarehouse);
        entityManager.flush();
    }

    // ========== findByCode ==========

    @Test
    @DisplayName("findByCode - returns warehouse when code exists")
    void testFindByCode_Found() {
        Optional<Warehouse> found = warehouseRepository.findByCode("WH01");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Main Warehouse");
        assertThat(found.get().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("findByCode - returns empty when code does not exist")
    void testFindByCode_NotFound() {
        Optional<Warehouse> found = warehouseRepository.findByCode("NONEXISTENT");

        assertThat(found).isEmpty();
    }

    // ========== existsByCode ==========

    @Test
    @DisplayName("existsByCode - returns true when code exists")
    void testExistsByCode_Exists() {
        boolean exists = warehouseRepository.existsByCode("WH01");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByCode - returns false when code does not exist")
    void testExistsByCode_NotExists() {
        boolean exists = warehouseRepository.existsByCode("NONEXISTENT");

        assertThat(exists).isFalse();
    }

    // ========== findByIsActive ==========

    @Test
    @DisplayName("findByIsActive(true) - returns only active warehouses")
    void testFindByIsActive_Active() {
        List<Warehouse> active = warehouseRepository.findByIsActive(true);

        assertThat(active).hasSize(2);
        assertThat(active)
                .extracting(Warehouse::getCode)
                .containsExactlyInAnyOrder("WH01", "WH02");
        assertThat(active).allMatch(Warehouse::getIsActive);
    }

    @Test
    @DisplayName("findByIsActive(false) - returns only inactive warehouses")
    void testFindByIsActive_Inactive() {
        List<Warehouse> inactive = warehouseRepository.findByIsActive(false);

        assertThat(inactive).hasSize(1);
        assertThat(inactive.get(0).getCode()).isEqualTo("WH03");
        assertThat(inactive.get(0).getIsActive()).isFalse();
    }

    // ========== findAllActive ==========

    @Test
    @DisplayName("findAllActive - returns active warehouses ordered by code")
    void testFindAllActive() {
        List<Warehouse> active = warehouseRepository.findAllActive();

        assertThat(active).hasSize(2);
        // Results ordered by code: WH01, WH02
        assertThat(active.get(0).getCode()).isEqualTo("WH01");
        assertThat(active.get(1).getCode()).isEqualTo("WH02");
    }

    // ========== findByNameContaining ==========

    @Test
    @DisplayName("findByNameContaining - finds warehouses matching keyword")
    void testFindByNameContaining_Found() {
        List<Warehouse> results = warehouseRepository.findByNameContaining("Warehouse");

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("findByNameContaining - finds specific match")
    void testFindByNameContaining_Specific() {
        List<Warehouse> results = warehouseRepository.findByNameContaining("Main");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCode()).isEqualTo("WH01");
    }

    @Test
    @DisplayName("findByNameContaining - returns empty when no match")
    void testFindByNameContaining_NoMatch() {
        List<Warehouse> results = warehouseRepository.findByNameContaining("ZZZMATCH");

        assertThat(results).isEmpty();
    }

    // ========== countLocationsByWarehouseId ==========

    @Test
    @DisplayName("countLocationsByWarehouseId - returns 0 when no locations")
    void testCountLocationsByWarehouseId_Zero() {
        long count = warehouseRepository.countLocationsByWarehouseId(mainWarehouse.getId());

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("countLocationsByWarehouseId - returns correct count after adding locations")
    void testCountLocationsByWarehouseId_WithLocations() {
        Location loc1 = Location.builder()
                .warehouse(mainWarehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("A01")
                .positionNumber("001")
                .enabled(true)
                .build();
        Location loc2 = Location.builder()
                .warehouse(mainWarehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("A01")
                .positionNumber("002")
                .enabled(true)
                .build();
        entityManager.persist(loc1);
        entityManager.persist(loc2);
        entityManager.flush();

        long count = warehouseRepository.countLocationsByWarehouseId(mainWarehouse.getId());

        assertThat(count).isEqualTo(2);
    }

    // ========== basic CRUD ==========

    @Test
    @DisplayName("save - persists new warehouse with audit timestamps")
    void testSave_NewWarehouse() {
        Warehouse newWarehouse = Warehouse.builder()
                .code("WH04")
                .name("New Warehouse")
                .isActive(true)
                .build();

        Warehouse saved = warehouseRepository.save(newWarehouse);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(warehouseRepository.findByCode("WH04")).isPresent();
    }

    @Test
    @DisplayName("count - returns total number of warehouses")
    void testCount() {
        long count = warehouseRepository.count();

        assertThat(count).isEqualTo(3);
    }
}
