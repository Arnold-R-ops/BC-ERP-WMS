package com.wms.system.service;

import com.wms.system.dto.sales.BatchOptionDto;
import com.wms.system.dto.sales.CreateSalesOrderRequest.SalesOrderItemData;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Product;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.ProductRepository;
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
 * V3.7 架构：销售订单录入服务
 *
 * 核心功能：
 * 1. Excel 模板下载
 * 2. Excel 导入解析
 * 3. 库存预检查（批次选项查询）
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesEntryService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductRepository productRepository;

    // ========== Excel Template Download ==========

    /**
     * 下载 Excel 导入模板
     *
     * 功能：
     * - 生成标准 Excel 模板文件
     * - 包含表头和示例数据
     * - 返回字节数组供前端下载
     *
     * @return Excel 文件字节数组
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
     * 创建 Excel 模板工作簿
     *
     * @return Excel 工作簿
     */
    private Workbook createExcelTemplate() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("销售订单导入模板");

        // 创建表头样式
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

        // 创建表头行 (row 0)
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "customerId (客户ID)",
            "productId (产品ID)",
            "quantity (数量)",
            "unitPrice (单价)",
            "rejectNearExpiry (拒收临期品: true/false)",
            "specifiedBatchIds (指定批次ID，逗号分隔)",
            "remark (备注)"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 创建示例数据行 (row 1)
        Row exampleRow = sheet.createRow(1);
        exampleRow.createCell(0).setCellValue(1);           // customerId
        exampleRow.createCell(1).setCellValue(100);         // productId
        exampleRow.createCell(2).setCellValue(50);          // quantity
        exampleRow.createCell(3).setCellValue(99.99);       // unitPrice
        exampleRow.createCell(4).setCellValue("false");     // rejectNearExpiry
        exampleRow.createCell(5).setCellValue("123,456");   // specifiedBatchIds
        exampleRow.createCell(6).setCellValue("测试订单");   // remark

        // 设置列宽
        sheet.setColumnWidth(0, 5000);  // customerId
        sheet.setColumnWidth(1, 5000);  // productId
        sheet.setColumnWidth(2, 4000);  // quantity
        sheet.setColumnWidth(3, 4000);  // unitPrice
        sheet.setColumnWidth(4, 8000);  // rejectNearExpiry
        sheet.setColumnWidth(5, 8000);  // specifiedBatchIds
        sheet.setColumnWidth(6, 6000);  // remark

        return workbook;
    }

    // ========== Excel Import ==========

    /**
     * 从 Excel 导入销售订单数据
     *
     * 功能：
     * - 解析 Excel 文件
     * - 验证数据格式
     * - 返回订单明细列表
     *
     * @param file Excel 文件
     * @return 订单明细列表
     */
    @Transactional(readOnly = true)
    public List<SalesOrderItemData> importSalesOrderFromExcel(MultipartFile file) {
        log.info("Importing sales order from Excel file: {}", file.getOriginalFilename());

        // 验证文件
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
            Sheet sheet = workbook.getSheet("销售订单导入模板");

            // 如果找不到指定 sheet，使用第一个 sheet
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            List<SalesOrderItemData> items = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();

            // 跳过表头行 (row 0)，从 row 1 开始读取
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
     * 解析单行 Excel 数据
     *
     * @param row Excel 行
     * @param rowIndex 行索引（0-indexed）
     * @return 订单明细数据
     */
    private SalesOrderItemData parseExcelRow(Row row, int rowIndex) {
        // 解析各列数据
        Long customerId = readLongCell(row, 0, "customerId");
        Long productId = readLongCell(row, 1, "productId");
        Integer quantity = readIntegerCell(row, 2, "quantity");
        BigDecimal unitPrice = readBigDecimalCell(row, 3, "unitPrice");
        Boolean rejectNearExpiry = readBooleanCell(row, 4, "rejectNearExpiry");
        String specifiedBatchIdsStr = readStringCell(row, 5, "specifiedBatchIds");
        String remark = readStringCell(row, 6, "remark");

        // 验证必填字段
        if (customerId == null) {
            throw new IllegalArgumentException("客户ID不能为空");
        }
        if (productId == null) {
            throw new IllegalArgumentException("产品ID不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于 0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("单价不能为负数");
        }

        // 解析指定批次ID列表
        List<Long> specifiedBatchIds = parseCommaSeparatedIds(specifiedBatchIdsStr);

        // 构建订单明细对象
        return SalesOrderItemData.builder()
            .productId(productId)
            .quantity(quantity)
            .unitPrice(unitPrice)
            .rejectNearExpiry(rejectNearExpiry != null ? rejectNearExpiry : false)
            .specifiedBatchIds(specifiedBatchIds)
            .remark(remark)
            .build();
    }

    /**
     * 检查行是否为空
     *
     * @param row Excel 行
     * @return true 如果行为空
     */
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
     * 获取批次选项（库存预检查）
     *
     * 功能：
     * - 查询可用批次
     * - 过滤临期批次（可选）
     * - 计算新鲜度和包装状态
     * - 按 FEFO 排序
     *
     * @param productId 产品ID
     * @param quantity 需求数量
     * @param rejectNearExpiry 是否拒收临期品
     * @return 批次选项列表
     */
    @Transactional(readOnly = true)
    public List<BatchOptionDto> getBatchOptions(Long productId, Integer quantity, Boolean rejectNearExpiry) {
        log.info("Getting batch options for productId={}, quantity={}, rejectNearExpiry={}",
            productId, quantity, rejectNearExpiry);

        // 查询产品信息
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_NOT_FOUND,
                Map.of("productId", productId)
            ));

        // 查询可用批次（FEFO 排序）
        List<InventoryBatch> batches = inventoryBatchRepository
            .findByProductIdAndActiveOrderByExpiryDateAsc(productId, true);

        LocalDate today = LocalDate.now();

        // 过滤和转换批次
        List<BatchOptionDto> options = batches.stream()
            .filter(batch -> {
                // 过滤临期批次
                if (Boolean.TRUE.equals(rejectNearExpiry)) {
                    long daysUntilExpiry = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
                    return daysUntilExpiry > product.getNearExpiryDays();
                }
                return true;
            })
            .map(batch -> {
                // 计算距离过期天数
                long daysUntilExpiry = ChronoUnit.DAYS.between(today, batch.getExpiryDate());

                // 判断新鲜度状态
                String freshnessStatus = daysUntilExpiry > product.getNearExpiryDays()
                    ? "FRESH"
                    : "WARNING";

                // 判断包装状态
                String packageStatus = (batch.getQuantity() % product.getPerPackQty() == 0)
                    ? "📦 整箱"
                    : "📦 散货";

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

        log.info("Found {} batch options for productId={}", options.size(), productId);
        return options;
    }

    // ========== Helper Methods ==========

    /**
     * 获取单元格值（字符串）
     *
     * @param cell Excel 单元格
     * @return 字符串值
     */
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
                // 避免科学计数法
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
     * 获取单元格值（Long）
     *
     * @param cell Excel 单元格
     * @return Long 值
     */
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
                    throw new IllegalArgumentException("无效的数字格式: " + value);
                }
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("无法转换为 Long 类型");
        }
    }

    /**
     * 获取单元格值（Integer）
     *
     * @param cell Excel 单元格
     * @return Integer 值
     */
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
                    throw new IllegalArgumentException("无效的数字格式: " + value);
                }
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("无法转换为 Integer 类型");
        }
    }

    /**
     * 获取单元格值（BigDecimal）
     *
     * @param cell Excel 单元格
     * @return BigDecimal 值
     */
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
                    throw new IllegalArgumentException("无效的数字格式: " + value);
                }
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("无法转换为 BigDecimal 类型");
        }
    }

    /**
     * 获取单元格值（Boolean）
     *
     * @param cell Excel 单元格
     * @return Boolean 值
     */
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
                throw new IllegalArgumentException("无效的布尔值: " + value);
            case NUMERIC:
                return cell.getNumericCellValue() != 0;
            case BLANK:
                return null;
            default:
                throw new IllegalArgumentException("无法转换为 Boolean 类型");
        }
    }

    /**
     * 解析逗号分隔的 ID 列表
     *
     * @param ids 逗号分隔的 ID 字符串（如 "123,456,789"）
     * @return ID 列表
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
            throw new IllegalArgumentException("无效的批次ID格式: " + ids);
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
