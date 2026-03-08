package com.wms.system.repository;

import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("PurchaseOrderRepository Tests")
class PurchaseOrderRepositoryTest {

    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private TestEntityManager entityManager;

    private PurchaseOrder orderOrdering1;
    private PurchaseOrder orderOrdering2;
    private PurchaseOrder orderInTransit;

    @BeforeEach
    void setUp() {
        orderOrdering1 = PurchaseOrder.builder()
                .poNumber("PO-20260101-001")
                .supplier("Supplier Alpha")
                .status(PurchaseOrderStatus.ORDERING)
                .totalQuantity(100)
                .totalCost(new BigDecimal("1000.00"))
                .expectedDate(LocalDate.of(2026, 6, 1))
                .operatorId(1L)
                .operatorName("admin")
                .isDeleted(false)
                .items(new ArrayList<>())
                .build();

        orderOrdering2 = PurchaseOrder.builder()
                .poNumber("PO-20260101-002")
                .supplier("Supplier Beta")
                .status(PurchaseOrderStatus.ORDERING)
                .totalQuantity(50)
                .totalCost(new BigDecimal("500.00"))
                .expectedDate(LocalDate.of(2026, 6, 15))
                .operatorId(1L)
                .operatorName("admin")
                .isDeleted(false)
                .items(new ArrayList<>())
                .build();

        orderInTransit = PurchaseOrder.builder()
                .poNumber("PO-20260102-001")
                .supplier("Supplier Alpha")
                .status(PurchaseOrderStatus.IN_TRANSIT)
                .totalQuantity(200)
                .totalCost(new BigDecimal("2000.00"))
                .expectedDate(LocalDate.of(2026, 7, 1))
                .operatorId(2L)
                .operatorName("manager")
                .isDeleted(false)
                .items(new ArrayList<>())
                .build();

        entityManager.persist(orderOrdering1);
        entityManager.persist(orderOrdering2);
        entityManager.persist(orderInTransit);
        entityManager.flush();
    }

    // ========== findByPoNumber ==========

    @Test
    @DisplayName("findByPoNumber - returns order when PO number exists")
    void testFindByPoNumber_Found() {
        Optional<PurchaseOrder> found = purchaseOrderRepository.findByPoNumber("PO-20260101-001");

        assertThat(found).isPresent();
        assertThat(found.get().getSupplier()).isEqualTo("Supplier Alpha");
        assertThat(found.get().getStatus()).isEqualTo(PurchaseOrderStatus.ORDERING);
    }

    @Test
    @DisplayName("findByPoNumber - returns empty when PO number does not exist")
    void testFindByPoNumber_NotFound() {
        Optional<PurchaseOrder> found = purchaseOrderRepository.findByPoNumber("PO-NONEXISTENT");

        assertThat(found).isEmpty();
    }

    // ========== existsByPoNumber ==========

    @Test
    @DisplayName("existsByPoNumber - returns true when PO number exists")
    void testExistsByPoNumber_Exists() {
        boolean exists = purchaseOrderRepository.existsByPoNumber("PO-20260101-001");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByPoNumber - returns false when PO number does not exist")
    void testExistsByPoNumber_NotExists() {
        boolean exists = purchaseOrderRepository.existsByPoNumber("PO-NONEXISTENT");

        assertThat(exists).isFalse();
    }

    // ========== findByStatus(status) ==========

    @Test
    @DisplayName("findByStatus - returns all orders with ORDERING status")
    void testFindByStatus_Ordering() {
        List<PurchaseOrder> orders = purchaseOrderRepository.findByStatus(PurchaseOrderStatus.ORDERING);

        assertThat(orders).hasSize(2);
        assertThat(orders)
                .extracting(PurchaseOrder::getPoNumber)
                .containsExactlyInAnyOrder("PO-20260101-001", "PO-20260101-002");
    }

    @Test
    @DisplayName("findByStatus - returns orders with IN_TRANSIT status")
    void testFindByStatus_InTransit() {
        List<PurchaseOrder> orders = purchaseOrderRepository.findByStatus(PurchaseOrderStatus.IN_TRANSIT);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getPoNumber()).isEqualTo("PO-20260102-001");
    }

    @Test
    @DisplayName("findByStatus - returns empty when no orders in status")
    void testFindByStatus_Empty() {
        List<PurchaseOrder> orders = purchaseOrderRepository.findByStatus(PurchaseOrderStatus.COMPLETED);

        assertThat(orders).isEmpty();
    }

    // ========== findByStatus(status, Pageable) ==========

    @Test
    @DisplayName("findByStatus with pageable - returns paginated results")
    void testFindByStatus_Pageable() {
        Page<PurchaseOrder> page = purchaseOrderRepository.findByStatus(
                PurchaseOrderStatus.ORDERING, PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("findByStatus with pageable - second page returns remaining")
    void testFindByStatus_Pageable_SecondPage() {
        Page<PurchaseOrder> page = purchaseOrderRepository.findByStatus(
                PurchaseOrderStatus.ORDERING, PageRequest.of(1, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }

    // ========== findLatestByDatePrefix ==========

    @Test
    @DisplayName("findLatestByDatePrefix - returns orders with matching PO number prefix, ordered desc")
    void testFindLatestByDatePrefix() {
        List<PurchaseOrder> latest = purchaseOrderRepository.findLatestByDatePrefix("PO-20260101");

        assertThat(latest).hasSize(2);
        // Ordered descending by poNumber: PO-20260101-002 first
        assertThat(latest.get(0).getPoNumber()).isEqualTo("PO-20260101-002");
        assertThat(latest.get(1).getPoNumber()).isEqualTo("PO-20260101-001");
    }

    @Test
    @DisplayName("findLatestByDatePrefix - returns empty when no matching prefix")
    void testFindLatestByDatePrefix_NoMatch() {
        List<PurchaseOrder> latest = purchaseOrderRepository.findLatestByDatePrefix("PO-20251231");

        assertThat(latest).isEmpty();
    }

    // ========== countByStatus ==========

    @Test
    @DisplayName("countByStatus - returns correct count for ORDERING")
    void testCountByStatus_Ordering() {
        long count = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.ORDERING);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByStatus - returns 0 for status with no orders")
    void testCountByStatus_Zero() {
        long count = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.COMPLETED);

        assertThat(count).isEqualTo(0);
    }

    // ========== soft delete ==========

    @Test
    @DisplayName("soft delete - deleted order does not appear in queries")
    void testSoftDelete_HiddenFromQueries() {
        purchaseOrderRepository.delete(orderOrdering1);
        entityManager.flush();
        entityManager.clear();

        Optional<PurchaseOrder> found = purchaseOrderRepository.findByPoNumber("PO-20260101-001");
        assertThat(found).isEmpty();

        long count = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.ORDERING);
        assertThat(count).isEqualTo(1);
    }
}
