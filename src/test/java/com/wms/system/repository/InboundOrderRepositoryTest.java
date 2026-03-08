package com.wms.system.repository;

import com.wms.system.entity.InboundOrder;
import com.wms.system.entity.Supplier;
import com.wms.system.entity.enums.InboundOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("InboundOrderRepository Tests")
class InboundOrderRepositoryTest {

    @Autowired private InboundOrderRepository inboundOrderRepository;
    @Autowired private TestEntityManager entityManager;

    private Supplier supplierAlpha;
    private Supplier supplierBeta;
    private InboundOrder orderPending;
    private InboundOrder orderApproved;
    private InboundOrder orderCompleted;

    @BeforeEach
    void setUp() {
        supplierAlpha = Supplier.builder()
                .code("SUP-ALPHA")
                .name("Alpha Supplier Co.")
                .contact("Alice")
                .isActive(true)
                .build();

        supplierBeta = Supplier.builder()
                .code("SUP-BETA")
                .name("Beta Supplier Ltd.")
                .contact("Bob")
                .isActive(true)
                .build();

        entityManager.persist(supplierAlpha);
        entityManager.persist(supplierBeta);
        entityManager.flush();

        orderPending = InboundOrder.builder()
                .orderNo("IN-20260101-001")
                .supplier(supplierAlpha)
                .status(InboundOrderStatus.PENDING_APPROVAL)
                .totalPlanQty(100)
                .totalActualQty(0)
                .applicantId(1L)
                .applicantName("Alice")
                .items(new ArrayList<>())
                .build();

        orderApproved = InboundOrder.builder()
                .orderNo("IN-20260101-002")
                .supplier(supplierAlpha)
                .status(InboundOrderStatus.APPROVED_PLAN)
                .totalPlanQty(200)
                .totalActualQty(0)
                .applicantId(1L)
                .applicantName("Alice")
                .items(new ArrayList<>())
                .build();

        orderCompleted = InboundOrder.builder()
                .orderNo("IN-20260102-001")
                .supplier(supplierBeta)
                .status(InboundOrderStatus.COMPLETED)
                .totalPlanQty(50)
                .totalActualQty(50)
                .applicantId(2L)
                .applicantName("Bob")
                .items(new ArrayList<>())
                .build();

        entityManager.persist(orderPending);
        entityManager.persist(orderApproved);
        entityManager.persist(orderCompleted);
        entityManager.flush();
    }

    // ========== findByOrderNo ==========

    @Test
    @DisplayName("findByOrderNo - returns inbound order when order number exists")
    void testFindByOrderNo_Found() {
        Optional<InboundOrder> found = inboundOrderRepository.findByOrderNo("IN-20260101-001");

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(InboundOrderStatus.PENDING_APPROVAL);
        assertThat(found.get().getApplicantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findByOrderNo - returns empty when order number does not exist")
    void testFindByOrderNo_NotFound() {
        Optional<InboundOrder> found = inboundOrderRepository.findByOrderNo("IN-NONEXISTENT");

        assertThat(found).isEmpty();
    }

    // ========== existsByOrderNo ==========

    @Test
    @DisplayName("existsByOrderNo - returns true when order number exists")
    void testExistsByOrderNo_Exists() {
        boolean exists = inboundOrderRepository.existsByOrderNo("IN-20260101-001");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByOrderNo - returns false when order number does not exist")
    void testExistsByOrderNo_NotExists() {
        boolean exists = inboundOrderRepository.existsByOrderNo("IN-NONEXISTENT");

        assertThat(exists).isFalse();
    }

    // ========== findByStatus ==========

    @Test
    @DisplayName("findByStatus - returns orders with PENDING_APPROVAL status")
    void testFindByStatus_Pending() {
        List<InboundOrder> results = inboundOrderRepository.findByStatus(InboundOrderStatus.PENDING_APPROVAL);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrderNo()).isEqualTo("IN-20260101-001");
    }

    @Test
    @DisplayName("findByStatus - returns orders with COMPLETED status")
    void testFindByStatus_Completed() {
        List<InboundOrder> results = inboundOrderRepository.findByStatus(InboundOrderStatus.COMPLETED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrderNo()).isEqualTo("IN-20260102-001");
    }

    @Test
    @DisplayName("findByStatus - returns empty when no orders in status")
    void testFindByStatus_Empty() {
        List<InboundOrder> results = inboundOrderRepository.findByStatus(InboundOrderStatus.REJECTED);

        assertThat(results).isEmpty();
    }

    // ========== findBySupplierId ==========

    @Test
    @DisplayName("findBySupplierId - returns all orders for given supplier")
    void testFindBySupplierId_Found() {
        List<InboundOrder> results = inboundOrderRepository.findBySupplierId(supplierAlpha.getId());

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(InboundOrder::getOrderNo)
                .containsExactlyInAnyOrder("IN-20260101-001", "IN-20260101-002");
    }

    @Test
    @DisplayName("findBySupplierId - returns single order for supplier beta")
    void testFindBySupplierId_SupplierBeta() {
        List<InboundOrder> results = inboundOrderRepository.findBySupplierId(supplierBeta.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrderNo()).isEqualTo("IN-20260102-001");
    }

    @Test
    @DisplayName("findBySupplierId - returns empty for unknown supplier id")
    void testFindBySupplierId_NotFound() {
        List<InboundOrder> results = inboundOrderRepository.findBySupplierId(9999L);

        assertThat(results).isEmpty();
    }

    // ========== findByApplicantId ==========

    @Test
    @DisplayName("findByApplicantId - returns orders for applicant 1")
    void testFindByApplicantId_Applicant1() {
        List<InboundOrder> results = inboundOrderRepository.findByApplicantId(1L);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(InboundOrder::getOrderNo)
                .containsExactlyInAnyOrder("IN-20260101-001", "IN-20260101-002");
    }

    @Test
    @DisplayName("findByApplicantId - returns orders for applicant 2")
    void testFindByApplicantId_Applicant2() {
        List<InboundOrder> results = inboundOrderRepository.findByApplicantId(2L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getApplicantName()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("findByApplicantId - returns empty for unknown applicant")
    void testFindByApplicantId_NotFound() {
        List<InboundOrder> results = inboundOrderRepository.findByApplicantId(9999L);

        assertThat(results).isEmpty();
    }

    // ========== countByStatus ==========

    @Test
    @DisplayName("countByStatus - returns correct count for PENDING_APPROVAL")
    void testCountByStatus_Pending() {
        long count = inboundOrderRepository.countByStatus(InboundOrderStatus.PENDING_APPROVAL);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("countByStatus - returns 0 for status with no orders")
    void testCountByStatus_Zero() {
        long count = inboundOrderRepository.countByStatus(InboundOrderStatus.AWAITING_RECEIVAL);

        assertThat(count).isEqualTo(0);
    }

    // ========== findByCreatedAtBetween ==========

    @Test
    @DisplayName("findByCreatedAtBetween - returns orders within time range")
    void testFindByCreatedAtBetween_Found() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusMinutes(5);

        List<InboundOrder> results = inboundOrderRepository.findByCreatedAtBetween(start, end);

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("findByCreatedAtBetween - returns empty list outside time range")
    void testFindByCreatedAtBetween_OutOfRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(365);
        LocalDateTime end = LocalDateTime.now().minusDays(364);

        List<InboundOrder> results = inboundOrderRepository.findByCreatedAtBetween(start, end);

        assertThat(results).isEmpty();
    }

    // ========== basic CRUD ==========

    @Test
    @DisplayName("count - returns total number of inbound orders")
    void testCount() {
        long count = inboundOrderRepository.count();

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("save - persists new inbound order with audit timestamps")
    void testSave_NewOrder() {
        InboundOrder newOrder = InboundOrder.builder()
                .orderNo("IN-20260201-001")
                .supplier(supplierAlpha)
                .status(InboundOrderStatus.PENDING_APPROVAL)
                .totalPlanQty(75)
                .totalActualQty(0)
                .applicantId(1L)
                .applicantName("Alice")
                .items(new ArrayList<>())
                .build();

        InboundOrder saved = inboundOrderRepository.save(newOrder);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(inboundOrderRepository.findByOrderNo("IN-20260201-001")).isPresent();
    }
}
