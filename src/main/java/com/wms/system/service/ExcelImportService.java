package com.wms.system.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Excel Import Service (Excel 导入服务)
 *
 * Core Responsibilities:
 * 1. Parse Excel files (XLSX format) for purchase order creation
 * 2. Extract product data from rows
 * 3. Convert to PurchaseOrderService.PurchaseOrderItemData
 * 4. Handle parsing errors gracefully
 *
 * Excel Format (Expected):
 * | productId | quantity | unitCost | expiryDate | productionDate | externalBatchCode | remark |
 * |-----------|----------|----------|------------|----------------|-------------------|--------|
 * | 1         | 100      | 10.50    | 2025-12-31 | 2025-01-01     | BATCH001          | Note   |
 * | 2         | 200      | 5.00     | 2026-06-30 |                |                   |        |
 *
 * Column Mapping:
 * - Column A (0): productId (Long, required)
 * - Column B (1): orderedQuantity (Integer, required)
 * - Column C (2): unitCost (BigDecimal, optional)
 * - Column D (3): expiryDate (LocalDate, optional at Stage 1)
 * - Column E (4): productionDate (LocalDate, optional)
 * - Column F (5): externalBatchCode (String, optional)
 * - Column G (6): remark (String, optional)
 *
 * Technical Features:
 * - Apache POI 5.2.3 (OOXML format)
 * - Supports both numeric and date cell types
 * - Graceful error handling with row numbers
 * - Skips header row automatically
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Excel Import)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final DataFormatter CELL_FORMATTER = new DataFormatter();

    /**
     * ⭐ Import purchase order from Excel file
     *
     * Business Flow:
     * 1. Read Excel file (XLSX format)
     * 2. Iterate through rows (skip header row 0)
     * 3. Extract data from each column
     * 4. Convert to PurchaseOrderItemData
     * 5. Validate required fields (productId, orderedQuantity)
     * 6. Return list of items
     *
     * Error Handling:
     * - Invalid file format: Throw INVALID_FILE_FORMAT
     * - Missing required fields: Throw INVALID_EXCEL_DATA with row number
     * - Cell type mismatch: Attempt conversion or throw error
     *
     * @param file Excel file (XLSX format)
     * @return List<PurchaseOrderService.PurchaseOrderItemData> Parsed items
     * @throws BusinessException if file format invalid or parsing fails
     */
    public List<PurchaseOrderService.PurchaseOrderItemData> importPurchaseOrderFromExcel(
        MultipartFile file
    ) {
        log.info("Starting Excel import: filename={}, size={} bytes",
            file.getOriginalFilename(), file.getSize());

        // Validate file extension
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            log.error("Invalid file format: filename={}", filename);

            throw new BusinessException(
                ErrorKeys.INVALID_FILE_FORMAT,
                Map.of(
                    "filename", filename != null ? filename : "unknown",
                    "expectedFormat", ".xlsx"
                )
            );
        }

        List<PurchaseOrderService.PurchaseOrderItemData> items = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            // Read first sheet
            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getPhysicalNumberOfRows();
            int lastRowNum = sheet.getLastRowNum();

            log.info("Excel file opened: sheetName={}, totalRows={}",
                sheet.getSheetName(), totalRows);

            // Iterate through logical row range (skip header row 0).
            // Using lastRowNum avoids missing valid rows after sparse/empty rows.
            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                if (row == null || isRowEmpty(row)) {
                    log.debug("Skipping empty row: rowIndex={}", rowIndex);
                    continue;
                }

                try {
                    // Parse row data
                    PurchaseOrderService.PurchaseOrderItemData item = parseRow(row, rowIndex);
                    items.add(item);

                    log.debug("Row parsed successfully: rowIndex={}, productId={}, quantity={}",
                        rowIndex, item.getProductId(), item.getOrderedQuantity());

                } catch (Exception e) {
                    log.error("Failed to parse row: rowIndex={}, error={}",
                        rowIndex, e.getMessage(), e);

                    throw new BusinessException(
                        ErrorKeys.INVALID_EXCEL_DATA,
                        Map.of(
                            "rowIndex", rowIndex + 1,  // User-facing row number (1-indexed)
                            "error", e.getMessage()
                        )
                    );
                }
            }

            log.info("✅ Excel import completed: filename={}, itemCount={}",
                filename, items.size());

            return items;

        } catch (IOException e) {
            log.error("Failed to read Excel file: filename={}, error={}",
                filename, e.getMessage(), e);

            throw new BusinessException(
                ErrorKeys.FILE_READ_ERROR,
                Map.of(
                    "filename", filename,
                    "error", e.getMessage()
                )
            );
        }
    }

    /**
     * Parse single row to PurchaseOrderItemData
     *
     * Column Mapping:
     * - Column A (0): productId (Long, required)
     * - Column B (1): orderedQuantity (Integer, required)
     * - Column C (2): unitCost (BigDecimal, optional)
     * - Column D (3): expiryDate (LocalDate, optional)
     * - Column E (4): productionDate (LocalDate, optional)
     * - Column F (5): externalBatchCode (String, optional)
     * - Column G (6): remark (String, optional)
     *
     * @param row Excel row
     * @param rowIndex Row index (for error reporting)
     * @return PurchaseOrderService.PurchaseOrderItemData Parsed item
     * @throws IllegalArgumentException if required fields missing
     */
    private PurchaseOrderService.PurchaseOrderItemData parseRow(Row row, int rowIndex) {
        // Column A: productId (required)
        Long productId = getCellLong(row, 0);
        if (productId == null) {
            throw new IllegalArgumentException("Missing productId in column A");
        }

        // Column B: orderedQuantity (required)
        Integer orderedQuantity = getCellInt(row, 1);
        if (orderedQuantity == null || orderedQuantity <= 0) {
            throw new IllegalArgumentException("Invalid orderedQuantity in column B (must be > 0)");
        }

        // Column C: unitCost (optional)
        BigDecimal unitCost = getCellBigDecimal(row, 2);

        // Column D: expiryDate (optional at Stage 1)
        LocalDate expiryDate = getCellLocalDate(row, 3);

        // Column E: productionDate (optional)
        LocalDate productionDate = getCellLocalDate(row, 4);

        // Column F: externalBatchCode (optional)
        String externalBatchCode = getCellString(row, 5);

        // Column G: remark (optional)
        String remark = getCellString(row, 6);

        return PurchaseOrderService.PurchaseOrderItemData.builder()
            .productId(productId)
            .orderedQuantity(orderedQuantity)
            .unitCost(unitCost)
            .expiryDate(expiryDate)
            .productionDate(productionDate)
            .externalBatchCode(externalBatchCode)
            .remark(remark)
            .build();
    }

    /**
     * Check if row is empty (all cells null or blank)
     *
     * @param row Excel row
     * @return true if row is empty
     */
    private boolean isRowEmpty(Row row) {
        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
            Cell cell = row.getCell(cellIndex);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get Long value from cell
     *
     * @param row Excel row
     * @param columnIndex Column index
     * @return Long Cell value (null if empty)
     */
    private Long getCellLong(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        try {
            CellType cellType = getEffectiveCellType(cell);
            if (cellType == CellType.NUMERIC) {
                return (long) cell.getNumericCellValue();
            } else if (cellType == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                return value.isEmpty() ? null : Long.parseLong(value);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(buildParseError("Long", columnIndex, cell));
        }

        throw new IllegalArgumentException(buildParseError("Long", columnIndex, cell));
    }

    /**
     * Get Integer value from cell
     *
     * @param row Excel row
     * @param columnIndex Column index
     * @return Integer Cell value (null if empty)
     */
    private Integer getCellInt(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        try {
            CellType cellType = getEffectiveCellType(cell);
            if (cellType == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else if (cellType == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                return value.isEmpty() ? null : Integer.parseInt(value);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(buildParseError("Integer", columnIndex, cell));
        }

        throw new IllegalArgumentException(buildParseError("Integer", columnIndex, cell));
    }

    /**
     * Get BigDecimal value from cell
     *
     * @param row Excel row
     * @param columnIndex Column index
     * @return BigDecimal Cell value (null if empty)
     */
    private BigDecimal getCellBigDecimal(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        try {
            CellType cellType = getEffectiveCellType(cell);
            if (cellType == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            } else if (cellType == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                return value.isEmpty() ? null : new BigDecimal(value);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(buildParseError("BigDecimal", columnIndex, cell));
        }

        throw new IllegalArgumentException(buildParseError("BigDecimal", columnIndex, cell));
    }

    /**
     * Get LocalDate value from cell
     *
     * Supports:
     * - DATE cell type (Excel date)
     * - STRING cell type (format: yyyy-MM-dd)
     *
     * @param row Excel row
     * @param columnIndex Column index
     * @return LocalDate Cell value (null if empty)
     */
    private LocalDate getCellLocalDate(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        try {
            CellType cellType = getEffectiveCellType(cell);
            if (cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                // Excel date cell
                Date date = cell.getDateCellValue();
                return date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            } else if (cellType == CellType.STRING) {
                // String cell (format: yyyy-MM-dd)
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) {
                    return null;
                }
                return LocalDate.parse(value);  // ISO-8601 format
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(buildParseError("LocalDate", columnIndex, cell));
        }

        throw new IllegalArgumentException(buildParseError("LocalDate", columnIndex, cell));
    }

    /**
     * Get String value from cell
     *
     * @param row Excel row
     * @param columnIndex Column index
     * @return String Cell value (null if empty)
     */
    private String getCellString(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        try {
            CellType cellType = getEffectiveCellType(cell);
            if (cellType == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                return value.isEmpty() ? null : value;
            } else if (cellType == CellType.NUMERIC) {
                // Convert numeric to string
                return String.valueOf((long) cell.getNumericCellValue());
            } else if (cellType == CellType.BOOLEAN) {
                return String.valueOf(cell.getBooleanCellValue());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(buildParseError("String", columnIndex, cell));
        }

        throw new IllegalArgumentException(buildParseError("String", columnIndex, cell));
    }

    private CellType getEffectiveCellType(Cell cell) {
        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            return cell.getCachedFormulaResultType();
        }
        return cellType;
    }

    private String buildParseError(String targetType, int columnIndex, Cell cell) {
        return String.format(
            "Invalid %s in column %s: %s",
            targetType,
            formatColumnLabel(columnIndex),
            CELL_FORMATTER.formatCellValue(cell)
        );
    }

    private String formatColumnLabel(int columnIndex) {
        int index = columnIndex;
        StringBuilder label = new StringBuilder();
        while (index >= 0) {
            int remainder = index % 26;
            label.insert(0, (char) ('A' + remainder));
            index = (index / 26) - 1;
        }
        return label.toString();
    }
}
