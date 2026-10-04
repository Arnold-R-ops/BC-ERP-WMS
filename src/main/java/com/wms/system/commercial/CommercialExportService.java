package com.wms.system.commercial;

import com.wms.system.commercial.CommercialDtos.*;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import java.io.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@Service
public class CommercialExportService {
    public byte[] pdf(DocumentView d) {
        try (PDDocument pdf=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream();
             InputStream font=getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
            if(font==null) throw new IOException("Document font is missing");
            PDType0Font type=PDType0Font.load(pdf,font,true);
            PdfLayout layout=new PdfLayout(pdf,type);
            layout.newPage(false);
            layout.paragraph(d.company().name(),17,24);
            layout.paragraph(d.company().address(),9,14);
            layout.paragraph(join(d.company().phone(),d.company().email()),9,14);
            layout.gap(18);
            layout.paragraph((d.kind().equals("QUOTE")?"报价单 / QUOTATION":"商业账单 / INVOICE")+"   "+d.number(),17,26);
            layout.paragraph("日期 / Date: "+d.documentDate()+"    "+(d.kind().equals("QUOTE")?"有效期 / Valid until: ":"到期日 / Due date: ")+d.dueDate(),9,16);
            layout.paragraph("客户 / Customer: "+d.customerName(),11,18);
            layout.paragraph("账单地址 / Bill to: "+value(d.billingAddress()),9,14);
            if(d.shippingAddress()!=null && !d.shippingAddress().equals(d.billingAddress())) layout.paragraph("收货地址 / Ship to: "+d.shippingAddress(),9,14);
            if(d.salesOrderNo()!=null) layout.paragraph("销售订单 / Sales order: "+d.salesOrderNo(),9,14);
            if(d.status().equals("CANCELLED") || d.status().equals("VOID")) layout.paragraph("已作废 / VOID — "+value(d.closeReason()),12,20);
            layout.gap(16); layout.tableHeader();
            for(LineView l:d.lines()) layout.tableRow(new String[]{l.skuCode(),l.description(),l.quantity()+" "+l.unit(),l.unitPrice().toPlainString(),l.amount().toPlainString()});
            layout.gap(14);
            layout.paragraph("合计 / Total: GBP "+d.total().toPlainString(),14,23);
            if(d.kind().equals("INVOICE")) {
                layout.paragraph("已收 / Received: GBP "+d.received().toPlainString()+"    待收 / Outstanding: GBP "+d.outstanding().toPlainString(),10,18);
                if(d.orderAmountChanged()) layout.paragraph("原订单数量已变更，请核对本账单。 / Order quantities changed; review this invoice.",10,18);
            }
            layout.paragraph("价格口径：商品单价合计（GBP）；未另行计算税费或运费。",9,15);
            layout.paragraph("Basis: item prices in GBP; no additional tax or freight calculated.",9,15);
            if(d.terms()!=null && !d.terms().isBlank()) { layout.gap(15); layout.paragraph("条款 / Terms",11,19); layout.paragraph(d.terms(),9,15); }
            layout.finish();
            for(int i=0;i<pdf.getNumberOfPages();i++) {
                try(var cs=new PDPageContentStream(pdf,pdf.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) {
                    cs.beginText();cs.setFont(type,8);cs.setNonStrokingColor(Color.GRAY);cs.newLineAtOffset(42,27);
                    cs.showText(d.number()+"   |   "+(i+1)+" / "+pdf.getNumberOfPages());cs.endText();
                }
            }
            pdf.getDocumentInformation().setTitle(d.number()); pdf.save(out); return out.toByteArray();
        } catch(IOException ex) { throw new IllegalStateException("Could not generate document PDF",ex); }
    }

    public byte[] xlsx(DocumentView d) {
        try(XSSFWorkbook workbook=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Sheet sheet=workbook.createSheet("报价与反馈");sheet.setDisplayGridlines(false);
            org.apache.poi.ss.usermodel.Font normal=workbook.createFont(); normal.setFontName("Microsoft YaHei"); normal.setFontHeightInPoints((short)11);
            org.apache.poi.ss.usermodel.Font bold=workbook.createFont(); bold.setFontName("Microsoft YaHei");bold.setFontHeightInPoints((short)11);bold.setBold(true);bold.setColor(IndexedColors.WHITE.getIndex());
            CellStyle text=workbook.createCellStyle();text.setFont(normal);text.setWrapText(true);text.setVerticalAlignment(VerticalAlignment.TOP);
            CellStyle heading=workbook.createCellStyle();heading.cloneStyleFrom(text);heading.setFont(bold);heading.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money=workbook.createCellStyle();money.cloneStyleFrom(text);money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            CellStyle feedback=workbook.createCellStyle();feedback.cloneStyleFrom(text);feedback.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());feedback.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle feedbackMoney=workbook.createCellStyle();feedbackMoney.cloneStyleFrom(feedback);feedbackMoney.setDataFormat(money.getDataFormat());
            int r=0;
            r=merged(sheet,r,d.company().name()+"  |  "+d.number(),heading,32);
            r=merged(sheet,r,"客户: "+d.customerName()+"    币种: GBP    有效期: "+d.dueDate(),text,30);
            String addresses="账单地址: "+value(d.billingAddress())+"    收货地址: "+value(d.shippingAddress());
            r=merged(sheet,r,addresses,text,estimatedHeight(addresses,150));
            r=merged(sheet,r,"保留我方报价；请在黄色列填写客户意向单价与反馈备注。回传后由业务员确认，不自动修改订单。",text,32);
            r=merged(sheet,r,"价格口径：商品单价合计，单位见各行；未另行计算税费或运费。",text,24);
            String[] labels={"SKU","商品","数量","单位","我方单价 (GBP)","金额 (GBP)","客户意向单价 (GBP)","反馈备注"};
            Row head=sheet.createRow(r++);head.setHeightInPoints(38);
            for(int c=0;c<labels.length;c++) cell(head,c,labels[c],heading);
            int first=r;
            for(var l:d.lines()) {
                Row row=sheet.createRow(r++);row.setHeightInPoints(estimatedHeight(l.description(),28));
                cell(row,0,l.skuCode(),text);cell(row,1,l.description(),text);cell(row,2,l.quantity(),text);cell(row,3,l.unit(),text);
                cell(row,4,l.unitPrice().doubleValue(),money);cell(row,5,l.amount().doubleValue(),money);
                cell(row,6,null,feedbackMoney);cell(row,7,null,feedback);
            }
            Row total=sheet.createRow(r++);total.setHeightInPoints(28);cell(total,4,"合计 GBP",text);cell(total,5,d.total().doubleValue(),money);
            if(d.terms()!=null && !d.terms().isBlank()) {
                r=merged(sheet,r,"条款 / Terms",text,24);
                for(String paragraph:d.terms().split("\\R",-1)) {
                    for(int offset=0;offset<Math.max(1,paragraph.length());offset+=250) {
                        String chunk=paragraph.substring(Math.min(offset,paragraph.length()),Math.min(offset+250,paragraph.length()));
                        r=merged(sheet,r,chunk,text,estimatedHeight(chunk,150));
                    }
                }
            }
            int[] widths={18,34,10,10,18,18,23,35};for(int c=0;c<widths.length;c++) sheet.setColumnWidth(c,widths[c]*256);
            sheet.createFreezePane(2,first);sheet.setAutoFilter(new CellRangeAddress(first-1,first+d.lines().size()-1,0,7));
            sheet.setRepeatingRows(new CellRangeAddress(first-1,first-1,-1,-1));sheet.getPrintSetup().setLandscape(true);sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.setFitToPage(true);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);
            workbook.write(out);return out.toByteArray();
        } catch(IOException ex) { throw new IllegalStateException("Could not generate quotation workbook",ex); }
    }
    private static int merged(Sheet s,int r,String value,CellStyle style,float height) {Row row=s.createRow(r);row.setHeightInPoints(height);cell(row,0,value,style);s.addMergedRegion(new CellRangeAddress(r,r,0,7));return r+1;}
    private static float estimatedHeight(String value,int columnUnits) {
        int units=value.codePoints().map(cp -> cp>255 ? 2 : 1).sum();
        return Math.min(409,Math.max(38,((float)Math.ceil(units/(double)columnUnits)+1)*18));
    }
    private static void cell(Row r,int c,Object value,CellStyle style) {Cell cell=r.createCell(c);cell.setCellStyle(style);if(value instanceof Number n)cell.setCellValue(n.doubleValue());else if(value!=null)cell.setCellValue(value.toString());}
    private static String value(String s) {return s==null?"":s;}
    private static String join(String a,String b) {return value(a)+(a!=null&&!a.isBlank()?"   ":"")+value(b);}

    private static class PdfLayout {
        final PDDocument doc; final PDType0Font font; PDPageContentStream stream; float y;
        final float[] widths={78,197,78,73,85};
        PdfLayout(PDDocument d,PDType0Font f){doc=d;font=f;}
        void finish() throws IOException {if(stream!=null){stream.close();stream=null;}}
        void newPage(boolean table) throws IOException {finish();PDPage p=new PDPage(PDRectangle.A4);doc.addPage(p);stream=new PDPageContentStream(doc,p);y=797;if(table)tableHeader();}
        void ensure(float height,boolean table) throws IOException {if(y-height<55)newPage(table);}
        void gap(float height){y-=height;}
        String supported(String raw) {StringBuilder b=new StringBuilder();value(raw).codePoints().forEach(cp -> {
            if(cp=='\n'){b.append('\n');return;} if(Character.isISOControl(cp)){b.append(' ');return;}
            // hasGlyph expects a PDF character code, not a Unicode code point.
            // Encoding a Unicode string uses the font's cmap and works for CJK.
            try {font.getStringWidth(new String(Character.toChars(cp)));b.appendCodePoint(cp);}
            catch(IOException | IllegalArgumentException ex){b.append('?');}
        });return b.toString();}
        List<String> wrap(String raw,float size,float width) throws IOException {
            List<String> out=new ArrayList<>();StringBuilder line=new StringBuilder();float used=0;
            for(int cp:supported(raw).codePoints().toArray()) {
                if(cp=='\n'){out.add(line.toString());line.setLength(0);used=0;continue;}
                String ch=new String(Character.toChars(cp));float w=font.getStringWidth(ch)*size/1000;
                if(used+w>width && !line.isEmpty()){out.add(line.toString());line.setLength(0);used=0;}
                line.append(ch);used+=w;
            }out.add(line.toString());return out;
        }
        void text(String s,float x,float baseline,float size,Color color) throws IOException {stream.beginText();stream.setFont(font,size);stream.setNonStrokingColor(color);stream.newLineAtOffset(x,baseline);stream.showText(s);stream.endText();}
        void paragraph(String raw,float size,float height) throws IOException {if(raw==null || raw.isBlank())return;for(String l:wrap(raw,size,511)){ensure(height,false);text(l,42,y-size,size,new Color(35,45,58));y-=height;}}
        void tableHeader() throws IOException {ensure(29,false);stream.setNonStrokingColor(new Color(30,67,81));stream.addRect(42,y-29,511,29);stream.fill();
            String[] labels={"SKU","商品 / Description","数量 / Qty","单价 / Price","金额 / Amount"};float x=42;
            for(int i=0;i<labels.length;i++){text(labels[i],x+5,y-18,8,Color.WHITE);x+=widths[i];}y-=29;
        }
        void tableRow(String[] cells) throws IOException {List<List<String>> wrapped=new ArrayList<>();int count=1;for(int i=0;i<cells.length;i++){var ls=wrap(cells[i],9,widths[i]-10);wrapped.add(ls);count=Math.max(count,ls.size());}
            float h=count*14+12;ensure(h,true);float x=42;
            for(int i=0;i<cells.length;i++){int n=0;for(String s:wrapped.get(i))text(s,x+5,y-16-(n++)*14,9,new Color(35,45,58));x+=widths[i];}
            y-=h;stream.setStrokingColor(new Color(220,226,230));stream.setLineWidth(.5f);stream.moveTo(42,y);stream.lineTo(553,y);stream.stroke();
        }
    }
}
