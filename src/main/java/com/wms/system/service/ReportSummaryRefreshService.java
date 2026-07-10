package com.wms.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSummaryRefreshService {

    private static final Long DEFAULT_COMPANY_ID = 1L;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public RefreshResult refreshAll() {
        int customerRows = refreshCustomerFacts();
        int staleCustomerRows = deleteStaleCustomerFacts();
        int salesDailyRows = refreshSalesDailySummary();
        int staleSalesRows = deleteStaleSalesDailySummary();

        log.info(
            "Report fact buffer refreshed: customerRows={}, staleCustomerRows={}, salesDailyRows={}, staleSalesRows={}",
            customerRows, staleCustomerRows, salesDailyRows, staleSalesRows
        );

        return new RefreshResult(customerRows, staleCustomerRows, salesDailyRows, staleSalesRows);
    }

    private int refreshCustomerFacts() {
        String sql = """
            INSERT INTO customer_fact_summary (
                company_id,
                customer_id,
                total_order_count,
                total_amount,
                last_order_date,
                average_interval_days,
                refreshed_at,
                created_at,
                updated_at
            )
            WITH valid_orders AS (
                SELECT
                    company_id,
                    customer_id,
                    total_amount,
                    created_at,
                    LAG(created_at) OVER (
                        PARTITION BY company_id, customer_id
                        ORDER BY created_at
                    ) AS previous_created_at
                FROM sales_orders
                WHERE company_id = ?
                  AND status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
            )
            SELECT
                company_id,
                customer_id,
                COUNT(*) AS total_order_count,
                COALESCE(SUM(total_amount), 0.00) AS total_amount,
                MAX(created_at)::date AS last_order_date,
                COALESCE(
                    ROUND(
                        AVG(EXTRACT(EPOCH FROM (created_at - previous_created_at)) / 86400.0)
                            FILTER (WHERE previous_created_at IS NOT NULL),
                        2
                    ),
                    0.00
                ) AS average_interval_days,
                CURRENT_TIMESTAMP AS refreshed_at,
                CURRENT_TIMESTAMP AS created_at,
                CURRENT_TIMESTAMP AS updated_at
            FROM valid_orders
            GROUP BY company_id, customer_id
            ON CONFLICT (company_id, customer_id)
            DO UPDATE SET
                total_order_count = EXCLUDED.total_order_count,
                total_amount = EXCLUDED.total_amount,
                last_order_date = EXCLUDED.last_order_date,
                average_interval_days = EXCLUDED.average_interval_days,
                refreshed_at = EXCLUDED.refreshed_at,
                updated_at = EXCLUDED.updated_at
            """;

        return jdbcTemplate.update(sql, DEFAULT_COMPANY_ID);
    }

    private int deleteStaleCustomerFacts() {
        String sql = """
            DELETE FROM customer_fact_summary cfs
            WHERE cfs.company_id = ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM sales_orders so
                  WHERE so.company_id = cfs.company_id
                    AND so.customer_id = cfs.customer_id
                    AND so.status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
              )
            """;

        return jdbcTemplate.update(sql, DEFAULT_COMPANY_ID);
    }

    private int refreshSalesDailySummary() {
        String sql = """
            INSERT INTO sales_daily_summary (
                company_id,
                summary_date,
                total_order_count,
                total_amount,
                refreshed_at,
                created_at,
                updated_at
            )
            SELECT
                company_id,
                created_at::date AS summary_date,
                COUNT(*) AS total_order_count,
                COALESCE(SUM(total_amount), 0.00) AS total_amount,
                CURRENT_TIMESTAMP AS refreshed_at,
                CURRENT_TIMESTAMP AS created_at,
                CURRENT_TIMESTAMP AS updated_at
            FROM sales_orders
            WHERE company_id = ?
              AND status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
            GROUP BY company_id, created_at::date
            ON CONFLICT (company_id, summary_date)
            DO UPDATE SET
                total_order_count = EXCLUDED.total_order_count,
                total_amount = EXCLUDED.total_amount,
                refreshed_at = EXCLUDED.refreshed_at,
                updated_at = EXCLUDED.updated_at
            """;

        return jdbcTemplate.update(sql, DEFAULT_COMPANY_ID);
    }

    private int deleteStaleSalesDailySummary() {
        String sql = """
            DELETE FROM sales_daily_summary sds
            WHERE sds.company_id = ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM sales_orders so
                  WHERE so.company_id = sds.company_id
                    AND so.created_at::date = sds.summary_date
                    AND so.status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
              )
            """;

        return jdbcTemplate.update(sql, DEFAULT_COMPANY_ID);
    }

    public record RefreshResult(
        int customerRows,
        int staleCustomerRows,
        int salesDailyRows,
        int staleSalesRows
    ) {
    }
}
