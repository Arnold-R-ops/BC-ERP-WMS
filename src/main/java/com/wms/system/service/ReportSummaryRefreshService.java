package com.wms.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.wms.system.tenant.context.CompanyScope;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSummaryRefreshService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public RefreshResult refreshAll() {
        Long companyId = CompanyScope.currentCompanyId();
        int customerRows = refreshCustomerFacts(companyId);
        int staleCustomerRows = deleteStaleCustomerFacts(companyId);
        int customerProductRows = refreshCustomerProductFacts(companyId);
        int staleCustomerProductRows = deleteStaleCustomerProductFacts(companyId);
        int salesDailyRows = refreshSalesDailySummary(companyId);
        int staleSalesRows = deleteStaleSalesDailySummary(companyId);

        log.info(
            "Report fact buffer refreshed: customerRows={}, staleCustomerRows={}, customerProductRows={}, "
                + "staleCustomerProductRows={}, salesDailyRows={}, staleSalesRows={}",
            customerRows,
            staleCustomerRows,
            customerProductRows,
            staleCustomerProductRows,
            salesDailyRows,
            staleSalesRows
        );

        return new RefreshResult(
            customerRows,
            staleCustomerRows,
            customerProductRows,
            staleCustomerProductRows,
            salesDailyRows,
            staleSalesRows
        );
    }

    /** Rebuilds one sales day in a new transaction after the source write commits. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int refreshSalesDailySummary(Long companyId, LocalDate summaryDate) {
        if (!CompanyScope.currentCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                "Report refresh company does not match the current company context");
        }
        String sql = """
            INSERT INTO sales_daily_summary (
                company_id,
                summary_date,
                total_order_count,
                total_amount,
                draft_count,
                pending_approval_count,
                approved_awaiting_shipment_count,
                shipped_count,
                rejected_count,
                cancelled_count,
                voided_count,
                refreshed_at,
                created_at,
                updated_at
            )
            SELECT
                company_id,
                created_at::date AS summary_date,
                COUNT(*) FILTER (
                    WHERE status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
                ) AS total_order_count,
                COALESCE(
                    SUM(total_amount) FILTER (
                        WHERE status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
                    ),
                    0.00
                ) AS total_amount,
                COUNT(*) FILTER (WHERE status = 'DRAFT') AS draft_count,
                COUNT(*) FILTER (WHERE status = 'PENDING_APPROVAL') AS pending_approval_count,
                COUNT(*) FILTER (WHERE status = 'APPROVED_AWAITING_SHIPMENT')
                    AS approved_awaiting_shipment_count,
                COUNT(*) FILTER (WHERE status = 'SHIPPED') AS shipped_count,
                COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected_count,
                COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count,
                COUNT(*) FILTER (WHERE status = 'VOIDED') AS voided_count,
                CURRENT_TIMESTAMP AS refreshed_at,
                CURRENT_TIMESTAMP AS created_at,
                CURRENT_TIMESTAMP AS updated_at
            FROM sales_orders
            WHERE company_id = ?
              AND created_at::date = ?
            GROUP BY company_id, created_at::date
            ON CONFLICT (company_id, summary_date)
            DO UPDATE SET
                total_order_count = EXCLUDED.total_order_count,
                total_amount = EXCLUDED.total_amount,
                draft_count = EXCLUDED.draft_count,
                pending_approval_count = EXCLUDED.pending_approval_count,
                approved_awaiting_shipment_count = EXCLUDED.approved_awaiting_shipment_count,
                shipped_count = EXCLUDED.shipped_count,
                rejected_count = EXCLUDED.rejected_count,
                cancelled_count = EXCLUDED.cancelled_count,
                voided_count = EXCLUDED.voided_count,
                refreshed_at = EXCLUDED.refreshed_at,
                updated_at = EXCLUDED.updated_at
            """;

        return jdbcTemplate.update(sql, companyId, summaryDate);
    }

    private int refreshCustomerFacts(Long companyId) {
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

        return jdbcTemplate.update(sql, companyId);
    }

    private int deleteStaleCustomerFacts(Long companyId) {
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

        return jdbcTemplate.update(sql, companyId);
    }

    private int refreshCustomerProductFacts(Long companyId) {
        String sql = """
            INSERT INTO customer_product_summary (
                company_id,
                customer_id,
                product_sku_id,
                total_order_count,
                total_quantity,
                total_amount,
                first_order_date,
                last_order_date,
                average_interval_days,
                refreshed_at,
                created_at,
                updated_at
            )
            WITH valid_customer_product_orders AS (
                SELECT
                    sales_order.company_id,
                    sales_order.customer_id,
                    sales_order_item.product_sku_id,
                    sales_order.id AS order_id,
                    sales_order.created_at,
                    SUM(sales_order_item.quantity) AS order_quantity,
                    SUM(sales_order_item.subtotal) AS order_amount
                FROM sales_orders sales_order
                JOIN sales_order_items sales_order_item
                  ON sales_order_item.sales_order_id = sales_order.id
                 AND sales_order_item.company_id = sales_order.company_id
                WHERE sales_order.company_id = ?
                  AND sales_order.status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
                GROUP BY
                    sales_order.company_id,
                    sales_order.customer_id,
                    sales_order_item.product_sku_id,
                    sales_order.id,
                    sales_order.created_at
            ), sequenced_orders AS (
                SELECT
                    valid_order.*,
                    LAG(valid_order.created_at) OVER (
                        PARTITION BY valid_order.company_id, valid_order.customer_id, valid_order.product_sku_id
                        ORDER BY valid_order.created_at, valid_order.order_id
                    ) AS previous_created_at
                FROM valid_customer_product_orders valid_order
            )
            SELECT
                company_id,
                customer_id,
                product_sku_id,
                COUNT(*) AS total_order_count,
                COALESCE(SUM(order_quantity), 0) AS total_quantity,
                COALESCE(SUM(order_amount), 0.00) AS total_amount,
                MIN(created_at)::date AS first_order_date,
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
            FROM sequenced_orders
            GROUP BY company_id, customer_id, product_sku_id
            ON CONFLICT (company_id, customer_id, product_sku_id)
            DO UPDATE SET
                total_order_count = EXCLUDED.total_order_count,
                total_quantity = EXCLUDED.total_quantity,
                total_amount = EXCLUDED.total_amount,
                first_order_date = EXCLUDED.first_order_date,
                last_order_date = EXCLUDED.last_order_date,
                average_interval_days = EXCLUDED.average_interval_days,
                refreshed_at = EXCLUDED.refreshed_at,
                updated_at = EXCLUDED.updated_at
            """;

        return jdbcTemplate.update(sql, companyId);
    }

    private int deleteStaleCustomerProductFacts(Long companyId) {
        String sql = """
            DELETE FROM customer_product_summary customer_product
            WHERE customer_product.company_id = ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM sales_orders sales_order
                  JOIN sales_order_items sales_order_item
                    ON sales_order_item.sales_order_id = sales_order.id
                   AND sales_order_item.company_id = sales_order.company_id
                  WHERE sales_order.company_id = customer_product.company_id
                    AND sales_order.customer_id = customer_product.customer_id
                    AND sales_order_item.product_sku_id = customer_product.product_sku_id
                    AND sales_order.status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
              )
            """;

        return jdbcTemplate.update(sql, companyId);
    }

    private int refreshSalesDailySummary(Long companyId) {
        String sql = """
            INSERT INTO sales_daily_summary (
                company_id,
                summary_date,
                total_order_count,
                total_amount,
                draft_count,
                pending_approval_count,
                approved_awaiting_shipment_count,
                shipped_count,
                rejected_count,
                cancelled_count,
                voided_count,
                refreshed_at,
                created_at,
                updated_at
            )
            SELECT
                company_id,
                created_at::date AS summary_date,
                COUNT(*) FILTER (
                    WHERE status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
                ) AS total_order_count,
                COALESCE(
                    SUM(total_amount) FILTER (
                        WHERE status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
                    ),
                    0.00
                ) AS total_amount,
                COUNT(*) FILTER (WHERE status = 'DRAFT') AS draft_count,
                COUNT(*) FILTER (WHERE status = 'PENDING_APPROVAL') AS pending_approval_count,
                COUNT(*) FILTER (WHERE status = 'APPROVED_AWAITING_SHIPMENT')
                    AS approved_awaiting_shipment_count,
                COUNT(*) FILTER (WHERE status = 'SHIPPED') AS shipped_count,
                COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected_count,
                COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count,
                COUNT(*) FILTER (WHERE status = 'VOIDED') AS voided_count,
                CURRENT_TIMESTAMP AS refreshed_at,
                CURRENT_TIMESTAMP AS created_at,
                CURRENT_TIMESTAMP AS updated_at
            FROM sales_orders
            WHERE company_id = ?
            GROUP BY company_id, created_at::date
            ON CONFLICT (company_id, summary_date)
            DO UPDATE SET
                total_order_count = EXCLUDED.total_order_count,
                total_amount = EXCLUDED.total_amount,
                draft_count = EXCLUDED.draft_count,
                pending_approval_count = EXCLUDED.pending_approval_count,
                approved_awaiting_shipment_count = EXCLUDED.approved_awaiting_shipment_count,
                shipped_count = EXCLUDED.shipped_count,
                rejected_count = EXCLUDED.rejected_count,
                cancelled_count = EXCLUDED.cancelled_count,
                voided_count = EXCLUDED.voided_count,
                refreshed_at = EXCLUDED.refreshed_at,
                updated_at = EXCLUDED.updated_at
            """;

        return jdbcTemplate.update(sql, companyId);
    }

    private int deleteStaleSalesDailySummary(Long companyId) {
        String sql = """
            DELETE FROM sales_daily_summary sds
            WHERE sds.company_id = ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM sales_orders so
                  WHERE so.company_id = sds.company_id
                    AND so.created_at::date = sds.summary_date
              )
            """;

        return jdbcTemplate.update(sql, companyId);
    }

    public record RefreshResult(
        int customerRows,
        int staleCustomerRows,
        int customerProductRows,
        int staleCustomerProductRows,
        int salesDailyRows,
        int staleSalesRows
    ) {
    }
}
