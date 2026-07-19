package com.wms.system.service;

import com.wms.system.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelImportService Tests")
class ExcelImportServiceTest {

    @InjectMocks
    private ExcelImportService excelImportService;

    // ========== importPurchaseOrderFromExcel ==========

    @Test
    @DisplayName("importPurchaseOrderFromExcel - throws when file is not xlsx")
    void testImport_WrongExtension() {
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "orders.csv", "text/csv", "a,b,c".getBytes()
        );

        assertThatThrownBy(() -> excelImportService.importPurchaseOrderFromExcel(csvFile))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("importPurchaseOrderFromExcel - throws when filename is null")
    void testImport_NullFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "application/octet-stream", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> excelImportService.importPurchaseOrderFromExcel(file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("importPurchaseOrderFromExcel - returns empty list when only header row")
    void testImport_OnlyHeaderRow() throws Exception {
        byte[] xlsxBytes = createXlsxWithHeaderOnly();
        MockMultipartFile file = new MockMultipartFile(
                "file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        var result = excelImportService.importPurchaseOrderFromExcel(file);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("importPurchaseOrderFromExcel - parses valid rows")
    void testImport_ValidRows() throws Exception {
        byte[] xlsxBytes = createXlsxWithValidData();
        MockMultipartFile file = new MockMultipartFile(
                "file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        var result = excelImportService.importPurchaseOrderFromExcel(file);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductSkuId()).isEqualTo(1L);
        assertThat(result.get(0).getOrderedQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("importPurchaseOrderFromExcel - skips empty rows")
    void testImport_SkipsEmptyRows() throws Exception {
        byte[] xlsxBytes = createXlsxWithEmptyRow();
        MockMultipartFile file = new MockMultipartFile(
                "file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        var result = excelImportService.importPurchaseOrderFromExcel(file);

        // Empty rows should be skipped
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("importPurchaseOrderFromExcel - throws BusinessException on invalid cell data")
    void testImport_InvalidCellData() throws Exception {
        byte[] xlsxBytes = createXlsxWithInvalidData();
        MockMultipartFile file = new MockMultipartFile(
                "file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        assertThatThrownBy(() -> excelImportService.importPurchaseOrderFromExcel(file))
                .isInstanceOf(BusinessException.class);
    }

    // ========== Helpers ==========

    private byte[] createXlsxWithHeaderOnly() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("productSkuId");
        header.createCell(1).setCellValue("orderedQuantity");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

    private byte[] createXlsxWithValidData() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");
        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("productSkuId");
        header.createCell(1).setCellValue("orderedQuantity");
        // Data row
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue(1.0);   // productSkuId
        data.createCell(1).setCellValue(100.0); // orderedQuantity
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

    private byte[] createXlsxWithEmptyRow() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");
        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("productSkuId");
        // Empty row 1 (null row)
        // Valid data row 2
        Row data = sheet.createRow(2);
        data.createCell(0).setCellValue(1.0);
        data.createCell(1).setCellValue(50.0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

    private byte[] createXlsxWithInvalidData() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");
        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("productSkuId");
        // Invalid data: productSkuId is text, not numeric
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue("NOT_A_NUMBER"); // invalid productSkuId
        data.createCell(1).setCellValue(100.0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }
}
