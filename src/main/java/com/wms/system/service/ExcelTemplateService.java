package com.wms.system.service;

import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.ProductSkuRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Loads operator-maintained Excel import templates and provides a safe built-in fallback.
 *
 * Machine headers are deliberately stable English identifiers. Operators may change colors,
 * fonts, logos, widths and instruction sheets, but not the data sheet name or column order.
 */
@Slf4j
@Service
public class ExcelTemplateService {

    public static final String SALES_SHEET = "Sales Order Lines";
    public static final String PURCHASE_SHEET = "Purchase Order Lines";
    public static final String SKU_REFERENCE_SHEET = "SKU Reference";
    public static final String SALES_FILE = "sales_order_import_template.xlsx";
    public static final String PURCHASE_FILE = "purchase_order_import_template.xlsx";

    private static final int MAX_REFERENCE_ROWS = 5_000;
    private static final DataFormatter CELL_FORMATTER = new DataFormatter();
    private static final List<String> SALES_HEADERS = List.of(
        "productSkuId",
        "quantity",
        "unitPrice",
        "rejectNearExpiry",
        "specifiedBatchIds",
        "remark"
    );
    private static final List<String> PURCHASE_HEADERS = List.of(
        "productSkuId",
        "orderedQuantity",
        "unitCost",
        "expiryDate",
        "productionDate",
        "externalBatchCode",
        "remark"
    );

    private final ProductSkuRepository productSkuRepository;
    private final String externalDirectory;

    public ExcelTemplateService(
        ProductSkuRepository productSkuRepository,
        @Value("${wms.excel-templates.external-directory:}") String externalDirectory
    ) {
        this.productSkuRepository = productSkuRepository;
        this.externalDirectory = externalDirectory == null ? "" : externalDirectory.trim();
    }

    @Transactional(readOnly = true)
    public byte[] getSalesOrderTemplate() {
        return loadTemplate(TemplateDefinition.SALES);
    }

    @Transactional(readOnly = true)
    public byte[] getPurchaseOrderTemplate() {
        return loadTemplate(TemplateDefinition.PURCHASE);
    }

    public Sheet validateAndGetSalesOrderSheet(Workbook workbook) {
        return validateAndGetDataSheet(workbook, TemplateDefinition.SALES);
    }

    public Sheet validateAndGetPurchaseOrderSheet(Workbook workbook) {
        return validateAndGetDataSheet(workbook, TemplateDefinition.PURCHASE);
    }

    private byte[] loadTemplate(TemplateDefinition definition) {
        Workbook workbook = loadExternalTemplate(definition);
        if (workbook == null) {
            workbook = createBuiltInTemplate(definition);
        }

        try (Workbook managedWorkbook = workbook) {
            refreshSkuReference(managedWorkbook);
            return writeWorkbook(managedWorkbook);
        } catch (IOException e) {
            throw templateGenerationFailure(definition, e);
        }
    }

    private Workbook loadExternalTemplate(TemplateDefinition definition) {
        if (externalDirectory.isBlank()) {
            return null;
        }

        Path baseDirectory;
        Path templatePath;
        try {
            baseDirectory = Path.of(externalDirectory).toAbsolutePath().normalize();
            templatePath = baseDirectory.resolve(definition.fileName()).normalize();
        } catch (RuntimeException e) {
            log.warn(
                "External Excel template directory is invalid; using built-in fallback: directory={}, reason={}",
                externalDirectory,
                failureReason(e)
            );
            return null;
        }

        if (!templatePath.startsWith(baseDirectory)) {
            log.warn("Rejected Excel template path outside configured directory: {}", templatePath);
            return null;
        }
        if (!Files.isRegularFile(templatePath)) {
            log.info("External Excel template not found; using built-in fallback: {}", templatePath);
            return null;
        }

        Workbook workbook = null;
        try (InputStream inputStream = Files.newInputStream(templatePath)) {
            workbook = new XSSFWorkbook(inputStream);
            validateAndGetDataSheet(workbook, definition);
            log.info("Using external Excel template: {}", templatePath);
            return workbook;
        } catch (Exception e) {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException closeError) {
                    log.debug("Failed to close invalid Excel template: {}", closeError.getMessage());
                }
            }
            log.warn(
                "External Excel template is invalid; using built-in fallback: path={}, reason={}",
                templatePath,
                failureReason(e)
            );
            return null;
        }
    }

    private Workbook createBuiltInTemplate(TemplateDefinition definition) {
        Workbook workbook = new XSSFWorkbook();
        Sheet dataSheet = workbook.createSheet(definition.sheetName());
        Row headerRow = dataSheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);

        for (int index = 0; index < definition.headers().size(); index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(definition.headers().get(index));
            cell.setCellStyle(headerStyle);
            dataSheet.setColumnWidth(index, definition.columnWidths()[index]);
        }

        dataSheet.createFreezePane(0, 1);
        dataSheet.setAutoFilter(
            new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, definition.headers().size() - 1)
        );
        createInstructionsSheet(workbook, definition);
        return workbook;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private void createInstructionsSheet(Workbook workbook, TemplateDefinition definition) {
        Sheet instructions = workbook.createSheet("Instructions");
        String[] lines = definition == TemplateDefinition.SALES
            ? new String[] {
                "Sales order line import template",
                "Select the customer in the WMS form. This file imports line items only.",
                "Do not rename the data sheet or change the machine headers.",
                "productSkuId and quantity are required. unitPrice must not be negative.",
                "specifiedBatchIds accepts comma-separated inventory batch IDs."
            }
            : new String[] {
                "Purchase order line import template",
                "Select the supplier in the WMS import dialog. This file imports line items only.",
                "Do not rename the data sheet or change the machine headers.",
                "productSkuId and orderedQuantity are required.",
                "Dates use yyyy-MM-dd. Batch and remark fields are optional."
            };

        for (int index = 0; index < lines.length; index++) {
            instructions.createRow(index).createCell(0).setCellValue(lines[index]);
        }
        instructions.setColumnWidth(0, 18_000);
    }

    private void refreshSkuReference(Workbook workbook) {
        int existingIndex = workbook.getSheetIndex(SKU_REFERENCE_SHEET);
        if (existingIndex >= 0) {
            workbook.removeSheetAt(existingIndex);
        }

        Sheet sheet = workbook.createSheet(SKU_REFERENCE_SHEET);
        String[] headers = {"productSkuId", "skuCode", "skuName", "barcode", "productCode", "productName"};
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, index == 0 ? 4_000 : 6_000);
        }

        List<ProductSku> skus = productSkuRepository.findOperationalTemplateRows(
            PageRequest.of(0, MAX_REFERENCE_ROWS)
        );
        int rowIndex = 1;
        for (ProductSku sku : skus) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(sku.getId());
            row.createCell(1).setCellValue(nullToEmpty(sku.getSkuCode()));
            row.createCell(2).setCellValue(nullToEmpty(sku.getSkuName()));
            row.createCell(3).setCellValue(nullToEmpty(sku.getBarcode()));
            if (sku.getProduct() != null) {
                row.createCell(4).setCellValue(nullToEmpty(sku.getProduct().getProductCode()));
                row.createCell(5).setCellValue(nullToEmpty(sku.getProduct().getProductName()));
            }
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    private Sheet validateAndGetDataSheet(Workbook workbook, TemplateDefinition definition) {
        if (workbook == null) {
            throw invalidTemplate(definition, "workbook is missing");
        }

        Sheet sheet = workbook.getSheet(definition.sheetName());
        if (sheet == null) {
            throw invalidTemplate(definition, "required sheet is missing: " + definition.sheetName());
        }

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw invalidTemplate(definition, "header row 1 is missing");
        }

        for (int index = 0; index < definition.headers().size(); index++) {
            String actual = CELL_FORMATTER.formatCellValue(headerRow.getCell(index)).trim();
            String expected = definition.headers().get(index);
            if (!expected.equals(actual)) {
                throw invalidTemplate(
                    definition,
                    "column " + (index + 1) + " must be '" + expected + "' but was '" + actual + "'"
                );
            }
        }
        return sheet;
    }

    private byte[] writeWorkbook(Workbook workbook) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(
                ErrorKeys.EXCEL_TEMPLATE_GENERATION_FAILED,
                Map.of("reason", failureReason(e))
            );
        }
    }

    private BusinessException invalidTemplate(TemplateDefinition definition, String reason) {
        return new BusinessException(
            ErrorKeys.INVALID_EXCEL_DATA,
            Map.of(
                "template", definition.fileName(),
                "expectedSheet", definition.sheetName(),
                "reason", reason
            )
        );
    }

    private BusinessException templateGenerationFailure(TemplateDefinition definition, Exception e) {
        return new BusinessException(
            ErrorKeys.EXCEL_TEMPLATE_GENERATION_FAILED,
            Map.of("template", definition.fileName(), "reason", failureReason(e))
        );
    }

    private String failureReason(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : message;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private enum TemplateDefinition {
        SALES(
            SALES_FILE,
            SALES_SHEET,
            SALES_HEADERS,
            new int[] {5_000, 4_000, 4_000, 6_000, 7_000, 8_000}
        ),
        PURCHASE(
            PURCHASE_FILE,
            PURCHASE_SHEET,
            PURCHASE_HEADERS,
            new int[] {5_000, 5_000, 4_000, 5_000, 5_000, 6_000, 8_000}
        );

        private final String fileName;
        private final String sheetName;
        private final List<String> headers;
        private final int[] columnWidths;

        TemplateDefinition(String fileName, String sheetName, List<String> headers, int[] columnWidths) {
            this.fileName = fileName;
            this.sheetName = sheetName;
            this.headers = headers;
            this.columnWidths = columnWidths;
        }

        String fileName() {
            return fileName;
        }

        String sheetName() {
            return sheetName;
        }

        List<String> headers() {
            return headers;
        }

        int[] columnWidths() {
            return columnWidths;
        }
    }
}
