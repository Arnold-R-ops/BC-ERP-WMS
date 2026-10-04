package com.wms.system.service;

import com.wms.system.dto.report.CustomerFactListItemResponse;
import com.wms.system.entity.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportCsvServiceTest {
    @Test void preservesChineseQuotesCommasNewlinesAndNeutralizesFormulas() {
        String csv = ReportCsvService.csv(Collections.singletonList(new Object[] {"袋,瓶", "a\"b\nc", " =1+2", -3, null}));
        assertThat(csv).isEqualTo("\uFEFF\"袋,瓶\",\"a\"\"b\nc\",\"' =1+2\",\"-3\",\"\"\r\n");
    }
    @Test void exportsAllFilteredPagesWithExplicitLifetimeScope() {
        ReportQueryService query = mock(ReportQueryService.class);
        var first = CustomerFactListItemResponse.builder().customerId(1L).customerName("客户甲").totalAmount(BigDecimal.TEN).build();
        var second = CustomerFactListItemResponse.builder().customerId(2L).customerName("客户乙").build();
        when(query.listCustomerFacts("客户", CustomerType.CLIENT, CustomerSource.MANUAL, "totalAmount", "desc", 0, 100))
            .thenReturn(new PageImpl<>(List.of(first), PageRequest.of(0, 100), 101));
        when(query.listCustomerFacts("客户", CustomerType.CLIENT, CustomerSource.MANUAL, "totalAmount", "desc", 1, 100))
            .thenReturn(new PageImpl<>(List.of(second), PageRequest.of(1, 100), 101));
        String csv = new ReportCsvService(query).customers("客户", CustomerType.CLIENT, CustomerSource.MANUAL, "totalAmount", "desc");
        assertThat(csv).contains("客户甲", "客户乙", "Lifetime;", "APPROVED/COMPLETED/CLOSED");
        assertThat(csv.split("\r\n")).hasSize(3);
        verify(query, times(2)).listCustomerFacts(anyString(), eq(CustomerType.CLIENT), eq(CustomerSource.MANUAL), anyString(), anyString(), anyInt(), eq(100));
    }
}
