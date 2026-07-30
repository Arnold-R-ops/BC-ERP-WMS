package com.wms.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportSummaryRefreshServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReportSummaryRefreshService reportSummaryRefreshService;

    @Test
    void refreshAllUpdatesEveryFactSetWithinOneServiceCall() {
        when(jdbcTemplate.update(anyString(), eq(1L))).thenReturn(2);

        ReportSummaryRefreshService.RefreshResult result = reportSummaryRefreshService.refreshAll();

        assertThat(result.customerRows()).isEqualTo(2);
        assertThat(result.customerProductRows()).isEqualTo(2);
        assertThat(result.salesDailyRows()).isEqualTo(2);
        verify(jdbcTemplate, times(6)).update(anyString(), eq(1L));
    }
}
