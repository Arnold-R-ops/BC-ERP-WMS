package com.wms.system.service;

import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.IdempotencyRequestRepository;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final long COMPANY_ID = 42L;
    private static final String KEY = "command-1";
    private static final String OPERATION = "INVENTORY_POST";
    private static final String BODY = "{\"quantity\":10}";

    @Mock
    private IdempotencyRequestRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository);
        TenantContextHolder.set(new TenantContext(
            COMPANY_ID,
            "company-42",
            "company-42.example.test",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void startWithBlankKeyPreservesLegacyBypass() {
        assertThat(service.start(" ", OPERATION, BODY)).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void newClaimUsesCompanyScopedInsertBeforeRowLock() {
        IdempotencyRequest record = record(IdempotencyStatus.PROCESSING);
        when(repository.insertReservation(
            eq(COMPANY_ID), eq(KEY), eq(OPERATION),
            eq(IdempotencyService.requestHash(OPERATION, BODY)),
            any(LocalDateTime.class), anyString()
        )).thenReturn(1);
        when(repository.findByCompanyAndKeyForUpdate(COMPANY_ID, KEY))
            .thenReturn(Optional.of(record));

        IdempotencyClaim claim = service.claim(KEY, OPERATION, BODY);

        assertThat(claim.decision()).isEqualTo(IdempotencyClaimDecision.ACQUIRED);
        assertThat(claim.shouldExecute()).isTrue();
        assertThat(claim.request()).isSameAs(record);
        InOrder order = inOrder(repository);
        order.verify(repository).insertReservation(
            eq(COMPANY_ID), eq(KEY), eq(OPERATION), anyString(),
            any(LocalDateTime.class), anyString()
        );
        order.verify(repository).findByCompanyAndKeyForUpdate(COMPANY_ID, KEY);
    }

    @Test
    void sameIntentSucceededIsReplayable() {
        IdempotencyRequest record = record(IdempotencyStatus.SUCCEEDED);
        record.setResponseStatus(201);
        record.setResponseBody("saved");
        stubExisting(record);

        IdempotencyClaim claim = service.claim(KEY, OPERATION, BODY);

        assertThat(claim.decision()).isEqualTo(IdempotencyClaimDecision.REPLAY_SUCCEEDED);
        assertThat(claim.isReplay()).isTrue();
        assertThat(service.start(KEY, OPERATION, BODY).getResponseBody()).isEqualTo("saved");
    }

    @Test
    void sameIntentFailedRemainsTypedReplayWhileLegacyStartReacquiresIt() {
        IdempotencyRequest record = record(IdempotencyStatus.FAILED);
        record.setResponseStatus(422);
        record.setResponseBody("failed");
        stubExisting(record);
        when(repository.saveAndFlush(record)).thenReturn(record);

        assertThat(service.claim(KEY, OPERATION, BODY).decision())
            .isEqualTo(IdempotencyClaimDecision.REPLAY_FAILED);
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.FAILED);

        IdempotencyRequest retried = service.start(KEY, OPERATION, BODY);

        assertThat(retried).isSameAs(record);
        assertThat(retried.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(retried.getLeaseToken()).isNotBlank();
        assertThat(retried.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(4));
        assertThat(retried.getResponseStatus()).isNull();
        assertThat(retried.getResponseBody()).isNull();
        verify(repository).saveAndFlush(record);
    }

    @Test
    void liveProcessingLeaseDoesNotGrantSecondExecution() {
        IdempotencyRequest record = record(IdempotencyStatus.PROCESSING);
        record.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        stubExisting(record);

        assertThat(service.claim(KEY, OPERATION, BODY).decision())
            .isEqualTo(IdempotencyClaimDecision.IN_PROGRESS);
        assertThatThrownBy(() -> service.start(KEY, OPERATION, BODY))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS);
                assertThat(exception.getParam("reason"))
                    .isEqualTo("IDEMPOTENCY_REQUEST_PROCESSING");
            });
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void expiredProcessingLeaseCanBeTakenOver() {
        IdempotencyRequest record = record(IdempotencyStatus.PROCESSING);
        record.setLeaseToken("expired-owner");
        record.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        stubExisting(record);
        when(repository.saveAndFlush(record)).thenReturn(record);

        IdempotencyClaim claim = service.claim(KEY, OPERATION, BODY);

        assertThat(claim.decision()).isEqualTo(IdempotencyClaimDecision.ACQUIRED);
        assertThat(record.getLeaseToken()).isNotBlank().isNotEqualTo("expired-owner");
        assertThat(record.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(4));
        verify(repository).saveAndFlush(record);
    }

    @Test
    void sameKeyWithDifferentOperationConflicts() {
        IdempotencyRequest record = record(IdempotencyStatus.SUCCEEDED);
        stubExisting(record);

        assertIntentConflict(() -> service.claim(KEY, "OTHER_OPERATION", BODY));
    }

    @Test
    void sameKeyWithDifferentRequestBodyConflicts() {
        IdempotencyRequest record = record(IdempotencyStatus.SUCCEEDED);
        stubExisting(record);

        assertIntentConflict(() -> service.claim(KEY, OPERATION, "{\"quantity\":11}"));
    }

    @Test
    void requestHashIncludesOperationAndExactBody() {
        assertThat(IdempotencyService.requestHash(OPERATION, BODY))
            .hasSize(64)
            .isNotEqualTo(IdempotencyService.requestHash("OTHER_OPERATION", BODY))
            .isNotEqualTo(IdempotencyService.requestHash(OPERATION, BODY + " "));
    }

    @Test
    void successfulCompletionRequiresCurrentLeaseAndClearsIt() {
        IdempotencyRequest claimed = record(IdempotencyStatus.PROCESSING);
        claimed.setLeaseToken("owner-1");
        claimed.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        IdempotencyRequest current = copy(claimed);
        when(repository.findByCompanyAndIdForUpdate(COMPANY_ID, claimed.getId()))
            .thenReturn(Optional.of(current));
        when(repository.saveAndFlush(current)).thenReturn(current);

        service.markSucceeded(claimed, 200, "ok");

        assertThat(current.getStatus()).isEqualTo(IdempotencyStatus.SUCCEEDED);
        assertThat(current.getResponseStatus()).isEqualTo(200);
        assertThat(current.getResponseBody()).isEqualTo("ok");
        assertThat(current.getLockedUntil()).isNull();
        assertThat(current.getLeaseToken()).isNull();
        assertThat(claimed.getStatus()).isEqualTo(IdempotencyStatus.SUCCEEDED);
    }

    @Test
    void staleLeaseOwnerCannotCompleteAfterTakeover() {
        IdempotencyRequest claimed = record(IdempotencyStatus.PROCESSING);
        claimed.setLeaseToken("old-owner");
        IdempotencyRequest current = copy(claimed);
        current.setLeaseToken("new-owner");
        when(repository.findByCompanyAndIdForUpdate(COMPANY_ID, claimed.getId()))
            .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.markFailed(claimed, 500, "late failure"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getParam("reason"))
                    .isEqualTo("IDEMPOTENCY_LEASE_LOST"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void repeatingSameTerminalCompletionReturnsPersistedResult() {
        IdempotencyRequest claimed = record(IdempotencyStatus.PROCESSING);
        claimed.setLeaseToken("owner-1");
        IdempotencyRequest current = copy(claimed);
        current.setStatus(IdempotencyStatus.FAILED);
        current.setResponseStatus(409);
        current.setResponseBody("persisted failure");
        current.setLeaseToken(null);
        when(repository.findByCompanyAndIdForUpdate(COMPANY_ID, claimed.getId()))
            .thenReturn(Optional.of(current));

        service.markFailed(claimed, 500, "different late value");

        assertThat(claimed.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(claimed.getResponseStatus()).isEqualTo(409);
        assertThat(claimed.getResponseBody()).isEqualTo("persisted failure");
        verify(repository, never()).saveAndFlush(any());
    }

    private void stubExisting(IdempotencyRequest record) {
        when(repository.insertReservation(
            eq(COMPANY_ID), eq(KEY), anyString(), anyString(),
            any(LocalDateTime.class), anyString()
        )).thenReturn(0);
        when(repository.findByCompanyAndKeyForUpdate(COMPANY_ID, KEY))
            .thenReturn(Optional.of(record));
    }

    private IdempotencyRequest record(IdempotencyStatus status) {
        IdempotencyRequest record = IdempotencyRequest.builder()
            .id(9L)
            .idempotencyKey(KEY)
            .operation(OPERATION)
            .requestHash(IdempotencyService.requestHash(OPERATION, BODY))
            .status(status)
            .build();
        record.setCompanyId(COMPANY_ID);
        return record;
    }

    private IdempotencyRequest copy(IdempotencyRequest source) {
        IdempotencyRequest copy = IdempotencyRequest.builder()
            .id(source.getId())
            .idempotencyKey(source.getIdempotencyKey())
            .operation(source.getOperation())
            .requestHash(source.getRequestHash())
            .status(source.getStatus())
            .responseStatus(source.getResponseStatus())
            .responseBody(source.getResponseBody())
            .lockedUntil(source.getLockedUntil())
            .leaseToken(source.getLeaseToken())
            .build();
        copy.setCompanyId(source.getCompanyId());
        return copy;
    }

    private void assertIntentConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.IDEMPOTENCY_CONFLICT);
                assertThat(exception.getParam("reason"))
                    .isEqualTo("IDEMPOTENCY_KEY_REUSED_FOR_DIFFERENT_INTENT");
            });
    }
}
