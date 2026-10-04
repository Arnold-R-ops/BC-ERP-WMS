package com.wms.system.service;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.apache.poi.ss.usermodel.*;
import java.util.*;

/** A named first column selects one identifier scheme; numeric barcodes are never mistaken for IDs. */
final class OrderImportSupport {
    private OrderImportSupport() {}
    static boolean usesCode(Row row) {
        String header=new DataFormatter().formatCellValue(row.getSheet().getRow(0).getCell(0)).trim();
        return header.equals("skuCode") || header.equals("barcode");
    }
    static Long resolve(Row row,ProductSkuRepository repository) {
        String header=new DataFormatter().formatCellValue(row.getSheet().getRow(0).getCell(0)).trim();
        Cell cell=row.getCell(0);
        if(cell!=null && cell.getCellType()==CellType.NUMERIC && Math.abs(cell.getNumericCellValue())>=1e15)
            throw new IllegalArgumentException(header+": identifiers longer than 15 digits must be stored as text");
        String value=new DataFormatter().formatCellValue(cell).trim();
        if(value.isBlank())throw new IllegalArgumentException(header+": required");
        var sku=(header.equals("skuCode")?repository.findBySkuCode(value):repository.findByBarcode(value))
            .orElseThrow(()->new IllegalArgumentException(header+": no matching product"));
        if(!Boolean.TRUE.equals(sku.getEnabled()) || (sku.getProduct()!=null&&!Boolean.TRUE.equals(sku.getProduct().getEnabled())))
            throw new IllegalArgumentException(header+": product is disabled");
        return sku.getId();
    }
    static void limit(Sheet sheet) {if(sheet.getLastRowNum()>5000)throw new BusinessException(ErrorKeys.INVALID_EXCEL_DATA,Map.of("reason","Maximum 5000 data rows per order"));}
    static Map<String,Object> error(int row,Exception error) {return Map.of("rowIndex",row+1,"error",Objects.toString(error.getMessage(),"Invalid row"));}
    static void requireValid(List<Map<String,Object>> errors,int valid,int skipped) {
        if(errors.isEmpty())return;
        throw new BusinessException(ErrorKeys.INVALID_EXCEL_DATA,Map.of("errors",errors,"validRows",valid,"failedRows",errors.size(),"skippedRows",skipped,
            "rowIndex",errors.get(0).get("rowIndex"),"error",errors.get(0).get("error")));
    }
}
