package com.wms.system.service;

import com.wms.system.event.SalesOrderFactsChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SalesOrderFactsChangedListenerTest {

    @Mock private ReportSummaryRefreshService reportSummaryRefreshService;
    @InjectMocks private SalesOrderFactsChangedListener listener;

    @Test
    void refreshesOnlyTheAffectedSalesDay() {
        LocalDate summaryDate = LocalDate.of(2026, 6, 10);
        SalesOrderFactsChangedEvent event = new SalesOrderFactsChangedEvent(
            1L, 13L, summaryDate, "ARCHIVE_HISTORICAL_TEST_DATA"
        );

        listener.refreshSalesDailySummary(event);

        verify(reportSummaryRefreshService).refreshSalesDailySummary(1L, summaryDate);
    }

    @Test
    void containsRefreshFailureBecauseTheSourceTransactionAlreadyCommitted() {
        LocalDate summaryDate = LocalDate.of(2026, 6, 10);
        SalesOrderFactsChangedEvent event = new SalesOrderFactsChangedEvent(
            1L, 13L, summaryDate, "ARCHIVE_HISTORICAL_TEST_DATA"
        );
        doThrow(new IllegalStateException("transient report failure"))
            .when(reportSummaryRefreshService).refreshSalesDailySummary(1L, summaryDate);

        assertThatCode(() -> listener.refreshSalesDailySummary(event)).doesNotThrowAnyException();
    }

    @Test
    void listenerRunsAfterCommitAndTargetedRefreshUsesANewTransaction() throws NoSuchMethodException {
        TransactionalEventListener listenerBoundary = SalesOrderFactsChangedListener.class
            .getMethod("refreshSalesDailySummary", SalesOrderFactsChangedEvent.class)
            .getAnnotation(TransactionalEventListener.class);
        Transactional refreshBoundary = ReportSummaryRefreshService.class
            .getMethod("refreshSalesDailySummary", Long.class, LocalDate.class)
            .getAnnotation(Transactional.class);

        assertThat(listenerBoundary).isNotNull();
        assertThat(listenerBoundary.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(refreshBoundary).isNotNull();
        assertThat(refreshBoundary.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
