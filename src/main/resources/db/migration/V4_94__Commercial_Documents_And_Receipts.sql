-- Quotations, commercial invoices and immutable manual payment evidence; no inventory side effects.
CREATE UNIQUE INDEX IF NOT EXISTS uk_v494_customers_company_id ON public.customers(company_id,id);
CREATE TABLE public.commercial_profile (
 id BIGSERIAL PRIMARY KEY,company_id BIGINT NOT NULL REFERENCES public.tenants(id),
 created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 name VARCHAR(200) NOT NULL,address VARCHAR(255),phone VARCHAR(50),email VARCHAR(100),terms VARCHAR(2000),UNIQUE(company_id)
);
ALTER TABLE public.commercial_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.commercial_profile FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.commercial_profile FOR ALL
 USING(company_id=public.bcwms_current_company_id()) WITH CHECK(company_id=public.bcwms_current_company_id());
CREATE TABLE public.commercial_document (
 id BIGSERIAL PRIMARY KEY,company_id BIGINT NOT NULL REFERENCES public.tenants(id),
 created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version BIGINT NOT NULL DEFAULT 0,
kind VARCHAR(12) NOT NULL CHECK(kind IN ('QUOTE','INVOICE')),
status VARCHAR(12) NOT NULL,
request_key VARCHAR(36) NOT NULL,request_hash VARCHAR(64) NOT NULL,
customer_id BIGINT NOT NULL,sales_order_id BIGINT,created_by BIGINT NOT NULL,
customer_name VARCHAR(200) NOT NULL,billing_address VARCHAR(255),shipping_address VARCHAR(255),
company_name VARCHAR(200) NOT NULL,company_address VARCHAR(255),company_phone VARCHAR(50),company_email VARCHAR(100),
terms VARCHAR(2000),document_date DATE NOT NULL,due_date DATE NOT NULL,total NUMERIC(15,2) NOT NULL CHECK(total>=0),close_reason VARCHAR(500),
UNIQUE(company_id,id),UNIQUE(company_id,kind,request_key),
FOREIGN KEY(company_id,customer_id) REFERENCES customers(company_id,id),
FOREIGN KEY(company_id,sales_order_id) REFERENCES sales_orders(company_id,id),
CHECK((kind='QUOTE' AND status IN ('OPEN','CONVERTED','CANCELLED')) OR (kind='INVOICE' AND status IN ('ISSUED','VOID'))),
CHECK(status<>'CONVERTED' OR sales_order_id IS NOT NULL),CHECK(kind<>'INVOICE' OR sales_order_id IS NOT NULL)
);
ALTER TABLE public.commercial_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.commercial_document FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.commercial_document FOR ALL
 USING(company_id=public.bcwms_current_company_id()) WITH CHECK(company_id=public.bcwms_current_company_id());
CREATE TABLE public.commercial_document_line (
 id BIGSERIAL PRIMARY KEY,company_id BIGINT NOT NULL REFERENCES public.tenants(id),
 created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 document_id BIGINT NOT NULL,position INTEGER NOT NULL,product_sku_id BIGINT NOT NULL,
sku_code VARCHAR(50) NOT NULL,description VARCHAR(300) NOT NULL,unit VARCHAR(20) NOT NULL,
quantity INTEGER NOT NULL CHECK(quantity>0),unit_price NUMERIC(10,2) NOT NULL CHECK(unit_price>=0),amount NUMERIC(15,2) NOT NULL,
UNIQUE(company_id,document_id,position),CHECK(amount=quantity*unit_price),
FOREIGN KEY(company_id,document_id) REFERENCES commercial_document(company_id,id),
FOREIGN KEY(company_id,product_sku_id) REFERENCES product_skus(company_id,id)
);
ALTER TABLE public.commercial_document_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.commercial_document_line FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.commercial_document_line FOR ALL
 USING(company_id=public.bcwms_current_company_id()) WITH CHECK(company_id=public.bcwms_current_company_id());
CREATE TABLE public.commercial_receipt (
 id BIGSERIAL PRIMARY KEY,company_id BIGINT NOT NULL REFERENCES public.tenants(id),
 created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 document_id BIGINT NOT NULL,kind VARCHAR(12) NOT NULL CHECK(kind IN ('RECEIPT','REVERSAL')),reverses_id BIGINT,
request_key VARCHAR(36) NOT NULL,request_hash VARCHAR(64) NOT NULL,amount NUMERIC(15,2) NOT NULL CHECK(amount>0),
payment_date DATE NOT NULL,method VARCHAR(20) NOT NULL CHECK(method IN ('BANK_TRANSFER','CASH','CARD','OTHER')),
reference VARCHAR(120),note VARCHAR(500),created_by BIGINT NOT NULL,
UNIQUE(company_id,id),UNIQUE(company_id,request_key),UNIQUE(company_id,reverses_id),
CHECK((kind='RECEIPT' AND reverses_id IS NULL) OR (kind='REVERSAL' AND reverses_id IS NOT NULL AND length(trim(note))>0)),
FOREIGN KEY(company_id,document_id) REFERENCES commercial_document(company_id,id),
FOREIGN KEY(company_id,reverses_id) REFERENCES commercial_receipt(company_id,id)
);
ALTER TABLE public.commercial_receipt ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.commercial_receipt FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.commercial_receipt FOR ALL
 USING(company_id=public.bcwms_current_company_id()) WITH CHECK(company_id=public.bcwms_current_company_id());
CREATE UNIQUE INDEX uk_commercial_active_invoice ON commercial_document(company_id,sales_order_id) WHERE kind='INVOICE' AND status='ISSUED';
CREATE INDEX idx_commercial_document_customer ON commercial_document(company_id,customer_id,kind,id);
CREATE INDEX idx_commercial_receipt_document ON commercial_receipt(company_id,document_id);
CREATE FUNCTION public.bcwms_commercial_receipt_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' AND current_setting('app.tenant_purge',true)='on' THEN RETURN OLD; END IF;
 -- Tenant purge attempts to null self references first. Skip that update: deleting
 -- all company receipt rows in one statement satisfies the self FK without mutating evidence.
 IF TG_OP='UPDATE' AND current_setting('app.tenant_purge',true)='on' THEN RETURN NULL; END IF;
 RAISE EXCEPTION 'Commercial receipt evidence is immutable; record a reversal instead';
END $$;
CREATE TRIGGER trg_commercial_receipt_immutable BEFORE UPDATE OR DELETE ON public.commercial_receipt
 FOR EACH ROW EXECUTE FUNCTION public.bcwms_commercial_receipt_immutable();
DO $$ DECLARE tenant_row RECORD; prior_scope TEXT:=current_setting('app.company_id',true); BEGIN
 FOR tenant_row IN SELECT id FROM tenants LOOP
  PERFORM set_config('app.company_id',tenant_row.id::text,true);
  INSERT INTO sys_permission(company_id,permission_code,permission_name,permission_type,resource_path,http_method,data_scope,risk_level,custom_assignable,status,sort_order,created_at,updated_at)
  SELECT tenant_row.id,v.code,v.name,'API',v.path,v.method,'ALL',v.risk,true,'ACTIVE',v.sort_order,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
  FROM (VALUES ('quotation:view','查看报价','/api/commercial/quotes/**;/api/commercial/quotes;/api/commercial/profile','GET','NORMAL',181),
('quotation:edit','编制报价','/api/commercial/quotes/**;/api/commercial/quotes','*','NORMAL',182),
('quotation:convert','报价转销售单','/api/commercial/quotes/*/convert','POST','HIGH',183),
('invoice:view','查看账单与收款','/api/commercial/invoices/**;/api/commercial/invoices;/api/commercial/profile','GET','NORMAL',184),
('invoice:create','开立或作废账单','/api/commercial/invoices;/api/commercial/invoices/*/void','POST','HIGH',185),
('invoice:receive','登记和更正手工收款','/api/commercial/invoices/*/receipts;/api/commercial/invoices/*/receipts/*/reverse','POST','HIGH',186),
('commercial:export','导出报价及账单（含完整客户地址）','/api/commercial/quotes/*/pdf;/api/commercial/quotes/*/xlsx;/api/commercial/invoices/*/pdf','GET','HIGH',187)) v(code,name,path,method,risk,sort_order)
  ON CONFLICT(company_id,permission_code) DO NOTHING;
 END LOOP;
 PERFORM set_config('app.company_id',coalesce(prior_scope,''),true);
END $$;
