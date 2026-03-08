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
@DisplayName("LocationRepository Tests")
class LocationRepositoryTest {

    @Autowired private LocationRepository locationRepository;
    @Autowired private TestEntityManager entityManager;

    private Warehouse warehouse;
    private Location locA01_001;
    private Location locA01_002;
    private Location locB01_001;
    private Location disabledLoc;

    @BeforeEach
    void setUp() {
        warehouse = Warehouse.builder()
                .code("WH01")
                .name("Main Warehouse")
                .isActive(true)
                .build();
        entityManager.persist(warehouse);

        // Zone A locations
        locA01_001 = Location.builder()
                .warehouse(warehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("A01")
                .positionNumber("001")
                .enabled(true)
                .build();

        locA01_002 = Location.builder()
                .warehouse(warehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("A01")
                .positionNumber("002")
                .enabled(true)
                .build();

        // Zone B location
        locB01_001 = Location.builder()
                .warehouse(warehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_B)
                .shelfNumber("B01")
                .positionNumber("001")
                .enabled(true)
                .build();

        // Disabled location in Zone A
        disabledLoc = Location.builder()
                .warehouse(warehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("A02")
                .positionNumber("001")
                .enabled(false)
                .build();

        entityManager.persist(locA01_001);
        entityManager.persist(locA01_002);
        entityManager.persist(locB01_001);
        entityManager.persist(disabledLoc);
        entityManager.flush();
        // Reload to get computed locationCode from @PrePersist
        entityManager.clear();
        locA01_001 = entityManager.find(Location.class, locA01_001.getId());
        locB01_001 = entityManager.find(Location.class, locB01_001.getId());
        warehouse = entityManager.find(Warehouse.class, warehouse.getId());
    }

    // ========== findByLocationCode ==========

    @Test
    @DisplayName("findByLocationCode - returns location when code exists")
    void testFindByLocationCode_Found() {
        // locationCode is generated as warehouseCode-zone.name()-shelfNumber-positionNumber
        String expectedCode = "WH01-ZONE_A-A01-001";
        Optional<Location> found = locationRepository.findByLocationCode(expectedCode);

        assertThat(found).isPresent();
        assertThat(found.get().getShelfNumber()).isEqualTo("A01");
        assertThat(found.get().getZone()).isEqualTo(Zone.ZONE_A);
    }

    @Test
    @DisplayName("findByLocationCode - returns empty when code does not exist")
    void testFindByLocationCode_NotFound() {
        Optional<Location> found = locationRepository.findByLocationCode("NONEXISTENT-CODE");

        assertThat(found).isEmpty();
    }

    // ========== findByWarehouseCode ==========

    @Test
    @DisplayName("findByWarehouseCode - returns all locations for warehouse")
    void testFindByWarehouseCode() {
        List<Location> locations = locationRepository.findByWarehouseCode("WH01");

        assertThat(locations).hasSize(4);
    }

    @Test
    @DisplayName("findByWarehouseCode - returns empty for unknown warehouse code")
    void testFindByWarehouseCode_Unknown() {
        List<Location> locations = locationRepository.findByWarehouseCode("UNKNOWN");

        assertThat(locations).isEmpty();
    }

    // ========== findByZone ==========

    @Test
    @DisplayName("findByZone - returns all locations in ZONE_A")
    void testFindByZone_ZoneA() {
        List<Location> zoneA = locationRepository.findByZone(Zone.ZONE_A);

        assertThat(zoneA).hasSize(3); // locA01_001, locA01_002, disabledLoc
        assertThat(zoneA).allMatch(l -> l.getZone() == Zone.ZONE_A);
    }

    @Test
    @DisplayName("findByZone - returns locations in ZONE_B")
    void testFindByZone_ZoneB() {
        List<Location> zoneB = locationRepository.findByZone(Zone.ZONE_B);

        assertThat(zoneB).hasSize(1);
        assertThat(zoneB.get(0).getShelfNumber()).isEqualTo("B01");
    }

    // ========== findByWarehouseCodeAndZone ==========

    @Test
    @DisplayName("findByWarehouseCodeAndZone - returns locations matching warehouse and zone")
    void testFindByWarehouseCodeAndZone() {
        List<Location> results = locationRepository.findByWarehouseCodeAndZone("WH01", Zone.ZONE_A);

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(l ->
                "WH01".equals(l.getWarehouseCode()) && l.getZone() == Zone.ZONE_A);
    }

    // ========== findByEnabledTrue ==========

    @Test
    @DisplayName("findByEnabledTrue - returns only enabled locations")
    void testFindByEnabledTrue() {
        List<Location> enabled = locationRepository.findByEnabledTrue();

        assertThat(enabled).hasSize(3); // locA01_001, locA01_002, locB01_001
        assertThat(enabled).allMatch(Location::getEnabled);
    }

    // ========== countByZone ==========

    @Test
    @DisplayName("countByZone - returns count for ZONE_A")
    void testCountByZone() {
        long count = locationRepository.countByZone(Zone.ZONE_A);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countByZone - returns 0 for zone with no locations")
    void testCountByZone_Empty() {
        long count = locationRepository.countByZone(Zone.ZONE_D);

        assertThat(count).isEqualTo(0);
    }

    // ========== countByWarehouseId ==========

    @Test
    @DisplayName("countByWarehouseId - returns total locations for warehouse")
    void testCountByWarehouseId() {
        long count = locationRepository.countByWarehouseId(warehouse.getId());

        assertThat(count).isEqualTo(4);
    }

    // ========== findByWarehouseId ==========

    @Test
    @DisplayName("findByWarehouseId - returns all locations for warehouse id")
    void testFindByWarehouseId() {
        List<Location> locations = locationRepository.findByWarehouseId(warehouse.getId());

        assertThat(locations).hasSize(4);
    }

    // ========== findByWarehouseAndZone ==========

    @Test
    @DisplayName("findByWarehouseAndZone - returns locations in specified zone of warehouse")
    void testFindByWarehouseAndZone() {
        List<Location> results = locationRepository.findByWarehouseAndZone(warehouse, Zone.ZONE_B);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getZone()).isEqualTo(Zone.ZONE_B);
    }
}
