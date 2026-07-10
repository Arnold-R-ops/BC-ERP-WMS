-- V4.6 phase 1: reporting fact buffer.
-- These summary tables keep dashboard/customer portrait reads away from hot order tables.

CREATE TABLE IF NOT EXISTS customer_fact_summary (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    customer_id BIGINT NOT NULL,
    total_order_count BIGINT NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    last_order_date DATE,
    average_interval_days NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customer_fact_summary_company_customer UNIQUE (company_id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_fact_summary_customer
    ON customer_fact_summary(customer_id);

CREATE INDEX IF NOT EXISTS idx_customer_fact_summary_refreshed
    ON customer_fact_summary(refreshed_at);

CREATE TABLE IF NOT EXISTS sales_daily_summary (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    summary_date DATE NOT NULL,
    total_order_count BIGINT NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_daily_summary_company_date UNIQUE (company_id, summary_date)
);

CREATE INDEX IF NOT EXISTS idx_sales_daily_summary_date
    ON sales_daily_summary(summary_date);

CREATE INDEX IF NOT EXISTS idx_sales_daily_summary_refreshed
    ON sales_daily_summary(refreshed_at);

COMMENT ON TABLE customer_fact_summary IS 'Customer reporting fact buffer refreshed by nightly batch job.';
COMMENT ON COLUMN customer_fact_summary.customer_id IS 'Customer ID from customers.id.';
COMMENT ON COLUMN customer_fact_summary.total_order_count IS 'Effective sales order count excluding rejected, cancelled, and voided orders.';
COMMENT ON COLUMN customer_fact_summary.total_amount IS 'Effective sales order amount excluding rejected, cancelled, and voided orders.';
COMMENT ON COLUMN customer_fact_summary.last_order_date IS 'Last effective sales order creation date.';
COMMENT ON COLUMN customer_fact_summary.average_interval_days IS 'Average days between effective orders for this customer.';
COMMENT ON COLUMN customer_fact_summary.refreshed_at IS 'Last batch refresh timestamp.';

COMMENT ON TABLE sales_daily_summary IS 'Daily sales reporting fact buffer refreshed by nightly batch job.';
COMMENT ON COLUMN sales_daily_summary.summary_date IS 'Sales order creation date in backend UTC calendar.';
COMMENT ON COLUMN sales_daily_summary.total_order_count IS 'Effective daily sales order count excluding rejected, cancelled, and voided orders.';
COMMENT ON COLUMN sales_daily_summary.total_amount IS 'Effective daily sales amount excluding rejected, cancelled, and voided orders.';
COMMENT ON COLUMN sales_daily_summary.refreshed_at IS 'Last batch refresh timestamp.';
