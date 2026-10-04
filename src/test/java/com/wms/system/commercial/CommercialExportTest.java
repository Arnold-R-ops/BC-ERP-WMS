package com.wms.system.commercial;
import com.wms.system.commercial.CommercialDtos.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import java.io.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CommercialExportTest {
    @Test void exportsSameChineseQuoteWithMultiPageLayoutAndBlankFeedbackCells() throws Exception {
        List<LineView> lines=new ArrayList<>();
        for(int i=1;i<=48;i++) lines.add(new LineView((long)i,"TEA-"+String.format("%03d",i),
            i==1?"精选茉莉花茶 100g，独立包装。客户确认价格后请注明所需数量与送货地址。":"精选茶叶 / Jasmine tea 100g 第 "+i+" 款", "袋",2,new BigDecimal("12.50"),new BigDecimal("25.00")));
        lines.add(new LineView(49L,"=UNTRUSTED","=HYPERLINK(\"https://example.com\")","袋",1,new BigDecimal("0.00"),new BigDecimal("0.00")));
        var profile=new ProfileView("安诺商贸有限公司 / Arnold Trading","北京市朝阳区测试路 88 号，三层 301 室。London NW1 6XE","+44 20 1234 5678","sales@example.com","");
        var d=new DocumentView(123L,0L,"QT-000123","QUOTE","OPEN",1L,"东方茶叶贸易有限公司","上海市浦东新区测试大道 123 号","上海市闵行区仓储路 456 号（收货仓）",profile,
            LocalDate.of(2026,9,19),LocalDate.of(2026,10,19),"GBP","请于报价有效期内确认。\nPlease confirm quantities and delivery details before ordering.",new BigDecimal("1200.00"),BigDecimal.ZERO,BigDecimal.ZERO,null,null,false,null,lines,List.of());
        var exporter=new CommercialExportService();byte[] pdf=exporter.pdf(d),xlsx=exporter.xlsx(d);
        try(var document=Loader.loadPDF(pdf)) {
            String text=new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(text).contains("安诺商贸有限公司","东方茶叶贸易有限公司","1200.00","TEA-048","GBP");
            for(int i=0;i<document.getNumberOfPages();i++) { var stripper=new PDFTextStripper();stripper.setStartPage(i+1);stripper.setEndPage(i+1);assertThat(stripper.getText(document)).contains("QT-000123"); }
        }
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet=book.getSheetAt(0);assertThat(book.getNumberOfSheets()).isEqualTo(1);
            assertThat(sheet.getRow(5).getCell(6).getStringCellValue()).contains("客户意向单价");
            assertThat(sheet.getRow(6).getCell(3).getStringCellValue()).isEqualTo("袋");
            assertThat(sheet.getRow(6).getCell(4).getNumericCellValue()).isEqualTo(12.5);
            assertThat(sheet.getRow(6).getCell(6).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(sheet.getRow(54).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(55).getCell(5).getNumericCellValue()).isEqualTo(1200);
        }
        if(Boolean.getBoolean("upgrade.artifacts")) {
            Path folder=Path.of("output","commercial");Files.createDirectories(folder);
            Files.write(folder.resolve("quotation-sample.pdf"),pdf);Files.write(folder.resolve("quotation-feedback-sample.xlsx"),xlsx);
        }
    }
}
