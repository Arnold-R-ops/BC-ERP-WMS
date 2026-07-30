package com.wms.system.service;

import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.ProductSkuRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelTemplateServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void builtInPurchaseTemplateContainsStableHeadersAndSkuReference() throws Exception {
        ProductSkuRepository repository = repositoryWithOneSku();
        ExcelTemplateService service = new ExcelTemplateService(repository, "");

        byte[] result = service.getPurchaseOrderTemplate();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            Sheet dataSheet = workbook.getSheet(ExcelTemplateService.PURCHASE_SHEET);
            assertThat(dataSheet).isNotNull();
            assertThat(dataSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("productSkuId");
            assertThat(dataSheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("orderedQuantity");
            Sheet reference = workbook.getSheet(ExcelTemplateService.SKU_REFERENCE_SHEET);
            assertThat(reference.getRow(1).getCell(1).getStringCellValue()).isEqualTo("SKU00000001");
        }
    }

    @Test
    void validExternalTemplateIsUsedAndKeepsCustomContent() throws Exception {
        ProductSkuRepository repository = repositoryWithOneSku();
        Path templatePath = tempDirectory.resolve(ExcelTemplateService.SALES_FILE);
        try (Workbook workbook = salesWorkbook("productSkuId")) {
            workbook.getSheet(ExcelTemplateService.SALES_SHEET).createRow(2).createCell(5)
                    .setCellValue("custom-layout-marker");
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                workbook.write(output);
                Files.write(templatePath, output.toByteArray());
            }
        }
        ExcelTemplateService service = new ExcelTemplateService(repository, tempDirectory.toString());

        byte[] result = service.getSalesOrderTemplate();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertThat(workbook.getSheet(ExcelTemplateService.SALES_SHEET)
                    .getRow(2).getCell(5).getStringCellValue()).isEqualTo("custom-layout-marker");
        }
    }

    @Test
    void invalidExternalTemplateFallsBackToBuiltInTemplate() throws Exception {
        ProductSkuRepository repository = repositoryWithOneSku();
        Path templatePath = tempDirectory.resolve(ExcelTemplateService.SALES_FILE);
        try (Workbook workbook = salesWorkbook("wrongHeader");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            Files.write(templatePath, output.toByteArray());
        }
        ExcelTemplateService service = new ExcelTemplateService(repository, tempDirectory.toString());

        byte[] result = service.getSalesOrderTemplate();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertThat(workbook.getSheet(ExcelTemplateService.SALES_SHEET)
                    .getRow(0).getCell(0).getStringCellValue()).isEqualTo("productSkuId");
        }
    }

    @Test
    void invalidExternalDirectoryFallsBackToBuiltInTemplate() throws Exception {
        ExcelTemplateService service = new ExcelTemplateService(
            repositoryWithOneSku(),
            "invalid\u0000directory"
        );

        byte[] result = service.getSalesOrderTemplate();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertThat(workbook.getSheet(ExcelTemplateService.SALES_SHEET)).isNotNull();
        }
    }

    @Test
    void importValidationRejectsChangedColumnOrder() throws Exception {
        ExcelTemplateService service = new ExcelTemplateService(repositoryWithOneSku(), "");
        try (Workbook workbook = salesWorkbook("quantity")) {
            assertThatThrownBy(() -> service.validateAndGetSalesOrderSheet(workbook))
                    .isInstanceOf(BusinessException.class);
        }
    }

    private ProductSkuRepository repositoryWithOneSku() {
        ProductSkuRepository repository = mock(ProductSkuRepository.class);
        Product product = Product.builder()
                .productCode("P0000001")
                .productName("Test product")
                .enabled(true)
                .build();
        ProductSku sku = ProductSku.builder()
                .id(1L)
                .skuCode("SKU00000001")
                .skuName("500ml")
                .name("Test product 500ml")
                .barcode("6900000000001")
                .enabled(true)
                .product(product)
                .build();
        when(repository.findOperationalTemplateRows(any(Pageable.class))).thenReturn(List.of(sku));
        return repository;
    }

    private Workbook salesWorkbook(String firstHeader) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(ExcelTemplateService.SALES_SHEET);
        Row row = sheet.createRow(0);
        String[] headers = {
            firstHeader,
            "quantity",
            "unitPrice",
            "rejectNearExpiry",
            "specifiedBatchIds",
            "remark"
        };
        for (int index = 0; index < headers.length; index++) {
            row.createCell(index).setCellValue(headers[index]);
        }
        return workbook;
    }
}
