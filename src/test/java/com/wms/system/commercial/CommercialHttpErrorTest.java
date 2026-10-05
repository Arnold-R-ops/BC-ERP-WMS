package com.wms.system.commercial;

import com.wms.system.controller.GlobalExceptionHandler;
import com.wms.system.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated HTTP regression: no Spring application or database is started. */
class CommercialHttpErrorTest {
    @Test void overpaymentIsAClientRejectionOnTheReceiptEndpoint() throws Exception {
        var service = mock(CommercialService.class);
        when(service.recordReceipt(eq(2L), any(), anyLong()))
            .thenThrow(new BusinessException("COMMERCIAL_OVERPAYMENT"));
        var mvc = MockMvcBuilders.standaloneSetup(new CommercialController(service, mock(CommercialExportService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/commercial/invoices/2/receipts").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"requestKey":"71204637-fb20-4030-82f8-f0604b46fb13","amount":28.00,
                 "paymentDate":"2020-01-01","method":"BANK_TRANSFER"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorKey").value("COMMERCIAL_OVERPAYMENT"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.path").value("/api/commercial/invoices/2/receipts"));
        verify(service).recordReceipt(eq(2L), any(), anyLong());
    }

    @ParameterizedTest
    @CsvSource({
        "COMMERCIAL_INVALID_KIND,400", "COMMERCIAL_INVALID_AMOUNT,400", "COMMERCIAL_EMPTY_INVOICE,400",
        "COMMERCIAL_FUTURE_PAYMENT,400", "COMMERCIAL_CUSTOMER_IMMUTABLE,400", "COMMERCIAL_OVERPAYMENT,400",
        "COMMERCIAL_DOCUMENT_NOT_FOUND,404", "COMMERCIAL_RECEIPT_NOT_FOUND,404",
        "COMMERCIAL_STALE_VERSION,409", "COMMERCIAL_REQUEST_CONFLICT,409", "COMMERCIAL_INVALID_STATE,409",
        "COMMERCIAL_QUOTE_EXPIRED,409", "COMMERCIAL_ORDER_NOT_CONFIRMED,409", "COMMERCIAL_PAID_INVOICE_VOID,409",
        "STOCK_INSUFFICIENT,400", "UNCLASSIFIED_REGRESSION_ERROR,500"
    })
    void businessRejectionsKeepTheirHttpMeaningWithoutChangingUnknownErrors(String key, int status) {
        var request = new MockHttpServletRequest("POST", "/api/commercial/invoices/2/receipts");
        var response = new GlobalExceptionHandler().handleBusinessException(new BusinessException(key), request);
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().getStatus()).isEqualTo(status);
        assertThat(response.getBody().getErrorKey()).isEqualTo(key);
    }
}
