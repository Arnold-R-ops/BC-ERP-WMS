package com.wms.system.service;

import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.ProductSkuRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderImportMatchingTest {
    private final ProductSkuRepository products = mock(ProductSkuRepository.class);
    private final ExcelTemplateService templates = new ExcelTemplateService(products, "");
    private final ExcelImportService purchases = new ExcelImportService(templates, products);
    private final SalesEntryService sales = new SalesEntryService(mock(InventoryBatchRepository.class), products, templates);

    @Test void skuCodesResolveBeforePurchaseDraftCreation() throws Exception {
        when(products.findBySkuCode("SKU-001")).thenReturn(Optional.of(sku()));
        var items = purchases.importPurchaseOrderFromExcel(file(true, "skuCode", new Object[][]{{"SKU-001", 5, 2.5}}));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductSkuId()).isEqualTo(71L);
        assertThat(items.get(0).getOrderedQuantity()).isEqualTo(5);
    }

    @Test void numericLookingBarcodeNeverFallsBackToDatabaseId() throws Exception {
        when(products.findByBarcode("00000071")).thenReturn(Optional.of(sku()));
        var items = sales.importSalesOrderFromExcel(file(false, "barcode", new Object[][]{{"00000071", 2, 4.5}}));
        assertThat(items.get(0).getProductSkuId()).isEqualTo(71L);
        verify(products, never()).findById(anyLong());
        verify(products, never()).findBySkuCode(anyString());
    }

    @Test void allBadRowsReportedAndNothingPartiallyReturned() throws Exception {
        when(products.findBySkuCode("OK")).thenReturn(Optional.of(sku()));
        var input = file(true, "skuCode", new Object[][]{{"MISSING", 1, 1}, {}, {"OK", 2, 1}, {"OK", 1.5, 1}});
        var error = catchThrowableOfType(() -> purchases.importPurchaseOrderFromExcel(input), BusinessException.class);
        assertThat(error.getParams()).containsEntry("validRows", 1).containsEntry("failedRows", 2).containsEntry("skippedRows", 1);
        @SuppressWarnings("unchecked") var rows = (List<Map<String,Object>>)error.getParam("errors");
        assertThat(rows).extracting(row -> row.get("rowIndex")).containsExactly(2, 5);
    }

    @Test void disabledAndExcelRoundedIdentifiersAreRejectedTogether() throws Exception {
        var disabled = sku(); disabled.setEnabled(false);
        when(products.findByBarcode("DISABLED")).thenReturn(Optional.of(disabled));
        var input = file(false, "barcode", new Object[][]{{"DISABLED", 2, 1}, {1234567890123456d, 1, 1}});
        var error = catchThrowableOfType(() -> sales.importSalesOrderFromExcel(input), BusinessException.class);
        assertThat(error.getParam("failedRows")).isEqualTo(2);
        assertThat(error.getParam("validRows")).isEqualTo(0);
    }

    @Test void oldIdTemplatesRemainUsable() throws Exception {
        var items = purchases.importPurchaseOrderFromExcel(file(true, "productSkuId", new Object[][]{{71, 3, 2}}));
        assertThat(items.get(0).getProductSkuId()).isEqualTo(71L);
        verifyNoInteractions(products);
    }

    private ProductSku sku() { return ProductSku.builder().id(71L).enabled(true).build(); }
    private MockMultipartFile file(boolean purchase, String identifier, Object[][] values) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet(purchase ? ExcelTemplateService.PURCHASE_SHEET : ExcelTemplateService.SALES_SHEET);
            var headers = purchase ? new String[]{identifier,"orderedQuantity","unitCost","expiryDate","productionDate","externalBatchCode","remark"}
                : new String[]{identifier,"quantity","unitPrice","rejectNearExpiry","specifiedBatchIds","remark"};
            var header = sheet.createRow(0);
            for(int i=0;i<headers.length;i++)header.createCell(i).setCellValue(headers[i]);
            for(int i=0;i<values.length;i++) {
                var row=sheet.createRow(i+1);
                for(int j=0;j<values[i].length;j++) {
                    var cell=row.createCell(j); var value=values[i][j];
                    if(value instanceof Number number)cell.setCellValue(number.doubleValue());else cell.setCellValue(value.toString());
                }
            }
            book.write(out);
            return new MockMultipartFile("file","order.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
        }
    }
}
