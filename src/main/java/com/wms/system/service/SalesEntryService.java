package com.wms.system.service;

import com.wms.system.dto.sales.BatchOptionDto;
import com.wms.system.dto.sales.CreateSalesOrderRequest.SalesOrderItemData;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.util.PackageStatusFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sales Entry Service
 *
 * V3.7 鏋舵瀯锛氶攢鍞鍗曞綍鍏ユ湇鍔? *
 * 鏍稿績鍔熻兘锛? * 1. Excel 妯℃澘涓嬭浇
 * 2. Excel 瀵煎叆瑙ｆ瀽
 * 3. 搴撳瓨棰勬鏌ワ紙鎵规閫夐」鏌ヨ锛? *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesEntryService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductSkuRepository productSkuRepository;

    // ========== Excel Template Download ==========

    /**
     * 涓嬭浇 Excel 瀵煎叆妯℃澘
     *
     * 鍔熻兘锛?     * - 鐢熸垚鏍囧噯 Excel 妯℃澘鏂囦欢
     * - 鍖呭惈琛ㄥご鍜岀ず渚嬫暟鎹?     * - 杩斿洖瀛楄妭鏁扮粍渚涘墠绔笅杞?     *
     * @return Excel 鏂囦欢瀛楄妭鏁扮粍
     */
    public byte[] downloadExcelTemplate() {
        log.info("Generating Excel template for sales order import");

        try {
            Workbook workbook = createExcelTemplate();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            byte[] bytes = outputStream.toByteArray();
            log.info("Excel template generated successfully, size: {} bytes", bytes.length);
            return bytes;

        } catch (IOException e) {
            log.error("Failed to generate Excel template", e);
            throw new BusinessException(
                ErrorKeys.EXCEL_TEMPLATE_GENERATION_FAILED,
                Map.of("reason", e.getMessage())
            );
        }
    }

    /**
     * 鍒涘缓 Excel 妯℃澘宸ヤ綔绨?     *
     * @return Excel 宸ヤ綔绨?     */
    private Workbook createExcelTemplate() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sales Order Import Template");

        // 鍒涘缓琛ㄥご鏍峰紡
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // 鍒涘缓琛ㄥご琛?(row 0)
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "customerId (瀹㈡埛ID)",
            "productSkuId (浜у搧ID)",
            "quantity (鏁伴噺)",
            "unitPrice (鍗曚环)",
            "rejectNearExpiry (鎷掓敹涓存湡鍝? true/false)",
            "specifiedBatchIds (鎸囧畾鎵规ID锛岄€楀彿鍒嗛殧)",
            "remark (澶囨敞)"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 鍒涘缓绀轰緥鏁版嵁琛?(row 1)
        Row exampleRow = sheet.createRow(1);
        exampleRow.createCell(0).setCellValue(1);           // customerId
        exampleRow.createCell(1).setCellValue(100);         // productSkuId
        exampleRow.createCell(2).setCellValue(50);          // quantity
        exampleRow.createCell(3).setCellValue(99.99);       // unitPrice
        exampleRow.createCell(4).setCellValue("false");     // rejectNearExpiry
        exampleRow.createCell(5).setCellValue("123,456");   // specifiedBatchIds
        exampleRow.createCell(6).setCellValue("娴嬭瘯璁㈠崟");   // remark

        // 璁剧疆鍒楀
        sheet.setColumnWidth(0, 5000);  // customerId
        sheet.setColumnWidth(1, 5000);  // productSkuId
        sheet.setColumnWidth(2, 4000);  // quantity
        sheet.setColumnWidth(3, 4000);  // unitPrice
        sheet.setColumnWidth(4, 8000);  // rejectNearExpiry
        sheet.setColumnWidth(5, 8000);  // specifiedBatchIds
        sheet.setColumnWidth(6, 6000);  // remark

        return workbook;
    }

    // ========== Excel Import ==========

    /**
     * 浠?Excel 瀵煎叆閿€鍞鍗曟暟鎹?     *
     * 鍔熻兘锛?     * - 瑙ｆ瀽 Excel 鏂囦欢
     * - 楠岃瘉鏁版嵁鏍煎紡
     * - 杩斿洖璁㈠崟鏄庣粏鍒楄〃
     *
     * @param file Excel 鏂囦欢
     * @return 璁㈠崟鏄庣粏鍒楄〃
     */
    @Transactional(readOnly = true)
    public List<SalesOrderItemData> importSalesOrderFromExcel(MultipartFile file) {
        log.info("Importing sales order from Excel file: {}", file.getOriginalFilename());

        // 楠岃瘉鏂囦欢
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                ErrorKeys.INVALID_FILE_FORMAT,
                Map.of("filename", "null", "expectedFormat", ".xlsx")
            );
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(
                ErrorKeys.INVALID_FILE_FORMAT,
                Map.of(
                    "filename", filename != null ? filename : "unknown",
                    "expectedFormat", ".xlsx",
                    "actualFormat", filename != null ? filename.substring(filename.lastIndexOf('.')) : "unknown"
                )
            );
        }

        try {
            Workbook workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheet("Sales Order Import Template");

            // 濡傛灉鎵句笉鍒版寚瀹?sheet锛屼娇鐢ㄧ涓€涓?sheet
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            List<SalesOrderItemData> items = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();

            // 璺宠繃琛ㄥご琛?(row 0)锛屼粠 row 1 寮€濮嬭鍙?
            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                try {
                    SalesOrderItemData item = parseExcelRow(row, rowIndex);
                    items.add(item);
                } catch (Exception e) {
                    log.error("Failed to parse row {}: {}", rowIndex + 1, e.getMessage());
                    throw new BusinessException(
                        ErrorKeys.INVALID_EXCEL_DATA,
                        Map.of(
                            "rowIndex", rowIndex + 1,  // 1-indexed for user-facing
                            "error", e.getMessage()
                        )
                    );
                }
            }

            workbook.close();
            log.info("Successfully imported {} items from Excel", items.size());
            return items;

        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", e.getMessage());
            throw new BusinessException(
                ErrorKeys.FILE_READ_ERROR,
                Map.of("filename", filename, "error", e.getMessage())
            );
        }
    }

    /**
     * 瑙ｆ瀽鍗曡 Excel 鏁版嵁
     *
     * @param row Excel 琛?     * @param rowIndex 琛岀储寮曪紙0-indexed锛?     * @return 璁㈠崟鏄庣粏鏁版嵁
     */
    private SalesOrderItemData parseExcelRow(Row row, int rowIndex) {
        // 瑙ｆ瀽鍚勫垪鏁版嵁
        Long customerId = readLongCell(row, 0, "customerId");
        Long productSkuId = readLongCell(row, 1, "productSkuId");
        Integer quantity = readIntegerCell(row, 2, "quantity");
        BigDecimal unitPrice = readBigDecimalCell(row, 3, "unitPrice");
        Boolean rejectNearExpiry = readBooleanCell(row, 4, "rejectNearExpiry");
        String specifiedBatchIdsStr = readStringCell(row, 5, "specifiedBatchIds");
        String remark = readStringCell(row, 6, "remark");

        // 楠岃瘉蹇呭～瀛楁
        if (customerId == null) {
            throw new IllegalArgumentException("瀹㈡埛ID涓嶈兘涓虹┖");
        }
        if (productSkuId == null) {
            throw new IllegalArgumentException("浜у搧ID涓嶈兘涓虹┖");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("鏁伴噺蹇呴』澶т簬 0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }

        // 瑙ｆ瀽鎸囧畾鎵规ID鍒楄〃
        List<Long> specifiedBatchIds = parseCommaSeparatedIds(specifiedBatchIdsStr);

        // 鏋勫缓璁㈠崟鏄庣粏瀵硅薄
        return SalesOrderItemData.builder()
            .productSkuId(productSkuId)
            .quantity(quantity)
            .unitPrice(unitPrice)
            .rejectNearExpiry(rejectNearExpiry != null ? rejectNearExpiry : false)
            .specifiedBatchIds(specifiedBatchIds)
            .remark(remark)
            .build();
    }

    /**
     * 妫€鏌ヨ鏄惁涓虹┖
     *
     * @param row Excel 琛?     * @return true 濡傛灉琛屼负绌?     */
    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < 7; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    // ========== Batch Options Pre-Check ==========

    /**
     * 鑾峰彇鎵规閫夐」锛堝簱瀛橀妫€鏌ワ級
     *
     * 鍔熻兘锛?     * - 鏌ヨ鍙敤鎵规
     * - 杩囨护涓存湡鎵规锛堝彲閫夛級
     * - 璁＄畻鏂伴矞搴﹀拰鍖呰鐘舵€?     * - 鎸?FEFO 鎺掑簭
     *
     * @param productSkuId 浜у搧ID
     * @param quantity 闇€姹傛暟閲?     * @param rejectNearExpiry 鏄惁鎷掓敹涓存湡鍝?     * @return 鎵规閫夐」鍒楄〃
     */
    @Transactional(readOnly = true)
    public List<BatchOptionDto> getBatchOptions(Long productSkuId, Integer quantity, Boolean rejectNearExpiry) {
        log.info("Getting batch options for productSkuId={}, quantity={}, rejectNearExpiry={}",
            productSkuId, quantity, rejectNearExpiry);

        // 鏌ヨ浜у搧淇℃伅
        ProductSku product = productSkuRepository.findById(productSkuId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                Map.of("productSkuId", productSkuId)
            ));

        // 鏌ヨ鍙敤鎵规锛團EFO 鎺掑簭锛?
        List<InventoryBatch> batches = inventoryBatchRepository
            .findByProductSkuIdAndActiveOrderByExpiryDateAsc(productSkuId, true);

        LocalDate today = LocalDate.now();

        // 杩囨护鍜岃浆鎹㈡壒娆?
        List<BatchOptionDto> options = batches.stream()
            .filter(batch -> {
                // 杩囨护涓存湡鎵规
                if (Boolean.TRUE.equals(rejectNearExpiry)) {
                    long daysUntilExpiry = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
                    return daysUntilExpiry > product.getNearExpiryDays();
                }
                return true;
            })
            .map(batch -> {
                // 璁＄畻璺濈杩囨湡澶╂暟
                long daysUntilExpiry = ChronoUnit.DAYS.between(today, batch.getExpiryDate());

                // 鍒ゆ柇鏂伴矞搴︾姸鎬?
                String freshnessStatus = daysUntilExpiry > product.getNearExpiryDays()
                    ? "FRESH"
                    : "WARNING";

                // 鍒ゆ柇鍖呰鐘舵€?
                String packageStatus = PackageStatusFormatter.withIcon(
                    batch.getQuantity(),
                    product.getPerPackQty()
                );

                return BatchOptionDto.builder()
                    .batchId(batch.getId())
                    .batchCode(batch.getBatchCode())
                    .locationCode(batch.getLocationCode())
                    .quantity(batch.getQuantity())
                    .expiryDate(batch.getExpiryDate())
                    .daysUntilExpiry((int) daysUntilExpiry)
                    .freshnessStatus(freshnessStatus)
                    .packageStatus(packageStatus)
                    .unitPrice(product.getUnitPrice())
                    .build();
            })
            .collect(Collectors.toList());

        log.info("Found {} batch options for productSkuId={}", options.size(), productSkuId);
        return options;
    }

    // ========== Helper Methods ==========

    /**
     * 鑾峰彇鍗曞厓鏍煎€硷紙瀛楃涓诧級
     *
     * @param cell Excel 鍗曞厓鏍?     * @return 瀛楃涓插€?     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                // 閬垮厤绉戝璁℃暟娉?
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("Unsupported cell type for String");
        }
    }

    /**
     * 鑾峰彇鍗曞厓鏍煎€硷紙Long锛?     *
     * @param cell Excel 鍗曞厓鏍?     * @return Long 鍊?     */
    private Long getCellValueAsLong(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case NUMERIC:
                return (long) cell.getNumericCellValue();
            case STRING:
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) {
                    return null;
                }
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("鏃犳晥鐨勬暟瀛楁牸寮? " + value);
                }
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("鏃犳硶杞崲涓?Long 绫诲瀷");
        }
    }

    /**
     * 鑾峰彇鍗曞厓鏍煎€硷紙Integer锛?     *
     * @param cell Excel 鍗曞厓鏍?     * @return Integer 鍊?     */
    private Integer getCellValueAsInteger(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case NUMERIC:
                return (int) cell.getNumericCellValue();
            case STRING:
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) {
                    return null;
                }
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("鏃犳晥鐨勬暟瀛楁牸寮? " + value);
                }
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("鏃犳硶杞崲涓?Integer 绫诲瀷");
        }
    }

    /**
     * 鑾峰彇鍗曞厓鏍煎€硷紙BigDecimal锛?     *
     * @param cell Excel 鍗曞厓鏍?     * @return BigDecimal 鍊?     */
    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) {
                    return null;
                }
                try {
                    return new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("鏃犳晥鐨勬暟瀛楁牸寮? " + value);
                }
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("鏃犳硶杞崲涓?BigDecimal 绫诲瀷");
        }
    }

    /**
     * 鑾峰彇鍗曞厓鏍煎€硷紙Boolean锛?     *
     * @param cell Excel 鍗曞厓鏍?     * @return Boolean 鍊?     */
    private Boolean getCellValueAsBoolean(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case STRING:
                String value = cell.getStringCellValue().trim().toLowerCase();
                if (value.isEmpty()) {
                    return null;
                }
                if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
                    return true;
                }
                if ("false".equals(value) || "no".equals(value) || "0".equals(value)) {
                    return false;
                }
                throw new IllegalArgumentException("鏃犳晥鐨勫竷灏斿€? " + value);
            case NUMERIC:
                return cell.getNumericCellValue() != 0;
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("鏃犳硶杞崲涓?Boolean 绫诲瀷");
        }
    }

    /**
     * 瑙ｆ瀽閫楀彿鍒嗛殧鐨?ID 鍒楄〃
     *
     * @param ids 閫楀彿鍒嗛殧鐨?ID 瀛楃涓诧紙濡?"123,456,789"锛?     * @return ID 鍒楄〃
     */
    private List<Long> parseCommaSeparatedIds(String ids) {
        if (ids == null || ids.trim().isEmpty()) {
            return null;
        }

        try {
            return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("鏃犳晥鐨勬壒娆D鏍煎紡: " + ids);
        }
    }

    private Long readLongCell(Row row, int columnIndex, String fieldName) {
        try {
            return getCellValueAsLong(row.getCell(columnIndex));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(buildColumnError(fieldName, "Long", columnIndex, e));
        }
    }

    private Integer readIntegerCell(Row row, int columnIndex, String fieldName) {
        try {
            return getCellValueAsInteger(row.getCell(columnIndex));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(buildColumnError(fieldName, "Integer", columnIndex, e));
        }
    }

    private BigDecimal readBigDecimalCell(Row row, int columnIndex, String fieldName) {
        try {
            return getCellValueAsBigDecimal(row.getCell(columnIndex));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(buildColumnError(fieldName, "BigDecimal", columnIndex, e));
        }
    }

    private Boolean readBooleanCell(Row row, int columnIndex, String fieldName) {
        try {
            return getCellValueAsBoolean(row.getCell(columnIndex));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(buildColumnError(fieldName, "Boolean", columnIndex, e));
        }
    }

    private String readStringCell(Row row, int columnIndex, String fieldName) {
        try {
            return getCellValueAsString(row.getCell(columnIndex));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(buildColumnError(fieldName, "String", columnIndex, e));
        }
    }

    private String buildColumnError(String fieldName, String targetType, int columnIndex, Exception cause) {
        return String.format(
            "Invalid %s in column %s (%s): %s",
            fieldName,
            formatColumnLabel(columnIndex),
            targetType,
            cause.getMessage()
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


