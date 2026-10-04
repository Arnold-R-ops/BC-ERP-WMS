package com.wms.system.service;

import com.wms.system.dto.report.*;
import com.wms.system.entity.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReportCsvService {
    private final ReportQueryService reports;
    private static final String BASIS = "APPROVED/COMPLETED/CLOSED NORMAL orders; original confirmed amount; order creation date (UTC); GBP; not receipts or dispatched value";

    public static String csv(List<Object[]> rows) {
        StringBuilder output = new StringBuilder("\uFEFF");
        for (Object[] row : rows) {
            for (int index = 0; index < row.length; index++) {
                if (index > 0) output.append(',');
                Object value = row[index];
                String text = value == null ? "" : value.toString();
                if (value instanceof String && text.matches("(?s)^[\\s\\p{Cntrl}]*[=+@-].*")) text = "'" + text;
                output.append('"').append(text.replace("\"", "\"\"")).append('"');
            }
            output.append("\r\n");
        }
        return output.toString();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String sales(LocalDate start, LocalDate end) {
        var summary = reports.getSalesOverview(start, end);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {"类型 / Type", "日期 / Date", "开始 / From", "结束 / To", "已确认订单数 / Confirmed orders",
            "已确认订单额 / Confirmed amount", "平均订单额 / Average order value", "草稿 / Draft",
            "待审批 / Pending", "待发完 / Awaiting full shipment", "已发完 / Fully shipped",
            "关闭剩余 / Closed remainder", "驳回 / Rejected", "取消 / Cancelled", "作废 / Voided", "快照 / Refreshed", "口径 / Basis"});
        rows.add(new Object[] {"TOTAL", null, start, end, summary.getTotalOrderCount(), summary.getTotalAmount(),
            summary.getAverageOrderValue(), summary.getDraftCount(), summary.getPendingApprovalCount(),
            summary.getApprovedAwaitingShipmentCount(), summary.getShippedCount(), summary.getClosedCount(), summary.getRejectedCount(),
            summary.getCancelledCount(), summary.getVoidedCount(), summary.getRefreshedAt(), BASIS});
        for (var day : reports.listSalesDailySummary(start, end)) {
            var average = day.getTotalOrderCount() == null || day.getTotalOrderCount() == 0
                ? java.math.BigDecimal.ZERO : day.getTotalAmount().divide(java.math.BigDecimal.valueOf(day.getTotalOrderCount()), 2, java.math.RoundingMode.HALF_UP);
            rows.add(new Object[] {"DAY", day.getSummaryDate(), start, end, day.getTotalOrderCount(), day.getTotalAmount(),
                average, day.getDraftCount(), day.getPendingApprovalCount(), day.getApprovedAwaitingShipmentCount(),
                day.getShippedCount(), day.getClosedCount(), day.getRejectedCount(), day.getCancelledCount(), day.getVoidedCount(), day.getRefreshedAt(), BASIS});
        }
        return csv(rows);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String customers(String keyword, CustomerType type, CustomerSource source, String sortBy, String sortDirection) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {"客户编号 / Code", "客户 / Customer", "类型 / Type", "来源 / Source",
            "已确认订单数 / Confirmed orders", "累计订单额 GBP / Lifetime amount", "平均订单额 GBP / Average value",
            "最近下单 / Last order", "平均间隔天 / Interval days", "快照 / Refreshed", "范围 / Scope",
            "搜索 / Keyword", "类型筛选 / Type filter", "来源筛选 / Source filter"});
        int page = 0;
        org.springframework.data.domain.Page<CustomerFactListItemResponse> batch;
        do {
            batch = reports.listCustomerFacts(keyword, type, source, sortBy, sortDirection, page++, 100);
            for (var customer : batch) rows.add(new Object[] {customer.getCustomerCode(), customer.getCustomerName(),
                customer.getCustomerType(), customer.getSource(), customer.getTotalOrderCount(), customer.getTotalAmount(),
                customer.getAverageOrderValue(), customer.getLastOrderDate(), customer.getAverageIntervalDays(),
                customer.getRefreshedAt(), "Lifetime; " + BASIS, keyword, type, source});
        } while (batch.hasNext());
        return csv(rows);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String customerProducts(Long customerId, int topProducts) {
        var customer = reports.getCustomerFactSummary(customerId, topProducts);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {"客户编号 / Code", "客户 / Customer", "商品编码 / SKU", "商品 / Product",
            "条码 / Barcode", "当前基础单位 / Current base unit", "已确认订单数 / Confirmed orders", "基础单位数量 / Base-unit quantity", "累计订单额 GBP / Lifetime amount",
            "首次下单 / First order", "最近下单 / Last order", "快照 / Refreshed", "范围 / Scope"});
        for (var product : customer.getTopProducts()) rows.add(new Object[] {customer.getCustomerCode(),
            customer.getCustomerName(), product.getSkuCode(), product.getSkuName(), product.getBarcode(), product.getBaseUnit(),
            product.getTotalOrderCount(), product.getTotalQuantity(), product.getTotalAmount(),
            product.getFirstOrderDate(), product.getLastOrderDate(), customer.getRefreshedAt(),
            "Lifetime Top " + Math.min(Math.max(topProducts, 1), 20) + "; " + BASIS});
        return csv(rows);
    }
}
