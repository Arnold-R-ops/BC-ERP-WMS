package com.wms.system.commercial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(CommercialPermissionTest.Config.class)
class CommercialPermissionTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean CommercialService service(){return mock(CommercialService.class);}
        @Bean CommercialExportService exports(){return mock(CommercialExportService.class);}
        @Bean CommercialController controller(CommercialService s,CommercialExportService e){return new CommercialController(s,e);}
    }
    @Autowired CommercialController controller;
    @Autowired CommercialService service;
    @Autowired CommercialExportService exports;
    @AfterEach void clear(){reset(service,exports);}

    @Test @WithMockUser(authorities="quotation:view")
    void viewingDoesNotGrantExportOrConversion(){
        assertThatThrownBy(()->controller.quotePdf(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.quoteXlsx(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.convert(1L,null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service,exports);
    }
    @Test @WithMockUser(authorities="commercial:export")
    void exportAlsoRequiresDocumentViewPermission(){
        assertThatThrownBy(()->controller.quotePdf(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.invoicePdf(1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service,exports);
    }
    @Test @WithMockUser(authorities="quotation:convert")
    void conversionRequiresExistingSalesCreationPermission(){
        assertThatThrownBy(()->controller.convert(1L,null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service,exports);
    }
    @Test @WithMockUser(authorities="invoice:view")
    void invoiceViewerCannotReceiveReverseOrVoid(){
        assertThatThrownBy(()->controller.receive(1L,null,null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.reverse(1L,2L,null,null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.voidInvoice(1L,null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service,exports);
    }
    @Test @WithMockUser(authorities={"quotation:view","commercial:export"})
    void authorizedExporterGetsSnapshotAndCannotExportInvoices(){
        var doc=mock(CommercialDtos.DocumentView.class);
        when(doc.kind()).thenReturn("QUOTE");when(doc.number()).thenReturn("QT-1");
        when(service.export(1L)).thenReturn(doc);when(exports.pdf(doc)).thenReturn(new byte[]{1,2});
        assertThat(controller.quotePdf(1L).getBody()).containsExactly((byte)1,(byte)2);
        assertThatThrownBy(()->controller.invoicePdf(1L)).isInstanceOf(AccessDeniedException.class);
        verify(service).export(1L);
    }
    @Test @WithMockUser(authorities="TENANT_ADMIN")
    void documentKindCannotBeChangedByGuessingAnotherEndpoint(){
        var doc=mock(CommercialDtos.DocumentView.class);when(doc.kind()).thenReturn("INVOICE");
        when(service.get(1L)).thenReturn(doc);
        assertThatThrownBy(()->controller.quote(1L)).hasMessage("COMMERCIAL_DOCUMENT_NOT_FOUND");
    }
}
