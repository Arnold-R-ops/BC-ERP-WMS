CREATE TABLE public.warehouse_stock_threshold (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES public.tenants(id),
    product_sku_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    min_stock INTEGER NOT NULL CHECK (min_stock >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, product_sku_id, warehouse_id),
    FOREIGN KEY (company_id, product_sku_id) REFERENCES public.product_skus(company_id, id),
    FOREIGN KEY (company_id, warehouse_id) REFERENCES public.warehouses(company_id, id)
);
ALTER TABLE public.warehouse_stock_threshold ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.warehouse_stock_threshold FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.warehouse_stock_threshold FOR ALL
    USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());
