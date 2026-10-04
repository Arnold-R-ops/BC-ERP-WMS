package com.wms.system.controller;

import com.wms.system.exception.ErrorKeys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerConcurrencyUnitTest {
    @Test
    void outboundOptimisticConflictMapsTo409NotAnUnclassifiedServerError() {
        var request = new MockHttpServletRequest("POST", "/api/outbound-tasks/1/confirm-dispatch");
        var exception = new ObjectOptimisticLockingFailureException("OutboundTask", 1L);
        var response = new GlobalExceptionHandler().handleOptimisticLockingFailure(exception, request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getErrorKey()).isEqualTo(ErrorKeys.STOCK_CONCURRENCY_CONFLICT);
        assertThat(response.getBody().getPath()).isEqualTo(request.getRequestURI());
    }
}
