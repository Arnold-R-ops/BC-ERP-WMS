-- BC WMS/ERP clean-install state baseline at V4.71.
--
-- This is a Flyway baseline migration (B prefix), not a normal incremental
-- migration. A database with existing Flyway history ignores it. A truly empty
-- database applies this state snapshot and future V migrations only.
--
-- Generated from a read-only, schema-only dump of the validated V4.71 public
-- schema. Deliberately excluded: Flyway metadata, owners, ACLs, database
-- roles, psql client commands, tenant/account/audit/business rows and sequence
-- current values. The only rows below are required global reference catalogues.

CREATE FUNCTION public._wms_reject_platform_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'platform_audit_logs is append-only';
END;
$$;


--
-- Name: bcwms_current_company_id(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bcwms_current_company_id() RETURNS bigint
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$
    SELECT NULLIF(current_setting('app.company_id', true), '')::BIGINT
$$;


--
-- Name: FUNCTION bcwms_current_company_id(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.bcwms_current_company_id() IS 'Transaction-local company discriminator used by BCWMS RLS policies';


--
-- Name: bcwms_purge_company_data(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bcwms_purge_company_data(p_company_id bigint) RETURNS bigint
    LANGUAGE plpgsql
    AS $_$
DECLARE
    tenant_state VARCHAR(30);
    tenant_purge_due_at TIMESTAMPTZ;
    tenant_table RECORD;
    deleted_rows BIGINT;
    total_deleted BIGINT := 0;
    remaining_rows BIGINT;
BEGIN
    IF p_company_id IS NULL OR p_company_id <= 0 THEN
        RAISE EXCEPTION 'A positive company identifier is required';
    END IF;
    IF bcwms_current_company_id() IS DISTINCT FROM p_company_id THEN
        RAISE EXCEPTION 'Company purge context mismatch';
    END IF;

    SELECT status, purge_due_at INTO tenant_state, tenant_purge_due_at
      FROM tenants WHERE id = p_company_id FOR UPDATE;
    IF tenant_state IS DISTINCT FROM 'PURGE_PENDING' THEN
        RAISE EXCEPTION 'Company % is not PURGE_PENDING', p_company_id;
    END IF;
    IF tenant_purge_due_at IS NULL OR tenant_purge_due_at > CURRENT_TIMESTAMP THEN
        RAISE EXCEPTION 'Company % retention deadline has not expired', p_company_id;
    END IF;
    PERFORM set_config('app.tenant_purge', 'on', true);

    CREATE TEMP TABLE IF NOT EXISTS bcwms_purge_identity_candidates (
        identity_id BIGINT PRIMARY KEY
    ) ON COMMIT DROP;
    TRUNCATE bcwms_purge_identity_candidates;
    INSERT INTO bcwms_purge_identity_candidates(identity_id)
    SELECT identity_id FROM tenant_memberships WHERE tenant_id = p_company_id
    UNION
    SELECT identity_id FROM signup_requests
     WHERE tenant_id = p_company_id AND identity_id IS NOT NULL;

    -- Break nullable self-references (for example category trees) before set deletes.
    FOR tenant_table IN
        SELECT c.relname, a.attname
        FROM pg_constraint con
        JOIN pg_class c ON c.oid = con.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN unnest(con.conkey) key(attnum) ON TRUE
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = key.attnum
        JOIN pg_attribute discriminator ON discriminator.attrelid = c.oid
            AND discriminator.attname = 'company_id' AND NOT discriminator.attisdropped
        WHERE n.nspname = 'public' AND con.contype = 'f'
          AND con.conrelid = con.confrelid AND NOT a.attnotnull
    LOOP
        EXECUTE format('UPDATE public.%I SET %I = NULL WHERE company_id = $1',
            tenant_table.relname, tenant_table.attname) USING p_company_id;
    END LOOP;

    -- Control-plane rows that reference business users/configuration must go first.
    DELETE FROM channel_webhook_routes WHERE tenant_id = p_company_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT; total_deleted := total_deleted + deleted_rows;
    DELETE FROM session_handoff_codes WHERE tenant_id = p_company_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT; total_deleted := total_deleted + deleted_rows;
    DELETE FROM platform_operation_authorizations WHERE tenant_id = p_company_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT; total_deleted := total_deleted + deleted_rows;
    DELETE FROM tenant_memberships WHERE tenant_id = p_company_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT; total_deleted := total_deleted + deleted_rows;

    -- Delete every tenant-owned table in foreign-key dependency order. This discovers
    -- future company_id tables too, and the final assertion prevents silent omissions.
    FOR tenant_table IN
        WITH RECURSIVE owned AS (
            SELECT c.oid, c.relname
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_attribute a ON a.attrelid = c.oid
            WHERE n.nspname = 'public' AND c.relkind = 'r'
              AND a.attname = 'company_id' AND NOT a.attisdropped
        ), dependency(child_oid, parent_oid, depth) AS (
            SELECT con.conrelid, con.confrelid, 1
            FROM pg_constraint con
            JOIN owned child ON child.oid = con.conrelid
            JOIN owned parent ON parent.oid = con.confrelid
            WHERE con.contype = 'f' AND con.conrelid <> con.confrelid
            UNION ALL
            SELECT d.child_oid, con.confrelid, d.depth + 1
            FROM dependency d
            JOIN pg_constraint con ON con.conrelid = d.parent_oid AND con.contype = 'f'
            JOIN owned parent ON parent.oid = con.confrelid
            WHERE con.confrelid <> d.child_oid AND d.depth < 100
        )
        SELECT o.relname, COALESCE(MAX(d.depth), 0) AS depth
        FROM owned o LEFT JOIN dependency d ON d.child_oid = o.oid
        GROUP BY o.oid, o.relname
        ORDER BY depth DESC, o.relname
    LOOP
        EXECUTE format('DELETE FROM public.%I WHERE company_id = $1', tenant_table.relname)
            USING p_company_id;
        GET DIAGNOSTICS deleted_rows = ROW_COUNT;
        total_deleted := total_deleted + deleted_rows;
    END LOOP;

    DELETE FROM email_verification_challenges
     WHERE signup_request_id IN (SELECT id FROM signup_requests WHERE tenant_id = p_company_id);
    DELETE FROM tenant_provisioning_jobs WHERE tenant_id = p_company_id;
    DELETE FROM tenant_subscriptions WHERE tenant_id = p_company_id;
    DELETE FROM signup_requests WHERE tenant_id = p_company_id;
    DELETE FROM tenant_domains WHERE tenant_id = p_company_id;
    DELETE FROM platform_export_jobs WHERE target_tenant_id = p_company_id;
    DELETE FROM user_identities identity
     WHERE identity.id IN (SELECT identity_id FROM bcwms_purge_identity_candidates)
       AND NOT EXISTS (SELECT 1 FROM tenant_memberships membership WHERE membership.identity_id = identity.id)
       AND NOT EXISTS (SELECT 1 FROM signup_requests signup WHERE signup.identity_id = identity.id);

    FOR tenant_table IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid
        WHERE n.nspname = 'public' AND c.relkind = 'r'
          AND a.attname = 'company_id' AND NOT a.attisdropped
    LOOP
        EXECUTE format('SELECT count(*) FROM public.%I WHERE company_id = $1', tenant_table.relname)
            INTO remaining_rows USING p_company_id;
        IF remaining_rows <> 0 THEN
            RAISE EXCEPTION 'Company purge left % rows in public.%', remaining_rows, tenant_table.relname;
        END IF;
    END LOOP;
    RETURN total_deleted;
END;
$_$;


--
-- Name: FUNCTION bcwms_purge_company_data(p_company_id bigint); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.bcwms_purge_company_data(p_company_id bigint) IS 'Irreversibly deletes one PURGE_PENDING company business/control data under matching RLS context';


--
-- Name: prevent_historical_archive_audit_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_historical_archive_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'historical_test_data_archive_audit is append-only';
END;
$$;


--
-- Name: prevent_historical_test_registry_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_historical_test_registry_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'historical_test_data_registry is migration-managed and immutable';
END;
$$;


--
-- Name: prevent_permission_request_audit_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_permission_request_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'sys_permission_request_audit is append-only';
END;
$$;


--
-- Name: prevent_tenant_purge_audit_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_tenant_purge_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'tenant_purge_audit_logs is append-only';
END;
$$;




--
-- Name: backorder_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.backorder_line (
    id bigint NOT NULL,
    sales_order_id bigint NOT NULL,
    sales_order_item_id bigint NOT NULL,
    product_sku_id bigint CONSTRAINT backorder_line_product_id_not_null NOT NULL,
    requested_qty integer NOT NULL,
    remaining_qty integer NOT NULL,
    allocated_qty integer DEFAULT 0 NOT NULL,
    status character varying(30) DEFAULT 'OPEN'::character varying NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    promised_date date,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.backorder_line FORCE ROW LEVEL SECURITY;


--
-- Name: backorder_line_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.backorder_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: backorder_line_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.backorder_line_id_seq OWNED BY public.backorder_line.id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    parent_id bigint,
    category_code character varying(50) NOT NULL,
    category_name character varying(100) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    description character varying(500),
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_categories_not_self_parent CHECK (((parent_id IS NULL) OR (parent_id <> id))),
    CONSTRAINT chk_categories_sort_order CHECK ((sort_order >= 0))
);

ALTER TABLE ONLY public.categories FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.categories IS 'Product category tree; current management UI exposes two visible levels';


--
-- Name: COLUMN categories.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.categories.parent_id IS 'Self reference supporting arbitrary category depth';


--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;


--
-- Name: channel_inventory_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.channel_inventory_state (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    channel character varying(50) NOT NULL,
    mapping_id bigint NOT NULL,
    last_pushed_available integer,
    last_pushed_at timestamp without time zone,
    status character varying(20) DEFAULT 'OK'::character varying NOT NULL,
    last_error text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE ONLY public.channel_inventory_state FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE channel_inventory_state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.channel_inventory_state IS 'Last pushed inventory level per channel SKU mapping - differential writeback state (P1-B4)';


--
-- Name: channel_inventory_state_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.channel_inventory_state_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: channel_inventory_state_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.channel_inventory_state_id_seq OWNED BY public.channel_inventory_state.id;


--
-- Name: channel_raw_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.channel_raw_events (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    channel character varying(50) NOT NULL,
    store_identifier character varying(255),
    source character varying(20) NOT NULL,
    event_type character varying(50) NOT NULL,
    external_id character varying(100),
    payload jsonb NOT NULL,
    status character varying(20) DEFAULT 'RECEIVED'::character varying NOT NULL,
    error_message text,
    processed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    webhook_event_id character varying(100)
);

ALTER TABLE ONLY public.channel_raw_events FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE channel_raw_events; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.channel_raw_events IS 'Raw channel payload landing table: persist before processing, per-payload status for diagnosis/replay/reconciliation';


--
-- Name: COLUMN channel_raw_events.channel; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_raw_events.channel IS 'Sales channel: SHOPIFY / AMAZON / ... (channel-generic)';


--
-- Name: COLUMN channel_raw_events.source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_raw_events.source IS 'How the payload arrived: POLL / WEBHOOK / RECONCILE';


--
-- Name: COLUMN channel_raw_events.event_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_raw_events.event_type IS 'Payload kind, e.g. ORDER';


--
-- Name: COLUMN channel_raw_events.external_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_raw_events.external_id IS 'Channel-side identifier (e.g. Shopify order id) for dedup and correlation';


--
-- Name: COLUMN channel_raw_events.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_raw_events.status IS 'RECEIVED / PROCESSED / FAILED / SKIPPED';


--
-- Name: COLUMN channel_raw_events.webhook_event_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_raw_events.webhook_event_id IS 'X-Shopify-Webhook-Id for exact webhook redelivery dedup (P1-B3)';


--
-- Name: channel_raw_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.channel_raw_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: channel_raw_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.channel_raw_events_id_seq OWNED BY public.channel_raw_events.id;


--
-- Name: channel_sku_mapping; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.channel_sku_mapping (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    channel character varying(50) NOT NULL,
    store_identifier character varying(255),
    external_sku character varying(200) NOT NULL,
    normalized_sku character varying(200) NOT NULL,
    mapping_type character varying(20) DEFAULT 'PRODUCT'::character varying NOT NULL,
    product_sku_id bigint,
    quantity_ratio integer DEFAULT 1 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    source character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    remark character varying(500),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    external_item_ref character varying(100),
    CONSTRAINT chk_sku_mapping_product_sku CHECK (((((mapping_type)::text = 'PRODUCT'::text) AND (product_sku_id IS NOT NULL)) OR ((mapping_type)::text = 'VIRTUAL'::text))),
    CONSTRAINT chk_sku_mapping_ratio CHECK ((quantity_ratio >= 1))
);

ALTER TABLE ONLY public.channel_sku_mapping FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE channel_sku_mapping; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.channel_sku_mapping IS 'Channel SKU -> internal product mapping (memory layer); VIRTUAL = non-stock line, excluded from fulfillment';


--
-- Name: COLUMN channel_sku_mapping.normalized_sku; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_sku_mapping.normalized_sku IS 'Uppercased, whitespace-stripped external_sku for fuzzy lookup';


--
-- Name: COLUMN channel_sku_mapping.quantity_ratio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_sku_mapping.quantity_ratio IS '1 external unit = N internal units (e.g. a 20KG listing selling a 20-unit bulk pack)';


--
-- Name: COLUMN channel_sku_mapping.source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_sku_mapping.source IS 'MANUAL (ops confirmed) / AUTO (learned from barcode match)';


--
-- Name: COLUMN channel_sku_mapping.external_item_ref; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.channel_sku_mapping.external_item_ref IS 'Channel-side inventory item reference (Shopify inventory_item_id), lazily resolved from catalog (P1-B4)';


--
-- Name: channel_sku_mapping_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.channel_sku_mapping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: channel_sku_mapping_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.channel_sku_mapping_id_seq OWNED BY public.channel_sku_mapping.id;


--
-- Name: channel_webhook_routes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.channel_webhook_routes (
    integration_config_id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    platform character varying(50) NOT NULL,
    canonical_store_identifier character varying(255) NOT NULL,
    signing_secret character varying(200) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_channel_webhook_routes_store_normalized CHECK ((((canonical_store_identifier)::text = lower(btrim((canonical_store_identifier)::text))) AND ((canonical_store_identifier)::text !~ '^https?://'::text) AND ((canonical_store_identifier)::text !~ '/$'::text) AND ((canonical_store_identifier)::text <> ''::text))),
    CONSTRAINT ck_channel_webhook_routes_tenant_positive CHECK ((tenant_id > 0))
);


--
-- Name: customer_fact_summary; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_fact_summary (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    customer_id bigint NOT NULL,
    total_order_count bigint DEFAULT 0 NOT NULL,
    total_amount numeric(15,2) DEFAULT 0.00 NOT NULL,
    last_order_date date,
    average_interval_days numeric(10,2) DEFAULT 0.00 NOT NULL,
    refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE ONLY public.customer_fact_summary FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE customer_fact_summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.customer_fact_summary IS 'Customer reporting fact buffer refreshed by nightly batch job.';


--
-- Name: COLUMN customer_fact_summary.customer_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_fact_summary.customer_id IS 'Customer ID from customers.id.';


--
-- Name: COLUMN customer_fact_summary.total_order_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_fact_summary.total_order_count IS 'Effective sales order count excluding rejected, cancelled, and voided orders.';


--
-- Name: COLUMN customer_fact_summary.total_amount; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_fact_summary.total_amount IS 'Effective sales order amount excluding rejected, cancelled, and voided orders.';


--
-- Name: COLUMN customer_fact_summary.last_order_date; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_fact_summary.last_order_date IS 'Last effective sales order creation date.';


--
-- Name: COLUMN customer_fact_summary.average_interval_days; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_fact_summary.average_interval_days IS 'Average days between effective orders for this customer.';


--
-- Name: COLUMN customer_fact_summary.refreshed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_fact_summary.refreshed_at IS 'Last batch refresh timestamp.';


--
-- Name: customer_fact_summary_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_fact_summary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_fact_summary_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_fact_summary_id_seq OWNED BY public.customer_fact_summary.id;


--
-- Name: customer_product_summary; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_product_summary (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    customer_id bigint NOT NULL,
    product_sku_id bigint NOT NULL,
    total_order_count bigint DEFAULT 0 NOT NULL,
    total_quantity bigint DEFAULT 0 NOT NULL,
    total_amount numeric(15,2) DEFAULT 0.00 NOT NULL,
    first_order_date date,
    last_order_date date,
    average_interval_days numeric(10,2) DEFAULT 0.00 NOT NULL,
    refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE ONLY public.customer_product_summary FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE customer_product_summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.customer_product_summary IS 'Customer-by-SKU purchase facts refreshed by the nightly reporting batch.';


--
-- Name: COLUMN customer_product_summary.total_order_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_product_summary.total_order_count IS 'Distinct effective sales order count containing this SKU.';


--
-- Name: COLUMN customer_product_summary.total_quantity; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_product_summary.total_quantity IS 'Total ordered quantity from effective sales orders.';


--
-- Name: COLUMN customer_product_summary.average_interval_days; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_product_summary.average_interval_days IS 'Average days between effective orders containing this SKU.';


--
-- Name: customer_product_summary_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_product_summary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_product_summary_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_product_summary_id_seq OWNED BY public.customer_product_summary.id;


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    address character varying(255),
    code character varying(50) NOT NULL,
    contact character varying(100),
    credit_limit numeric(15,2),
    email character varying(100),
    is_active boolean NOT NULL,
    is_deleted boolean NOT NULL,
    name character varying(200) NOT NULL,
    owner_id bigint,
    phone character varying(50),
    company_id bigint DEFAULT 1 NOT NULL,
    customer_type character varying(20) DEFAULT 'CLIENT'::character varying NOT NULL,
    source character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    external_customer_id character varying(64),
    normalized_email character varying(100),
    vat_rate numeric(5,2),
    secondary_tax_rate numeric(5,2),
    vat_number character varying(100),
    CONSTRAINT ck_customers_customer_type CHECK (((customer_type)::text = ANY ((ARRAY['CLIENT'::character varying, 'CONSUMER'::character varying])::text[]))),
    CONSTRAINT ck_customers_secondary_tax_rate CHECK (((secondary_tax_rate IS NULL) OR ((secondary_tax_rate >= (0)::numeric) AND (secondary_tax_rate <= (100)::numeric)))),
    CONSTRAINT ck_customers_source CHECK (((source)::text = ANY ((ARRAY['MANUAL'::character varying, 'CHANNEL'::character varying])::text[]))),
    CONSTRAINT ck_customers_vat_rate CHECK (((vat_rate IS NULL) OR ((vat_rate >= (0)::numeric) AND (vat_rate <= (100)::numeric))))
);

ALTER TABLE ONLY public.customers FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN customers.customer_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.customer_type IS 'CLIENT is governed customer master data; CONSUMER is a lightweight retail-channel identity.';


--
-- Name: COLUMN customers.source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.source IS 'Customer origin: MANUAL or CHANNEL.';


--
-- Name: COLUMN customers.external_customer_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.external_customer_id IS 'Channel-side customer identity used by the current Shopify retail resolver.';


--
-- Name: COLUMN customers.normalized_email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.normalized_email IS 'Lowercase trimmed email used for deterministic customer matching.';


--
-- Name: customers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customers_id_seq OWNED BY public.customers.id;


--
-- Name: domain_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.domain_outbox (
    id bigint NOT NULL,
    event_type character varying(80) NOT NULL,
    aggregate_type character varying(80) NOT NULL,
    aggregate_id character varying(80) NOT NULL,
    payload_json text NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    last_error character varying(1000),
    published_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.domain_outbox FORCE ROW LEVEL SECURITY;


--
-- Name: domain_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.domain_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: domain_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.domain_outbox_id_seq OWNED BY public.domain_outbox.id;


--
-- Name: email_verification_challenges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_verification_challenges (
    id bigint NOT NULL,
    signup_request_id bigint NOT NULL,
    code_hash character varying(128) NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_email_challenges_attempts CHECK (((attempt_count >= 0) AND (max_attempts > 0) AND (attempt_count <= max_attempts)))
);


--
-- Name: email_verification_challenges_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_verification_challenges_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_verification_challenges_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_verification_challenges_id_seq OWNED BY public.email_verification_challenges.id;


--
-- Name: emergency_stock_correction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.emergency_stock_correction (
    id bigint NOT NULL,
    correction_no character varying(40) NOT NULL,
    product_sku_id bigint CONSTRAINT emergency_stock_correction_product_id_not_null NOT NULL,
    location_id bigint NOT NULL,
    inventory_batch_id bigint,
    batch_code character varying(80),
    production_date date,
    expiry_date date,
    system_qty integer NOT NULL,
    counted_qty integer NOT NULL,
    adjustment_qty integer NOT NULL,
    reason_code character varying(80) NOT NULL,
    reason_detail character varying(1000),
    evidence_url character varying(500),
    related_sales_order_id bigint,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    submitted_by bigint,
    submitted_at timestamp with time zone,
    reviewed_by bigint,
    reviewed_at timestamp with time zone,
    approved_by bigint,
    approved_at timestamp with time zone,
    applied_by bigint,
    applied_at timestamp with time zone,
    review_comment character varying(500),
    approval_comment character varying(500),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.emergency_stock_correction FORCE ROW LEVEL SECURITY;


--
-- Name: emergency_stock_correction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.emergency_stock_correction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: emergency_stock_correction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.emergency_stock_correction_id_seq OWNED BY public.emergency_stock_correction.id;


--
-- Name: historical_test_data_archive_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_test_data_archive_audit (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    sales_order_id bigint NOT NULL,
    order_no character varying(30) NOT NULL,
    operator_id bigint NOT NULL,
    operator_username character varying(100) NOT NULL,
    reason character varying(500) NOT NULL,
    snapshot_fingerprint character varying(64) CONSTRAINT historical_test_data_archive_audi_snapshot_fingerprint_not_null NOT NULL,
    before_snapshot text NOT NULL,
    after_snapshot text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE ONLY public.historical_test_data_archive_audit FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE historical_test_data_archive_audit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.historical_test_data_archive_audit IS 'Append-only before/after evidence for guarded historical test data archives';


--
-- Name: historical_test_data_archive_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.historical_test_data_archive_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: historical_test_data_archive_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.historical_test_data_archive_audit_id_seq OWNED BY public.historical_test_data_archive_audit.id;


--
-- Name: historical_test_data_registry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_test_data_registry (
    id bigint NOT NULL,
    company_id bigint NOT NULL,
    sales_order_id bigint NOT NULL,
    order_no character varying(30) NOT NULL,
    source_version character varying(30) NOT NULL,
    registration_reason character varying(500) NOT NULL,
    registered_by character varying(100) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE ONLY public.historical_test_data_registry FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE historical_test_data_registry; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.historical_test_data_registry IS 'Immutable migration-managed identity evidence for explicitly approved historical test orders';


--
-- Name: historical_test_data_registry_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.historical_test_data_registry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: historical_test_data_registry_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.historical_test_data_registry_id_seq OWNED BY public.historical_test_data_registry.id;


--
-- Name: idempotency_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_request (
    id bigint NOT NULL,
    idempotency_key character varying(120) NOT NULL,
    request_hash character varying(128) NOT NULL,
    operation character varying(80) NOT NULL,
    status character varying(30) NOT NULL,
    response_status integer,
    response_body text,
    locked_until timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.idempotency_request FORCE ROW LEVEL SECURITY;


--
-- Name: idempotency_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.idempotency_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: idempotency_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.idempotency_request_id_seq OWNED BY public.idempotency_request.id;


--
-- Name: inbound_order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inbound_order_items (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    actual_qty integer NOT NULL,
    batch_code character varying(50),
    confirmed_qty integer,
    expiry_date date,
    external_batch_code character varying(100),
    plan_qty integer NOT NULL,
    production_date date,
    remark character varying(500),
    unit_cost numeric(10,2),
    inbound_order_id bigint NOT NULL,
    product_sku_id bigint CONSTRAINT inbound_order_items_product_id_not_null NOT NULL,
    target_location_id bigint,
    target_warehouse_id bigint,
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT inbound_order_items_actual_qty_check CHECK ((actual_qty >= 0)),
    CONSTRAINT inbound_order_items_confirmed_qty_check CHECK ((confirmed_qty >= 0)),
    CONSTRAINT inbound_order_items_plan_qty_check CHECK ((plan_qty >= 1))
);

ALTER TABLE ONLY public.inbound_order_items FORCE ROW LEVEL SECURITY;


--
-- Name: inbound_order_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inbound_order_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inbound_order_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inbound_order_items_id_seq OWNED BY public.inbound_order_items.id;


--
-- Name: inbound_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inbound_orders (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    applicant_id bigint NOT NULL,
    applicant_name character varying(100) NOT NULL,
    audit_log text,
    confirmation_comment character varying(500),
    confirmed_at timestamp(6) without time zone,
    confirmed_by bigint,
    expected_date date,
    gm_approval_comment character varying(500),
    gm_approved_at timestamp(6) without time zone,
    gm_approved_by bigint,
    order_no character varying(30) NOT NULL,
    received_at timestamp(6) without time zone,
    received_by bigint,
    remark character varying(500),
    status character varying(30) NOT NULL,
    total_actual_qty integer NOT NULL,
    total_confirmed_qty integer,
    total_plan_qty integer NOT NULL,
    supplier_id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT inbound_orders_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'APPROVED_PLAN'::character varying, 'AWAITING_RECEIVAL'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT inbound_orders_total_actual_qty_check CHECK ((total_actual_qty >= 0)),
    CONSTRAINT inbound_orders_total_confirmed_qty_check CHECK ((total_confirmed_qty >= 0)),
    CONSTRAINT inbound_orders_total_plan_qty_check CHECK ((total_plan_qty >= 0))
);

ALTER TABLE ONLY public.inbound_orders FORCE ROW LEVEL SECURITY;


--
-- Name: inbound_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inbound_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inbound_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inbound_orders_id_seq OWNED BY public.inbound_orders.id;


--
-- Name: integration_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_configs (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    access_token character varying(500),
    api_key character varying(255),
    is_active boolean,
    last_sync_at timestamp(6) without time zone,
    platform character varying(50) NOT NULL,
    store_url character varying(255) NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    client_id character varying(100),
    client_secret character varying(200),
    shopify_location_id character varying(50),
    retail_mode boolean DEFAULT false NOT NULL,
    canonical_store_identifier character varying(255) NOT NULL,
    CONSTRAINT ck_integration_configs_store_canonical CHECK ((((canonical_store_identifier)::text = lower(btrim((canonical_store_identifier)::text))) AND ((canonical_store_identifier)::text !~ '^https?://'::text) AND ((canonical_store_identifier)::text !~ '/$'::text) AND ((canonical_store_identifier)::text <> ''::text)))
);

ALTER TABLE ONLY public.integration_configs FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN integration_configs.access_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.integration_configs.access_token IS 'Legacy static Admin API token (shpat_...); optional. Used only when client_id/client_secret are absent';


--
-- Name: COLUMN integration_configs.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.integration_configs.client_id IS 'OAuth app Client ID (2026 dev-dashboard apps); used with client_secret to fetch short-lived access tokens at runtime';


--
-- Name: COLUMN integration_configs.client_secret; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.integration_configs.client_secret IS 'OAuth app Client Secret (shpss_...); also the webhook HMAC key. Stored plaintext for now - credential ladder upgrade planned (see ROADMAP P3)';


--
-- Name: COLUMN integration_configs.shopify_location_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.integration_configs.shopify_location_id IS 'Shopify location id for inventory_levels/set (P1-B4); auto-detectable via /detect-location';


--
-- Name: COLUMN integration_configs.retail_mode; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.integration_configs.retail_mode IS 'When true, automatic channel ingestion may create lightweight consumer records. Defaults off.';


--
-- Name: integration_configs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.integration_configs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: integration_configs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.integration_configs_id_seq OWNED BY public.integration_configs.id;


--
-- Name: inventory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    quantity integer NOT NULL,
    remark character varying(500),
    version bigint NOT NULL,
    location_id bigint NOT NULL,
    product_sku_id bigint CONSTRAINT inventory_product_id_not_null NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT inventory_quantity_check CHECK ((quantity >= 0))
);

ALTER TABLE ONLY public.inventory FORCE ROW LEVEL SECURITY;


--
-- Name: inventory_batch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_batch (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    active boolean NOT NULL,
    batch_code character varying(50) NOT NULL,
    entry_date timestamp(6) without time zone,
    expiry_date date NOT NULL,
    external_batch_code character varying(100),
    initial_quantity integer NOT NULL,
    production_date date,
    quantity integer NOT NULL,
    remark character varying(500),
    version integer DEFAULT 0 NOT NULL,
    location_id bigint,
    product_sku_id bigint CONSTRAINT inventory_batch_product_id_not_null NOT NULL,
    purchase_order_item_id bigint,
    location_code character varying(50) NOT NULL,
    reserved_quantity integer DEFAULT 0 NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_inventory_batch_non_negative CHECK (((quantity >= 0) AND (reserved_quantity >= 0))),
    CONSTRAINT chk_inventory_batch_reserved_quantity CHECK (((reserved_quantity >= 0) AND (reserved_quantity <= quantity)))
);

ALTER TABLE ONLY public.inventory_batch FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN inventory_batch.batch_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.inventory_batch.batch_code IS 'System batch code. Supports SPU-SKU-DATE format such as SPU-TEA-TEA-V44-1781052142-20260610.';


--
-- Name: COLUMN inventory_batch.purchase_order_item_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.inventory_batch.purchase_order_item_id IS 'Optional reference to purchase order item. NULL for inbound orders that are not linked to purchase orders.';


--
-- Name: COLUMN inventory_batch.location_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.inventory_batch.location_code IS 'V3.3: Location code string (redundant field for quick query)';


--
-- Name: COLUMN inventory_batch.company_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.inventory_batch.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';


--
-- Name: inventory_batch_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_batch_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_batch_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_batch_id_seq OWNED BY public.inventory_batch.id;


--
-- Name: inventory_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_id_seq OWNED BY public.inventory.id;


--
-- Name: inventory_reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_reservations (
    id bigint NOT NULL,
    sales_order_id bigint NOT NULL,
    sales_order_item_id bigint NOT NULL,
    inventory_batch_id bigint NOT NULL,
    product_sku_id bigint CONSTRAINT inventory_reservations_product_id_not_null NOT NULL,
    location_id bigint NOT NULL,
    reserved_qty integer NOT NULL,
    consumed_qty integer DEFAULT 0 NOT NULL,
    released_qty integer DEFAULT 0 NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    expires_at timestamp with time zone,
    source_type character varying(40) DEFAULT 'SALES_ORDER'::character varying NOT NULL,
    idempotency_key character varying(120),
    created_by bigint,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_inventory_reservation_consumed_qty CHECK ((consumed_qty >= 0)),
    CONSTRAINT chk_inventory_reservation_released_qty CHECK ((released_qty >= 0)),
    CONSTRAINT chk_inventory_reservation_reserved_qty CHECK ((reserved_qty > 0))
);

ALTER TABLE ONLY public.inventory_reservations FORCE ROW LEVEL SECURITY;


--
-- Name: inventory_reservations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_reservations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_reservations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_reservations_id_seq OWNED BY public.inventory_reservations.id;


--
-- Name: locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locations (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    enabled boolean NOT NULL,
    locationcode character varying(100),
    positionnumber character varying(10) NOT NULL,
    remark character varying(500),
    shelfnumber character varying(20) NOT NULL,
    warehousecode character varying(20) NOT NULL,
    zone character varying(20) NOT NULL,
    warehouse_id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    status character varying(20) DEFAULT 'EMPTY'::character varying NOT NULL,
    pos_x integer DEFAULT 0 NOT NULL,
    pos_y integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_locations_status CHECK (((status)::text = ANY ((ARRAY['EMPTY'::character varying, 'OCCUPIED'::character varying])::text[]))),
    CONSTRAINT locations_zone_check CHECK (((zone)::text = ANY ((ARRAY['ZONE_A'::character varying, 'ZONE_B'::character varying, 'ZONE_C'::character varying, 'ZONE_D'::character varying, 'ZONE_E'::character varying, 'ZONE_Q'::character varying, 'ZONE_R'::character varying])::text[])))
);

ALTER TABLE ONLY public.locations FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN locations.warehousecode; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.locations.warehousecode IS 'Warehouse code (redundant, synced from warehouse.code for performance)';


--
-- Name: COLUMN locations.warehouse_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.locations.warehouse_id IS 'Foreign key to warehouses table';


--
-- Name: locations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.locations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: locations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.locations_id_seq OWNED BY public.locations.id;


--
-- Name: outbound_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbound_tasks (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    actual_qty integer NOT NULL,
    assigned_batch_id bigint NOT NULL,
    location_id bigint NOT NULL,
    picked_at timestamp(6) without time zone,
    picked_by bigint,
    plan_qty integer NOT NULL,
    remark character varying(500),
    sales_order_id bigint NOT NULL,
    sales_order_item_id bigint NOT NULL,
    status character varying(30) NOT NULL,
    reservation_id bigint,
    company_id bigint DEFAULT 1 NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    archived_at timestamp without time zone,
    archived_by bigint,
    archive_reason character varying(500),
    CONSTRAINT ck_outbound_task_archive_fields CHECK (((((status)::text = 'VOIDED'::text) AND (archived_at IS NOT NULL) AND (archived_by IS NOT NULL) AND (archive_reason IS NOT NULL)) OR (((status)::text <> 'VOIDED'::text) AND (archived_at IS NULL) AND (archived_by IS NULL) AND (archive_reason IS NULL)))),
    CONSTRAINT outbound_tasks_actual_qty_check CHECK ((actual_qty >= 0)),
    CONSTRAINT outbound_tasks_plan_qty_check CHECK ((plan_qty >= 1)),
    CONSTRAINT outbound_tasks_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PICKING'::character varying, 'COMPLETED'::character varying, 'VOIDED'::character varying])::text[])))
);

ALTER TABLE ONLY public.outbound_tasks FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN outbound_tasks.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbound_tasks.status IS 'Task status: PENDING, PICKING, COMPLETED, VOIDED';


--
-- Name: COLUMN outbound_tasks.company_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbound_tasks.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';


--
-- Name: outbound_tasks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.outbound_tasks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: outbound_tasks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.outbound_tasks_id_seq OWNED BY public.outbound_tasks.id;


--
-- Name: pending_sku_mapping; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pending_sku_mapping (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    channel character varying(50) NOT NULL,
    store_identifier character varying(255),
    external_sku character varying(200) NOT NULL,
    external_title character varying(500),
    sample_unit_price numeric(12,2),
    sample_external_order_no character varying(100),
    occurrence_count integer DEFAULT 1 NOT NULL,
    last_seen_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    resolution character varying(20),
    resolved_by bigint,
    resolved_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE ONLY public.pending_sku_mapping FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE pending_sku_mapping; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pending_sku_mapping IS 'Unknown channel SKUs awaiting human mapping (replaces silent order drop); NOSKU:: prefix marks lines without any SKU';


--
-- Name: COLUMN pending_sku_mapping.occurrence_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pending_sku_mapping.occurrence_count IS 'How many times this SKU has blocked an order - ops priority signal';


--
-- Name: COLUMN pending_sku_mapping.resolution; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pending_sku_mapping.resolution IS 'MAPPED / VIRTUAL / IGNORED (set when status leaves PENDING)';


--
-- Name: pending_sku_mapping_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pending_sku_mapping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pending_sku_mapping_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pending_sku_mapping_id_seq OWNED BY public.pending_sku_mapping.id;


--
-- Name: plan_limits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan_limits (
    id bigint NOT NULL,
    plan_id bigint NOT NULL,
    limit_key character varying(80) NOT NULL,
    limit_kind character varying(20) NOT NULL,
    enabled_value boolean,
    numeric_value bigint,
    CONSTRAINT ck_plan_limits_kind CHECK (((limit_kind)::text = ANY ((ARRAY['ENTITLEMENT'::character varying, 'QUOTA'::character varying])::text[]))),
    CONSTRAINT ck_plan_limits_value CHECK (((((limit_kind)::text = 'ENTITLEMENT'::text) AND (enabled_value IS NOT NULL) AND (numeric_value IS NULL)) OR (((limit_kind)::text = 'QUOTA'::text) AND (enabled_value IS NULL) AND (numeric_value IS NOT NULL) AND (numeric_value >= 0))))
);


--
-- Name: TABLE plan_limits; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.plan_limits IS 'ENTITLEMENT controls features; QUOTA controls capacity.';


--
-- Name: plan_limits_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.plan_limits_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: plan_limits_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.plan_limits_id_seq OWNED BY public.plan_limits.id;


--
-- Name: platform_access_grants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_access_grants (
    id bigint NOT NULL,
    grantee_platform_user_id bigint NOT NULL,
    granted_by_platform_user_id bigint NOT NULL,
    capability character varying(20) NOT NULL,
    tenant_id bigint NOT NULL,
    dataset_code character varying(80) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoked_by_platform_user_id bigint,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT platform_access_grants_capability_check CHECK (((capability)::text = ANY ((ARRAY['READ'::character varying, 'EXPORT'::character varying])::text[]))),
    CONSTRAINT platform_access_grants_check CHECK ((expires_at > effective_from)),
    CONSTRAINT platform_access_grants_dataset_code_check CHECK (((dataset_code)::text = ANY ((ARRAY['users'::character varying, 'roles'::character varying, 'warehouses'::character varying, 'products'::character varying, 'inventory'::character varying, 'sales_orders'::character varying])::text[])))
);


--
-- Name: platform_access_grants_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_access_grants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_access_grants_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_access_grants_id_seq OWNED BY public.platform_access_grants.id;


--
-- Name: platform_admin_commands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_admin_commands (
    id bigint NOT NULL,
    actor_platform_user_id bigint NOT NULL,
    target_platform_user_id bigint NOT NULL,
    idempotency_key character varying(80) NOT NULL,
    action character varying(30) NOT NULL,
    request_fingerprint character(64) NOT NULL,
    status character varying(20) NOT NULL,
    safe_response_json text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT ck_platform_admin_command_action CHECK (((action)::text = ANY ((ARRAY['ADMIN_DISABLE'::character varying, 'ADMIN_ENABLE'::character varying, 'ADMIN_SESSIONS_REVOKE'::character varying, 'ADMIN_MFA_RESET'::character varying, 'ADMIN_ROLES_CHANGE'::character varying])::text[]))),
    CONSTRAINT ck_platform_admin_command_completion CHECK (((((status)::text = 'IN_PROGRESS'::text) AND (safe_response_json IS NULL) AND (completed_at IS NULL)) OR (((status)::text = 'SUCCEEDED'::text) AND (safe_response_json IS NOT NULL) AND (completed_at IS NOT NULL)))),
    CONSTRAINT ck_platform_admin_command_fingerprint CHECK ((request_fingerprint ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_platform_admin_command_status CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'SUCCEEDED'::character varying])::text[])))
);


--
-- Name: platform_admin_commands_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_admin_commands_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_admin_commands_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_admin_commands_id_seq OWNED BY public.platform_admin_commands.id;


--
-- Name: platform_admin_invitation_activations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_admin_invitation_activations (
    id bigint NOT NULL,
    invitation_id bigint NOT NULL,
    token_hash character varying(64) NOT NULL,
    password_hash character varying(255) NOT NULL,
    pending_secret_encrypted text CONSTRAINT platform_admin_invitation_act_pending_secret_encrypted_not_null NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_platform_admin_invitation_activation_attempts CHECK (((attempt_count >= 0) AND (attempt_count <= 5))),
    CONSTRAINT ck_platform_admin_invitation_activation_period CHECK ((expires_at > created_at)),
    CONSTRAINT ck_platform_admin_invitation_activation_token CHECK (((token_hash)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: platform_admin_invitation_activations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_admin_invitation_activations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_admin_invitation_activations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_admin_invitation_activations_id_seq OWNED BY public.platform_admin_invitation_activations.id;


--
-- Name: platform_admin_invitation_active_emails; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_admin_invitation_active_emails (
    normalized_email character varying(254) CONSTRAINT platform_admin_invitation_active_emai_normalized_email_not_null NOT NULL,
    invitation_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_platform_admin_invitation_active_email_normalized CHECK (((normalized_email)::text = lower(btrim((normalized_email)::text))))
);


--
-- Name: platform_admin_invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_admin_invitations (
    id bigint NOT NULL,
    token_hash character varying(64) NOT NULL,
    normalized_email character varying(254) NOT NULL,
    display_name character varying(100) NOT NULL,
    role_code character varying(50) NOT NULL,
    invited_by_platform_user_id bigint NOT NULL,
    reason character varying(500) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    accepted_at timestamp with time zone,
    accepted_platform_user_id bigint,
    revoked_at timestamp with time zone,
    revoked_by_platform_user_id bigint,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    invitation_type character varying(20) NOT NULL,
    CONSTRAINT ck_platform_admin_invitation_period CHECK ((expires_at > created_at)),
    CONSTRAINT ck_platform_admin_invitation_role CHECK (((((invitation_type)::text = 'SUPER_ADMIN'::text) AND ((role_code)::text = 'PLATFORM_SUPER_ADMIN'::text)) OR (((invitation_type)::text = 'ORDINARY_ADMIN'::text) AND ((role_code)::text = ANY ((ARRAY['PLATFORM_OPERATIONS_ADMIN'::character varying, 'PLATFORM_SECURITY_AUDITOR'::character varying])::text[]))))),
    CONSTRAINT ck_platform_admin_invitation_terminal CHECK (((accepted_at IS NULL) OR (revoked_at IS NULL))),
    CONSTRAINT ck_platform_admin_invitation_type CHECK (((invitation_type)::text = ANY ((ARRAY['SUPER_ADMIN'::character varying, 'ORDINARY_ADMIN'::character varying])::text[])))
);


--
-- Name: platform_admin_invitations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_admin_invitations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_admin_invitations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_admin_invitations_id_seq OWNED BY public.platform_admin_invitations.id;


--
-- Name: platform_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_audit_logs (
    id bigint NOT NULL,
    platform_user_id bigint NOT NULL,
    target_tenant_id bigint,
    action character varying(30) NOT NULL,
    resource_type character varying(80),
    resource_id character varying(100),
    request_id character varying(100),
    request_ip character varying(64),
    user_agent character varying(500),
    result character varying(30) NOT NULL,
    detail_json text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    target_platform_user_id bigint,
    CONSTRAINT ck_platform_audit_action CHECK (((action)::text = ANY ((ARRAY['READ'::character varying, 'EXPORT'::character varying, 'WRITE'::character varying, 'DELETE'::character varying, 'AUDIT_READ'::character varying, 'OPERATION_REQUESTED'::character varying, 'OPERATION_EXECUTED'::character varying, 'MFA_ENROLLED'::character varying, 'MFA_VERIFIED'::character varying, 'MFA_FAILED'::character varying, 'MFA_RECOVERY_USED'::character varying, 'MFA_RECOVERY_REGENERATED'::character varying, 'MFA_RESET'::character varying, 'ACCESS_GRANTED'::character varying, 'ACCESS_REVOKED'::character varying, 'SESSIONS_REVOKED'::character varying, 'ADMIN_INVITED'::character varying, 'ADMIN_INVITATION_REVOKED'::character varying, 'ADMIN_INVITATION_ACCEPTED'::character varying, 'ADMIN_DIRECTORY_READ'::character varying, 'ADMIN_DISABLED'::character varying, 'ADMIN_ENABLED'::character varying, 'ADMIN_SESSIONS_REVOKED'::character varying, 'ADMIN_ROLES_CHANGED'::character varying])::text[])))
);


--
-- Name: platform_audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_audit_logs_id_seq OWNED BY public.platform_audit_logs.id;


--
-- Name: platform_export_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_export_jobs (
    id bigint NOT NULL,
    public_id character varying(36) NOT NULL,
    platform_user_id bigint NOT NULL,
    target_tenant_id bigint NOT NULL,
    status character varying(20) NOT NULL,
    requested_resource character varying(80) NOT NULL,
    file_path character varying(1000),
    file_sha256 character varying(64),
    record_count bigint,
    error_code character varying(80),
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    CONSTRAINT ck_platform_export_jobs_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: platform_export_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_export_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_export_jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_export_jobs_id_seq OWNED BY public.platform_export_jobs.id;


--
-- Name: platform_mfa_challenges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_mfa_challenges (
    id bigint NOT NULL,
    token_hash character varying(64) NOT NULL,
    platform_user_id bigint NOT NULL,
    purpose character varying(40) NOT NULL,
    pending_secret_encrypted text,
    attempt_count integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    target_platform_user_id bigint,
    action_context_hash character(64),
    target_security_version bigint,
    CONSTRAINT ck_platform_mfa_challenge_attempts CHECK (((attempt_count >= 0) AND (attempt_count <= 5))),
    CONSTRAINT ck_platform_mfa_challenge_context_hash CHECK (((action_context_hash IS NULL) OR (action_context_hash ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT ck_platform_mfa_challenge_purpose CHECK (((purpose)::text = ANY ((ARRAY['ENROLL'::character varying, 'VERIFY'::character varying, 'LOGOUT_ALL'::character varying, 'RECOVERY_REGEN'::character varying, 'ADMIN_MFA_RESET'::character varying, 'ADMIN_INVITE'::character varying, 'ADMIN_STATUS_CHANGE'::character varying, 'ADMIN_SESSIONS_REVOKE'::character varying, 'ADMIN_ROLES_CHANGE'::character varying])::text[]))),
    CONSTRAINT ck_platform_mfa_challenge_target_version CHECK (((target_security_version IS NULL) OR (target_security_version >= 1)))
);


--
-- Name: platform_mfa_challenges_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_mfa_challenges_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_mfa_challenges_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_mfa_challenges_id_seq OWNED BY public.platform_mfa_challenges.id;


--
-- Name: platform_operation_authorizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_operation_authorizations (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    operation_type character varying(20) NOT NULL,
    resource_scope character varying(100) NOT NULL,
    approved_by_tenant_user_id bigint,
    approved_at timestamp with time zone,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    reason character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    public_id character varying(36) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    requested_by_platform_user_id bigint,
    resource_type character varying(40) NOT NULL,
    resource_id character varying(100) NOT NULL,
    request_payload_json text NOT NULL,
    request_fingerprint character varying(64) NOT NULL,
    consumed_at timestamp with time zone,
    executed_by_platform_user_id bigint,
    execution_key character varying(80),
    CONSTRAINT ck_platform_operation_auth_consumed CHECK (((consumed_at IS NULL) OR (((status)::text = ANY ((ARRAY['CONSUMED'::character varying, 'FAILED'::character varying])::text[])) AND (executed_by_platform_user_id IS NOT NULL)))),
    CONSTRAINT ck_platform_operation_auth_expiry CHECK ((expires_at > approved_at)),
    CONSTRAINT ck_platform_operation_auth_payload_json CHECK ((request_payload_json IS NOT NULL)),
    CONSTRAINT ck_platform_operation_auth_resource_type CHECK (((resource_type)::text = ANY ((ARRAY['product'::character varying, 'customer'::character varying, 'legacy'::character varying])::text[]))),
    CONSTRAINT ck_platform_operation_auth_revoked CHECK (((revoked_at IS NULL) OR (revoked_at >= approved_at))),
    CONSTRAINT ck_platform_operation_auth_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'CONSUMED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_platform_operation_auth_type CHECK (((operation_type)::text = ANY ((ARRAY['WRITE'::character varying, 'DELETE'::character varying])::text[])))
);


--
-- Name: TABLE platform_operation_authorizations; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.platform_operation_authorizations IS 'One-time fixed-scope company approval for platform write/delete; not a general database permission';


--
-- Name: platform_operation_authorizations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_operation_authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_operation_authorizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_operation_authorizations_id_seq OWNED BY public.platform_operation_authorizations.id;


--
-- Name: platform_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_roles (
    id bigint NOT NULL,
    role_code character varying(50) NOT NULL,
    display_name character varying(100) NOT NULL,
    CONSTRAINT ck_platform_roles_code CHECK (((role_code)::text = ANY ((ARRAY['PLATFORM_SUPER_ADMIN'::character varying, 'PLATFORM_OPERATIONS_ADMIN'::character varying, 'PLATFORM_SECURITY_AUDITOR'::character varying, 'PLATFORM_TENANT_READ'::character varying, 'PLATFORM_TENANT_EXPORT'::character varying, 'PLATFORM_TENANT_WRITE'::character varying, 'PLATFORM_TENANT_DELETE'::character varying])::text[])))
);


--
-- Name: platform_roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_roles_id_seq OWNED BY public.platform_roles.id;


--
-- Name: platform_user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_user_roles (
    id bigint NOT NULL,
    platform_user_id bigint NOT NULL,
    platform_role_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: platform_user_roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_user_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_user_roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_user_roles_id_seq OWNED BY public.platform_user_roles.id;


--
-- Name: platform_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_users (
    id bigint NOT NULL,
    normalized_email character varying(254) NOT NULL,
    password_hash character varying(255) NOT NULL,
    display_name character varying(100) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    security_version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    mfa_enabled boolean DEFAULT false NOT NULL,
    mfa_secret_encrypted text,
    recovery_code_hashes_json text,
    mfa_failed_attempts integer DEFAULT 0 NOT NULL,
    mfa_locked_until timestamp with time zone,
    mfa_enrolled_at timestamp with time zone,
    CONSTRAINT ck_platform_users_email_normalized CHECK (((normalized_email)::text = lower(btrim((normalized_email)::text)))),
    CONSTRAINT ck_platform_users_mfa_attempts CHECK (((mfa_failed_attempts >= 0) AND (mfa_failed_attempts <= 5))),
    CONSTRAINT ck_platform_users_security_version CHECK ((security_version > 0))
);


--
-- Name: TABLE platform_users; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.platform_users IS 'Platform-only accounts. They never belong to a company and must not be returned by company IAM queries.';


--
-- Name: platform_users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_users_id_seq OWNED BY public.platform_users.id;


--
-- Name: product_sku_code_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.product_sku_code_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: product_skus; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_skus (
    id bigint CONSTRAINT products_id_not_null NOT NULL,
    created_at timestamp(6) with time zone CONSTRAINT products_created_at_not_null NOT NULL,
    updated_at timestamp(6) with time zone CONSTRAINT products_updated_at_not_null NOT NULL,
    barcode character varying(50) CONSTRAINT products_barcode_not_null NOT NULL,
    description character varying(1000),
    enabled boolean CONSTRAINT products_enabled_not_null NOT NULL,
    leadtime integer CONSTRAINT products_leadtime_not_null NOT NULL,
    minstock integer CONSTRAINT products_minstock_not_null NOT NULL,
    name character varying(200) CONSTRAINT products_name_not_null NOT NULL,
    specification character varying(100),
    supplier character varying(200),
    unitprice numeric(10,2) CONSTRAINT products_unitprice_not_null NOT NULL,
    pack_unit character varying(50) DEFAULT 'Box'::character varying,
    conversion_rate integer DEFAULT 1,
    specs character varying(500),
    product_id bigint CONSTRAINT products_spu_id_not_null NOT NULL,
    sku_name character varying(100) CONSTRAINT products_sku_name_not_null NOT NULL,
    min_sales_price numeric(10,2),
    is_deleted boolean DEFAULT false CONSTRAINT products_is_deleted_not_null NOT NULL,
    near_expiry_days integer DEFAULT 90 CONSTRAINT products_near_expiry_days_not_null NOT NULL,
    per_pack_qty integer DEFAULT 1 CONSTRAINT products_per_pack_qty_not_null NOT NULL,
    safety_stock integer DEFAULT 0 CONSTRAINT products_safety_stock_not_null NOT NULL,
    version integer DEFAULT 0 CONSTRAINT products_version_not_null NOT NULL,
    company_id bigint DEFAULT 1 CONSTRAINT products_company_id_not_null NOT NULL,
    batch_tracking_mode character varying(30) DEFAULT 'PRINTED_LABEL'::character varying CONSTRAINT products_batch_tracking_mode_not_null NOT NULL,
    sku_code character varying(50) NOT NULL,
    CONSTRAINT chk_product_skus_batch_tracking_mode CHECK (((batch_tracking_mode)::text = ANY ((ARRAY['PRINTED_LABEL'::character varying, 'LOCATION_VISUAL'::character varying])::text[]))),
    CONSTRAINT products_leadtime_check CHECK ((leadtime >= 0)),
    CONSTRAINT products_minstock_check CHECK ((minstock >= 0))
);

ALTER TABLE ONLY public.product_skus FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE product_skus; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.product_skus IS 'ProductSku master; inventory, procurement, sales and fulfillment unit';


--
-- Name: COLUMN product_skus.specs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_skus.specs IS 'Specification description (JSON or text)';


--
-- Name: COLUMN product_skus.product_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_skus.product_id IS 'Foreign key to product_spu (Product Family)';


--
-- Name: COLUMN product_skus.sku_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_skus.sku_name IS 'SKU-specific name (short identifier)';


--
-- Name: COLUMN product_skus.sku_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_skus.sku_code IS 'Stable internal SKU code, independent from barcode and immutable';


--
-- Name: product_skus_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.product_skus_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: product_skus_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.product_skus_id_seq OWNED BY public.product_skus.id;


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    id bigint CONSTRAINT product_spu_id_not_null NOT NULL,
    product_code character varying(50) CONSTRAINT product_spu_spu_code_not_null NOT NULL,
    product_name character varying(200) CONSTRAINT product_spu_spu_name_not_null NOT NULL,
    brand character varying(100),
    description text,
    enabled boolean DEFAULT true CONSTRAINT product_spu_enabled_not_null NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT product_spu_created_at_not_null NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT product_spu_updated_at_not_null NOT NULL,
    company_id bigint DEFAULT 1 CONSTRAINT product_spu_company_id_not_null NOT NULL,
    category_id bigint NOT NULL,
    version integer DEFAULT 0 CONSTRAINT products_version_not_null1 NOT NULL
);

ALTER TABLE ONLY public.products FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE products; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.products IS 'Product master (SPU); categorized business product family';


--
-- Name: COLUMN products.category_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.products.category_id IS 'Required level-2 category; enforced by application service';


--
-- Name: products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.products ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    actual_entry_date timestamp(6) without time zone,
    audit_log text,
    expected_date date,
    operator_id bigint,
    operator_name character varying(100),
    po_number character varying(20) NOT NULL,
    remark character varying(500),
    status character varying(30) NOT NULL,
    supplier character varying(200) NOT NULL,
    total_cost numeric(10,2),
    total_quantity integer NOT NULL,
    is_deleted boolean NOT NULL,
    supplier_reliability_score numeric(5,2) DEFAULT 100.00 NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    supplier_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT purchase_order_status_check CHECK (((status)::text = ANY ((ARRAY['ORDERING'::character varying, 'IN_TRANSIT'::character varying, 'PARTIALLY_RECEIVED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);

ALTER TABLE ONLY public.purchase_order FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN purchase_order.supplier_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.purchase_order.supplier_id IS 'Supplier master reference. The supplier text column remains the order-time name snapshot.';


--
-- Name: COLUMN purchase_order.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.purchase_order.version IS 'Optimistic-lock version used to prevent concurrent ORDERING edits from overwriting each other';


--
-- Name: purchase_order_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_order_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_order_id_seq OWNED BY public.purchase_order.id;


--
-- Name: purchase_order_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_item (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    expiry_date date,
    external_batch_code character varying(100),
    ordered_quantity integer NOT NULL,
    production_date date,
    received_quantity integer NOT NULL,
    remark character varying(500),
    unit_cost numeric(10,2),
    product_sku_id bigint CONSTRAINT purchase_order_item_product_id_not_null NOT NULL,
    purchase_order_id bigint NOT NULL,
    product_price_snapshot numeric(10,2),
    committed_qty integer DEFAULT 0 NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.purchase_order_item FORCE ROW LEVEL SECURITY;


--
-- Name: purchase_order_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_order_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_order_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_order_item_id_seq OWNED BY public.purchase_order_item.id;


--
-- Name: sales_daily_summary; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_daily_summary (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    summary_date date NOT NULL,
    total_order_count bigint DEFAULT 0 NOT NULL,
    total_amount numeric(15,2) DEFAULT 0.00 NOT NULL,
    refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    draft_count bigint DEFAULT 0 NOT NULL,
    pending_approval_count bigint DEFAULT 0 NOT NULL,
    approved_awaiting_shipment_count bigint DEFAULT 0 NOT NULL,
    shipped_count bigint DEFAULT 0 NOT NULL,
    rejected_count bigint DEFAULT 0 NOT NULL,
    cancelled_count bigint DEFAULT 0 NOT NULL,
    voided_count bigint DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY public.sales_daily_summary FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE sales_daily_summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sales_daily_summary IS 'Daily sales reporting fact buffer refreshed by nightly batch job.';


--
-- Name: COLUMN sales_daily_summary.summary_date; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_daily_summary.summary_date IS 'Sales order creation date in backend UTC calendar.';


--
-- Name: COLUMN sales_daily_summary.total_order_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_daily_summary.total_order_count IS 'Effective daily sales order count excluding rejected, cancelled, and voided orders.';


--
-- Name: COLUMN sales_daily_summary.total_amount; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_daily_summary.total_amount IS 'Effective daily sales amount excluding rejected, cancelled, and voided orders.';


--
-- Name: COLUMN sales_daily_summary.refreshed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_daily_summary.refreshed_at IS 'Last batch refresh timestamp.';


--
-- Name: sales_daily_summary_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_daily_summary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_daily_summary_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_daily_summary_id_seq OWNED BY public.sales_daily_summary.id;


--
-- Name: sales_order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_order_items (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    product_sku_id bigint CONSTRAINT sales_order_items_product_id_not_null NOT NULL,
    product_price_snapshot numeric(10,2),
    quantity integer NOT NULL,
    reject_near_expiry boolean NOT NULL,
    remark character varying(500),
    sales_order_id bigint NOT NULL,
    specified_batch_ids text,
    subtotal numeric(15,2) NOT NULL,
    unit_price numeric(10,2) NOT NULL,
    requested_qty integer DEFAULT 0 NOT NULL,
    allocated_qty integer DEFAULT 0 NOT NULL,
    shipped_qty integer DEFAULT 0 NOT NULL,
    backorder_qty integer DEFAULT 0 NOT NULL,
    cancelled_qty integer DEFAULT 0 NOT NULL,
    fulfillment_status character varying(40) DEFAULT 'UNALLOCATED'::character varying NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT sales_order_items_quantity_check CHECK ((quantity >= 1))
);

ALTER TABLE ONLY public.sales_order_items FORCE ROW LEVEL SECURITY;


--
-- Name: sales_order_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_order_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_order_items_id_seq OWNED BY public.sales_order_items.id;


--
-- Name: sales_order_shipments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_order_shipments (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    sales_order_id bigint NOT NULL,
    tracking_no character varying(100) NOT NULL,
    carrier character varying(50),
    tracking_url character varying(500),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    shipped_at timestamp with time zone,
    created_by bigint,
    created_by_name character varying(100),
    remark character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    voided_by bigint,
    voided_by_name character varying(100),
    voided_at timestamp without time zone,
    CONSTRAINT ck_sales_order_shipment_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'VOIDED'::character varying])::text[])))
);

ALTER TABLE ONLY public.sales_order_shipments FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE sales_order_shipments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sales_order_shipments IS 'Internal shipment and tracking records; one sales order can have multiple shipments';


--
-- Name: COLUMN sales_order_shipments.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_order_shipments.status IS 'ACTIVE or VOIDED; records are retained for audit';


--
-- Name: COLUMN sales_order_shipments.voided_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_order_shipments.voided_by IS 'User ID that voided the shipment.';


--
-- Name: COLUMN sales_order_shipments.voided_by_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_order_shipments.voided_by_name IS 'Username snapshot for the shipment void action.';


--
-- Name: COLUMN sales_order_shipments.voided_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_order_shipments.voided_at IS 'Timestamp of the shipment void action.';


--
-- Name: sales_order_shipments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_order_shipments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_shipments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_order_shipments_id_seq OWNED BY public.sales_order_shipments.id;


--
-- Name: sales_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_orders (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    applicant_id bigint NOT NULL,
    applicant_name character varying(100) NOT NULL,
    approved_at timestamp(6) without time zone,
    audit_log text,
    channel character varying(50) DEFAULT 'MANUAL'::character varying NOT NULL,
    customer_id bigint NOT NULL,
    external_order_id character varying(100),
    external_order_no character varying(100),
    order_no character varying(30) NOT NULL,
    review_comment character varying(500),
    review_reason character varying(500),
    reviewed_at timestamp(6) without time zone,
    reviewed_by bigint,
    shipped_at timestamp(6) without time zone,
    status character varying(30) NOT NULL,
    total_amount numeric(15,2),
    commercial_status character varying(40) DEFAULT 'DRAFT'::character varying NOT NULL,
    fulfillment_status character varying(40) DEFAULT 'UNALLOCATED'::character varying NOT NULL,
    allocation_policy character varying(30) DEFAULT 'FULL_ONLY'::character varying NOT NULL,
    requested_ship_date date,
    promised_ship_date date,
    shortage_reason character varying(500),
    fulfillment_version bigint DEFAULT 0 NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    consignee_name character varying(200),
    consignee_phone character varying(50),
    ship_address1 character varying(255),
    ship_address2 character varying(255),
    ship_city character varying(100),
    ship_province character varying(100),
    ship_zip character varying(30),
    ship_country_code character varying(10),
    CONSTRAINT sales_orders_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED_AWAITING_SHIPMENT'::character varying, 'SHIPPED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying, 'VOIDED'::character varying])::text[])))
);

ALTER TABLE ONLY public.sales_orders FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN sales_orders.company_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';


--
-- Name: COLUMN sales_orders.consignee_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.consignee_name IS 'Order-time shipping addressee snapshot; never backfilled into the customer master.';


--
-- Name: COLUMN sales_orders.consignee_phone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.consignee_phone IS 'Order-time shipping phone snapshot.';


--
-- Name: COLUMN sales_orders.ship_address1; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.ship_address1 IS 'Order-time primary shipping address snapshot.';


--
-- Name: COLUMN sales_orders.ship_address2; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.ship_address2 IS 'Order-time secondary shipping address snapshot.';


--
-- Name: COLUMN sales_orders.ship_city; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.ship_city IS 'Order-time shipping city snapshot.';


--
-- Name: COLUMN sales_orders.ship_province; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.ship_province IS 'Order-time shipping province/state snapshot.';


--
-- Name: COLUMN sales_orders.ship_zip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.ship_zip IS 'Order-time shipping postal-code snapshot.';


--
-- Name: COLUMN sales_orders.ship_country_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sales_orders.ship_country_code IS 'Order-time ISO country-code snapshot.';


--
-- Name: sales_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_orders_id_seq OWNED BY public.sales_orders.id;


--
-- Name: session_handoff_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_handoff_codes (
    id bigint NOT NULL,
    signup_request_id bigint NOT NULL,
    code_hash character varying(128) NOT NULL,
    tenant_id bigint NOT NULL,
    identity_id bigint NOT NULL,
    tenant_user_id bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_session_handoff_consumed CHECK (((consumed_at IS NULL) OR (consumed_at >= created_at))),
    CONSTRAINT ck_session_handoff_expiry CHECK ((expires_at > created_at))
);


--
-- Name: TABLE session_handoff_codes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.session_handoff_codes IS 'Opaque 60-second single-use browser handoff; never contains a JWT.';


--
-- Name: session_handoff_codes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.session_handoff_codes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: session_handoff_codes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.session_handoff_codes_id_seq OWNED BY public.session_handoff_codes.id;


--
-- Name: signup_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.signup_requests (
    id bigint NOT NULL,
    public_id character varying(36) NOT NULL,
    idempotency_key character varying(80) NOT NULL,
    normalized_email character varying(254) NOT NULL,
    requested_plan_code character varying(30) NOT NULL,
    company_name character varying(160),
    slug character varying(30),
    status character varying(30) DEFAULT 'EMAIL_PENDING'::character varying NOT NULL,
    tenant_id bigint,
    last_error_code character varying(80),
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    first_name character varying(100),
    last_name character varying(100),
    password_hash character varying(255),
    request_fingerprint character varying(128),
    details_fingerprint character varying(128),
    identity_id bigint,
    CONSTRAINT ck_signup_requests_email_normalized CHECK (((normalized_email)::text = lower(btrim((normalized_email)::text)))),
    CONSTRAINT ck_signup_requests_plan CHECK (((requested_plan_code)::text = ANY ((ARRAY['FREE'::character varying, 'TRIAL'::character varying])::text[]))),
    CONSTRAINT ck_signup_requests_slug CHECK (((slug IS NULL) OR (((slug)::text = lower(btrim((slug)::text))) AND ((slug)::text ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$'::text) AND ((slug)::text <> ALL ((ARRAY['www'::character varying, 'app'::character varying, 'api'::character varying, 'platform'::character varying, 'admin'::character varying, 'support'::character varying, 'mail'::character varying, 'static'::character varying, 'cdn'::character varying, 'status'::character varying, 'help'::character varying])::text[]))))),
    CONSTRAINT ck_signup_requests_status CHECK (((status)::text = ANY ((ARRAY['EMAIL_PENDING'::character varying, 'EMAIL_VERIFIED'::character varying, 'DETAILS_COMPLETED'::character varying, 'PROVISIONING'::character varying, 'ACTIVE'::character varying, 'PROVISIONING_FAILED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: TABLE signup_requests; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.signup_requests IS 'Global pre-tenant signup state; never inherits a default company_id.';


--
-- Name: signup_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.signup_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: signup_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.signup_requests_id_seq OWNED BY public.signup_requests.id;


--
-- Name: stock_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_transactions (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    locationcode character varying(100) NOT NULL,
    operatorid bigint,
    operatorname character varying(100),
    productbarcode character varying(50) NOT NULL,
    productname character varying(200) NOT NULL,
    quantity integer NOT NULL,
    quantityafter integer NOT NULL,
    quantitybefore integer NOT NULL,
    remark character varying(1000),
    sourceorderid character varying(50) NOT NULL,
    sourcetype character varying(50) NOT NULL,
    transactiontype character varying(20) NOT NULL,
    location_id bigint NOT NULL,
    product_sku_id bigint CONSTRAINT stock_transactions_product_id_not_null NOT NULL,
    reason_code character varying(50),
    remarks character varying(500),
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT stock_transactions_sourcetype_check CHECK (((sourcetype)::text = ANY ((ARRAY['PURCHASE_IN'::character varying, 'INBOUND_IN'::character varying, 'SALE_OUT'::character varying, 'RETURN_IN'::character varying, 'PRODUCTION_OUT'::character varying, 'PRODUCTION_IN'::character varying, 'TRANSFER_OUT'::character varying, 'TRANSFER_IN'::character varying, 'INVENTORY_GAIN'::character varying, 'INVENTORY_LOSS'::character varying, 'SCRAP_OUT'::character varying, 'GIFT_OUT'::character varying, 'SAMPLE_OUT'::character varying, 'MANUAL_ADJUST'::character varying])::text[]))),
    CONSTRAINT stock_transactions_transactiontype_check CHECK (((transactiontype)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying, 'ADJUST'::character varying])::text[])))
);

ALTER TABLE ONLY public.stock_transactions FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN stock_transactions.company_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.stock_transactions.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';


--
-- Name: stock_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stock_transactions_id_seq OWNED BY public.stock_transactions.id;


--
-- Name: stocktake_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stocktake_items (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    batch_id bigint NOT NULL,
    counted_at timestamp(6) without time zone,
    counted_by bigint,
    counted_by_name character varying(100),
    counted_qty integer,
    difference_qty integer NOT NULL,
    is_counted boolean NOT NULL,
    location_id bigint NOT NULL,
    product_sku_id bigint CONSTRAINT stocktake_items_product_id_not_null NOT NULL,
    remark character varying(500),
    snapshot_qty integer NOT NULL,
    task_id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT stocktake_items_counted_qty_check CHECK ((counted_qty >= 0)),
    CONSTRAINT stocktake_items_snapshot_qty_check CHECK ((snapshot_qty >= 0))
);

ALTER TABLE ONLY public.stocktake_items FORCE ROW LEVEL SECURITY;


--
-- Name: stocktake_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stocktake_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stocktake_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stocktake_items_id_seq OWNED BY public.stocktake_items.id;


--
-- Name: stocktake_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stocktake_tasks (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    counted_items integer NOT NULL,
    created_by bigint NOT NULL,
    created_by_name character varying(100) NOT NULL,
    cycle_type character varying(20) NOT NULL,
    difference_items integer NOT NULL,
    review_comment character varying(500),
    reviewed_at timestamp(6) without time zone,
    reviewed_by bigint,
    reviewed_by_name character varying(100),
    snapshot_time timestamp(6) without time zone NOT NULL,
    status character varying(20) NOT NULL,
    task_no character varying(30) NOT NULL,
    total_items integer NOT NULL,
    warehouse_id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    CONSTRAINT stocktake_tasks_counted_items_check CHECK ((counted_items >= 0)),
    CONSTRAINT stocktake_tasks_cycle_type_check CHECK (((cycle_type)::text = ANY ((ARRAY['MONTHLY'::character varying, 'QUARTERLY'::character varying, 'ANNUAL'::character varying, 'ADHOC'::character varying])::text[]))),
    CONSTRAINT stocktake_tasks_difference_items_check CHECK ((difference_items >= 0)),
    CONSTRAINT stocktake_tasks_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'COUNTING'::character varying, 'REVIEWING'::character varying, 'COMPLETED'::character varying])::text[]))),
    CONSTRAINT stocktake_tasks_total_items_check CHECK ((total_items >= 0))
);

ALTER TABLE ONLY public.stocktake_tasks FORCE ROW LEVEL SECURITY;


--
-- Name: stocktake_tasks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stocktake_tasks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stocktake_tasks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stocktake_tasks_id_seq OWNED BY public.stocktake_tasks.id;


--
-- Name: subscription_plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_plans (
    id bigint NOT NULL,
    plan_code character varying(30) NOT NULL,
    display_name character varying(100) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    trial_days integer,
    fallback_plan_code character varying(30),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_subscription_plans_code CHECK (((plan_code)::text = ANY ((ARRAY['FREE'::character varying, 'TRIAL'::character varying])::text[]))),
    CONSTRAINT ck_subscription_plans_trial_days CHECK (((trial_days IS NULL) OR (trial_days > 0)))
);


--
-- Name: subscription_plans_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subscription_plans_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subscription_plans_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subscription_plans_id_seq OWNED BY public.subscription_plans.id;


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    address character varying(255),
    code character varying(50) NOT NULL,
    contact character varying(100),
    email character varying(100),
    is_active boolean NOT NULL,
    name character varying(200) NOT NULL,
    phone character varying(50),
    company_id bigint DEFAULT 1 NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    remark character varying(500)
);

ALTER TABLE ONLY public.suppliers FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN suppliers.is_deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.suppliers.is_deleted IS 'Logical deletion flag. Deleted suppliers are hidden from master-data APIs.';


--
-- Name: COLUMN suppliers.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.suppliers.remark IS 'Supplier master-data remark.';


--
-- Name: suppliers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.suppliers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: suppliers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.suppliers_id_seq OWNED BY public.suppliers.id;


--
-- Name: sys_approval_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_approval_template (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    template_code character varying(50) NOT NULL,
    template_name character varying(100) NOT NULL,
    object_type character varying(50) NOT NULL,
    approval_mode character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    system_defined boolean DEFAULT true NOT NULL,
    version_no integer DEFAULT 1 NOT NULL,
    description character varying(500),
    config_json text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_approval_template_mode CHECK (((approval_mode)::text = ANY ((ARRAY['SIMPLE'::character varying, 'FORMAL'::character varying])::text[]))),
    CONSTRAINT ck_approval_template_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT ck_approval_template_version CHECK ((version_no > 0))
);

ALTER TABLE ONLY public.sys_approval_template FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE sys_approval_template; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_approval_template IS 'Protected approval templates. FORMAL mode is reserved but not implemented in this release.';


--
-- Name: sys_approval_template_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_approval_template_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_approval_template_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_approval_template_id_seq OWNED BY public.sys_approval_template.id;


--
-- Name: sys_excel_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_excel_templates (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    description character varying(500),
    file_url character varying(500) NOT NULL,
    is_active boolean NOT NULL,
    template_name character varying(100) NOT NULL,
    template_type character varying(50) NOT NULL,
    version character varying(20),
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.sys_excel_templates FORCE ROW LEVEL SECURITY;


--
-- Name: sys_excel_templates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_excel_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_excel_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_excel_templates_id_seq OWNED BY public.sys_excel_templates.id;


--
-- Name: sys_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_permission (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    data_scope character varying(20),
    description character varying(500),
    http_method character varying(10),
    menu_icon character varying(50),
    menu_url character varying(200),
    parent_id bigint,
    permission_code character varying(100) NOT NULL,
    permission_name character varying(100) NOT NULL,
    permission_type character varying(20) NOT NULL,
    resource_path character varying(200),
    sort_order integer NOT NULL,
    status character varying(20) NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    risk_level character varying(20) DEFAULT 'NORMAL'::character varying NOT NULL,
    custom_assignable boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_sys_permission_critical_not_custom CHECK ((((risk_level)::text <> 'CRITICAL'::text) OR (custom_assignable = false))),
    CONSTRAINT ck_sys_permission_reserved_policy CHECK (((NOT (((permission_code)::text = ANY ((ARRAY['menu:system'::character varying, 'system:admin'::character varying])::text[])) OR ((permission_code)::text ~~ 'system:role:%'::text) OR ((permission_code)::text ~~ 'system:permission:%'::text) OR ((permission_code)::text ~~ 'system:user:%'::text))) OR (((risk_level)::text = 'CRITICAL'::text) AND (custom_assignable = false)))),
    CONSTRAINT ck_sys_permission_risk_level CHECK (((risk_level)::text = ANY ((ARRAY['NORMAL'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])))
);

ALTER TABLE ONLY public.sys_permission FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN sys_permission.risk_level; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_permission.risk_level IS 'Permission risk classification: NORMAL, HIGH (warning/reason required), CRITICAL (not custom assignable)';


--
-- Name: COLUMN sys_permission.custom_assignable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_permission.custom_assignable IS 'Whether the permission may be assigned to CUSTOM roles.';


--
-- Name: sys_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_permission_id_seq OWNED BY public.sys_permission.id;


--
-- Name: sys_permission_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_permission_request (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    target_user_id bigint NOT NULL,
    target_username character varying(100) NOT NULL,
    requested_role_id bigint NOT NULL,
    requested_role_code character varying(50) NOT NULL,
    requested_role_name character varying(100) NOT NULL,
    request_reason character varying(500),
    status character varying(30) DEFAULT 'PENDING_REVIEW'::character varying NOT NULL,
    high_risk_permission_count integer DEFAULT 0 NOT NULL,
    permission_codes character varying(4000),
    snapshot_fingerprint character varying(64) NOT NULL,
    submitted_by bigint NOT NULL,
    submitted_by_username character varying(100) NOT NULL,
    submitted_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reviewed_by bigint,
    reviewed_by_username character varying(100),
    reviewed_at timestamp without time zone,
    review_comment character varying(500),
    revoked_by bigint,
    revoked_by_username character varying(100),
    revoked_at timestamp without time zone,
    revocation_comment character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_permission_request_high_risk_count CHECK ((high_risk_permission_count >= 0)),
    CONSTRAINT ck_permission_request_status CHECK (((status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'REVOKED'::character varying])::text[])))
);

ALTER TABLE ONLY public.sys_permission_request FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE sys_permission_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_permission_request IS 'Permission-package request state; role and user names are snapshots for durable review history.';


--
-- Name: sys_permission_request_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_permission_request_audit (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    permission_request_id bigint NOT NULL,
    action character varying(30) NOT NULL,
    operator_id bigint NOT NULL,
    operator_username character varying(100) NOT NULL,
    operator_role_code character varying(50) NOT NULL,
    target_user_id bigint NOT NULL,
    target_username character varying(100) NOT NULL,
    requested_role_id bigint NOT NULL,
    requested_role_code character varying(50) NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    warehouse_ids character varying(1000),
    high_risk_permission_count integer DEFAULT 0 CONSTRAINT sys_permission_request_audi_high_risk_permission_count_not_null NOT NULL,
    permission_codes character varying(4000),
    reason character varying(500),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_permission_request_audit_action CHECK (((action)::text = ANY ((ARRAY['CREATE'::character varying, 'APPROVE'::character varying, 'REJECT'::character varying, 'REVOKE'::character varying])::text[]))),
    CONSTRAINT ck_permission_request_audit_high_risk_count CHECK ((high_risk_permission_count >= 0))
);

ALTER TABLE ONLY public.sys_permission_request_audit FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE sys_permission_request_audit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_permission_request_audit IS 'Append-only permission request audit; identifiers are snapshots without lifecycle-blocking foreign keys.';


--
-- Name: sys_permission_request_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_permission_request_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_permission_request_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_permission_request_audit_id_seq OWNED BY public.sys_permission_request_audit.id;


--
-- Name: sys_permission_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_permission_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_permission_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_permission_request_id_seq OWNED BY public.sys_permission_request.id;


--
-- Name: sys_permission_request_warehouse; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_permission_request_warehouse (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    permission_request_id bigint NOT NULL,
    warehouse_id bigint NOT NULL
);

ALTER TABLE ONLY public.sys_permission_request_warehouse FORCE ROW LEVEL SECURITY;


--
-- Name: sys_permission_request_warehouse_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_permission_request_warehouse_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_permission_request_warehouse_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_permission_request_warehouse_id_seq OWNED BY public.sys_permission_request_warehouse.id;


--
-- Name: sys_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_role (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    description character varying(500),
    role_code character varying(50) NOT NULL,
    role_name character varying(100) NOT NULL,
    role_type character varying(20) NOT NULL,
    sort_order integer NOT NULL,
    status character varying(20) NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    system_category character varying(30),
    import_allowed boolean DEFAULT false NOT NULL,
    approval_template_code character varying(50),
    review_status character varying(30) DEFAULT 'APPROVED'::character varying NOT NULL,
    review_submitted_by bigint,
    review_submitted_by_username character varying(100),
    review_submitted_at timestamp without time zone,
    reviewed_by bigint,
    reviewed_by_username character varying(100),
    reviewed_at timestamp without time zone,
    review_comment character varying(500),
    CONSTRAINT ck_sys_role_custom_active_approved CHECK ((((role_type)::text <> 'CUSTOM'::text) OR ((status)::text <> 'ACTIVE'::text) OR ((review_status)::text = 'APPROVED'::text))),
    CONSTRAINT ck_sys_role_custom_approval_template CHECK ((((role_type)::text <> 'CUSTOM'::text) OR (approval_template_code IS NOT NULL))),
    CONSTRAINT ck_sys_role_privileged_not_importable CHECK ((((system_category)::text IS DISTINCT FROM 'PRIVILEGED'::text) OR (import_allowed = false))),
    CONSTRAINT ck_sys_role_privileged_system_protected CHECK ((((system_category)::text IS DISTINCT FROM 'PRIVILEGED'::text) OR (((role_type)::text = 'SYSTEM'::text) AND (import_allowed = false)))),
    CONSTRAINT ck_sys_role_review_status CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying])::text[]))),
    CONSTRAINT ck_sys_role_system_category CHECK (((system_category IS NULL) OR ((system_category)::text = ANY ((ARRAY['BUSINESS_TEMPLATE'::character varying, 'PRIVILEGED'::character varying])::text[])))),
    CONSTRAINT ck_sys_role_tenant_admin_governance CHECK ((((role_code)::text <> 'TENANT_ADMIN'::text) OR (((role_type)::text = 'SYSTEM'::text) AND ((system_category)::text = 'PRIVILEGED'::text) AND (import_allowed = false))))
);

ALTER TABLE ONLY public.sys_role FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN sys_role.role_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_role.role_code IS 'Stable authorization role code; WAREHOUSE_STAFF is the restricted mobile warehouse operator.';


--
-- Name: COLUMN sys_role.system_category; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_role.system_category IS 'System-role governance category: BUSINESS_TEMPLATE or PRIVILEGED; null for custom roles.';


--
-- Name: COLUMN sys_role.import_allowed; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_role.import_allowed IS 'Whether this role is approved as a role-copy/import source.';


--
-- Name: COLUMN sys_role.approval_template_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_role.approval_template_code IS 'Approval template bound to a custom permission package.';


--
-- Name: COLUMN sys_role.review_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_role.review_status IS 'Permission-package governance state: DRAFT, PENDING_REVIEW, or APPROVED.';


--
-- Name: CONSTRAINT ck_sys_role_privileged_system_protected ON sys_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_sys_role_privileged_system_protected ON public.sys_role IS 'Every privileged identity is a protected, non-importable SYSTEM role.';


--
-- Name: sys_role_copy_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_role_copy_audit (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    operator_id bigint,
    operator_username character varying(100) NOT NULL,
    source_role_id bigint NOT NULL,
    source_role_code character varying(50) NOT NULL,
    target_role_id bigint NOT NULL,
    target_role_code character varying(50) NOT NULL,
    permission_count integer NOT NULL,
    high_risk_count integer NOT NULL,
    high_risk_permission_codes character varying(2000),
    operation_reason character varying(500),
    snapshot_fingerprint character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_role_copy_audit_counts CHECK (((permission_count >= 0) AND (high_risk_count >= 0) AND (high_risk_count <= permission_count)))
);

ALTER TABLE ONLY public.sys_role_copy_audit FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE sys_role_copy_audit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_role_copy_audit IS 'Immutable audit trail for effective-permission snapshot copies';


--
-- Name: COLUMN sys_role_copy_audit.source_role_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_role_copy_audit.source_role_id IS 'Source role ID snapshot; intentionally not a lifecycle-blocking foreign key';


--
-- Name: COLUMN sys_role_copy_audit.target_role_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_role_copy_audit.target_role_id IS 'Target role ID snapshot; intentionally not a lifecycle-blocking foreign key';


--
-- Name: sys_role_copy_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_role_copy_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_copy_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_role_copy_audit_id_seq OWNED BY public.sys_role_copy_audit.id;


--
-- Name: sys_role_governance_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_role_governance_audit (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    role_id bigint NOT NULL,
    role_code character varying(50) NOT NULL,
    action character varying(30) NOT NULL,
    operator_id bigint,
    operator_username character varying(100) NOT NULL,
    from_review_status character varying(30),
    to_review_status character varying(30),
    from_runtime_status character varying(20),
    to_runtime_status character varying(20),
    permission_count integer DEFAULT 0 NOT NULL,
    high_risk_count integer DEFAULT 0 NOT NULL,
    permission_codes character varying(4000),
    added_permission_codes character varying(2000),
    removed_permission_codes character varying(2000),
    reason character varying(500),
    snapshot_fingerprint character varying(64),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_role_governance_audit_action CHECK (((action)::text = ANY ((ARRAY['UPDATE_DRAFT'::character varying, 'SUBMIT_REVIEW'::character varying, 'APPROVE'::character varying, 'REJECT'::character varying, 'ACTIVATE'::character varying, 'DEACTIVATE'::character varying])::text[]))),
    CONSTRAINT ck_role_governance_audit_counts CHECK (((permission_count >= 0) AND (high_risk_count >= 0) AND (high_risk_count <= permission_count)))
);

ALTER TABLE ONLY public.sys_role_governance_audit FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE sys_role_governance_audit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_role_governance_audit IS 'Immutable role-package lifecycle audit; role identifiers are snapshots without lifecycle-blocking foreign keys.';


--
-- Name: sys_role_governance_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_role_governance_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_governance_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_role_governance_audit_id_seq OWNED BY public.sys_role_governance_audit.id;


--
-- Name: sys_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_role_id_seq OWNED BY public.sys_role.id;


--
-- Name: sys_role_inherit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_role_inherit (
    id bigint NOT NULL,
    child_role_id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    parent_role_id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.sys_role_inherit FORCE ROW LEVEL SECURITY;


--
-- Name: sys_role_inherit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_role_inherit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_inherit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_role_inherit_id_seq OWNED BY public.sys_role_inherit.id;


--
-- Name: sys_role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_role_permission (
    id bigint NOT NULL,
    granted_at timestamp(6) without time zone NOT NULL,
    granted_by bigint,
    permission_id bigint NOT NULL,
    role_id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.sys_role_permission FORCE ROW LEVEL SECURITY;


--
-- Name: sys_role_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_role_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_role_permission_id_seq OWNED BY public.sys_role_permission.id;


--
-- Name: sys_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user_role (
    id bigint NOT NULL,
    assigned_at timestamp(6) without time zone NOT NULL,
    assigned_by bigint,
    role_id bigint NOT NULL,
    user_id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.sys_user_role FORCE ROW LEVEL SECURITY;


--
-- Name: sys_user_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_role_id_seq OWNED BY public.sys_user_role.id;


--
-- Name: sys_user_warehouse; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user_warehouse (
    id bigint NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    user_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    assigned_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    assigned_by bigint
);

ALTER TABLE ONLY public.sys_user_warehouse FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE sys_user_warehouse; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_user_warehouse IS 'Warehouses assigned to users while operating under WAREHOUSE_STAFF';


--
-- Name: COLUMN sys_user_warehouse.assigned_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user_warehouse.assigned_by IS 'Administrator user ID that last assigned the warehouse';


--
-- Name: sys_user_warehouse_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_warehouse_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_warehouse_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_warehouse_id_seq OWNED BY public.sys_user_warehouse.id;


--
-- Name: system_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_config (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    config_key character varying(100) NOT NULL,
    config_type character varying(50) NOT NULL,
    config_value character varying(500) NOT NULL,
    description character varying(500),
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.system_config FORCE ROW LEVEL SECURITY;


--
-- Name: system_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_config_id_seq OWNED BY public.system_config.id;


--
-- Name: tenant_domains; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_domains (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    hostname character varying(253) NOT NULL,
    domain_type character varying(30) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    verification_token_hash character varying(128),
    verified_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_tenant_domains_hostname_normalized CHECK (((hostname)::text = lower(btrim((hostname)::text)))),
    CONSTRAINT ck_tenant_domains_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'ACTIVE'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT ck_tenant_domains_type CHECK (((domain_type)::text = ANY ((ARRAY['PLATFORM_SUBDOMAIN'::character varying, 'CUSTOM_DOMAIN'::character varying])::text[])))
);


--
-- Name: tenant_domains_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_domains_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_domains_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_domains_id_seq OWNED BY public.tenant_domains.id;


--
-- Name: tenant_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_memberships (
    id bigint NOT NULL,
    identity_id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    tenant_user_id bigint NOT NULL,
    status character varying(20) DEFAULT 'INVITED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_tenant_memberships_status CHECK (((status)::text = ANY ((ARRAY['INVITED'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: tenant_memberships_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_memberships_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_memberships_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_memberships_id_seq OWNED BY public.tenant_memberships.id;


--
-- Name: tenant_provisioning_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_provisioning_jobs (
    id bigint NOT NULL,
    signup_request_id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    current_step character varying(80),
    attempt_count integer DEFAULT 0 NOT NULL,
    last_error character varying(1000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_provisioning_jobs_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_provisioning_jobs_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: TABLE tenant_provisioning_jobs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenant_provisioning_jobs IS 'Provisioning worker claims a job only after signup_requests.tenant_id is assigned.';


--
-- Name: tenant_provisioning_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_provisioning_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_provisioning_jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_provisioning_jobs_id_seq OWNED BY public.tenant_provisioning_jobs.id;


--
-- Name: tenant_purge_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_purge_audit_logs (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    event_type character varying(20) NOT NULL,
    result character varying(20) NOT NULL,
    attempt_count integer NOT NULL,
    deleted_row_count bigint,
    error_detail character varying(1000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_tenant_purge_audit_event CHECK (((event_type)::text = ANY ((ARRAY['STARTED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_tenant_purge_audit_result CHECK (((result)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: TABLE tenant_purge_audit_logs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenant_purge_audit_logs IS 'Platform-only append-only evidence for irreversible company lifecycle cleanup';


--
-- Name: tenant_purge_audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_purge_audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_purge_audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_purge_audit_logs_id_seq OWNED BY public.tenant_purge_audit_logs.id;


--
-- Name: tenant_purge_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_purge_jobs (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    last_error character varying(1000),
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    lease_expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_tenant_purge_jobs_attempt CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_tenant_purge_jobs_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: TABLE tenant_purge_jobs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenant_purge_jobs IS 'Retryable lifecycle cleanup state; tenant tombstone and platform audit are retained permanently';


--
-- Name: tenant_purge_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_purge_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_purge_jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_purge_jobs_id_seq OWNED BY public.tenant_purge_jobs.id;


--
-- Name: tenant_session_security_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_session_security_audit (
    id bigint NOT NULL,
    company_id bigint NOT NULL,
    action character varying(40) NOT NULL,
    operator_id bigint NOT NULL,
    operator_username character varying(100) NOT NULL,
    target_user_id bigint NOT NULL,
    target_username character varying(100) NOT NULL,
    reason character varying(500) NOT NULL,
    result character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_tenant_session_audit_action CHECK (((action)::text = ANY ((ARRAY['SELF_REVOKE_ALL_SESSIONS'::character varying, 'ADMIN_REVOKE_ALL_SESSIONS'::character varying])::text[]))),
    CONSTRAINT ck_tenant_session_audit_result CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED'::character varying])::text[])))
);

ALTER TABLE ONLY public.tenant_session_security_audit FORCE ROW LEVEL SECURITY;


--
-- Name: tenant_session_security_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_session_security_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_session_security_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_session_security_audit_id_seq OWNED BY public.tenant_session_security_audit.id;


--
-- Name: tenant_subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_subscriptions (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    plan_id bigint NOT NULL,
    status character varying(30) NOT NULL,
    trial_started_at timestamp with time zone,
    trial_ends_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_tenant_subscriptions_status CHECK (((status)::text = ANY ((ARRAY['TRIALING'::character varying, 'FREE'::character varying, 'ACTIVE'::character varying, 'PAST_DUE'::character varying, 'CANCELED'::character varying, 'EXPIRED'::character varying])::text[]))),
    CONSTRAINT ck_tenant_subscriptions_trial CHECK ((((status)::text <> 'TRIALING'::text) OR ((trial_started_at IS NOT NULL) AND (trial_ends_at = (trial_started_at + '30 days'::interval)))))
);


--
-- Name: tenant_subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_subscriptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_subscriptions_id_seq OWNED BY public.tenant_subscriptions.id;


--
-- Name: tenants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenants (
    id bigint NOT NULL,
    tenant_code character varying(40) NOT NULL,
    display_name character varying(160) NOT NULL,
    slug character varying(30) NOT NULL,
    status character varying(30) DEFAULT 'PROVISIONING'::character varying NOT NULL,
    timezone character varying(60) DEFAULT 'Asia/Shanghai'::character varying NOT NULL,
    locale character varying(20) DEFAULT 'zh-CN'::character varying NOT NULL,
    closed_at timestamp with time zone,
    purge_due_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_tenants_retention CHECK (((((status)::text <> ALL ((ARRAY['CLOSED'::character varying, 'PURGE_PENDING'::character varying, 'PURGED'::character varying])::text[])) AND (closed_at IS NULL) AND (purge_due_at IS NULL)) OR (((status)::text = ANY ((ARRAY['CLOSED'::character varying, 'PURGE_PENDING'::character varying, 'PURGED'::character varying])::text[])) AND (closed_at IS NOT NULL) AND (purge_due_at IS NOT NULL) AND (purge_due_at = (closed_at + '30 days'::interval))))),
    CONSTRAINT ck_tenants_slug CHECK ((((slug)::text ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$'::text) AND ((slug)::text <> ALL ((ARRAY['www'::character varying, 'app'::character varying, 'api'::character varying, 'platform'::character varying, 'admin'::character varying, 'support'::character varying, 'mail'::character varying, 'static'::character varying, 'cdn'::character varying, 'status'::character varying, 'help'::character varying])::text[])))),
    CONSTRAINT ck_tenants_status CHECK (((status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'CLOSED'::character varying, 'PURGE_PENDING'::character varying, 'PURGED'::character varying])::text[])))
);


--
-- Name: TABLE tenants; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenants IS 'SaaS company registry. tenants.id is the authoritative company_id.';


--
-- Name: tenants_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenants_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenants_id_seq OWNED BY public.tenants.id;


--
-- Name: user_identities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_identities (
    id bigint NOT NULL,
    normalized_email character varying(254) NOT NULL,
    password_hash character varying(255) NOT NULL,
    email_verified_at timestamp with time zone NOT NULL,
    security_version bigint DEFAULT 1 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_user_identities_email_normalized CHECK (((normalized_email)::text = lower(btrim((normalized_email)::text)))),
    CONSTRAINT ck_user_identities_security_version CHECK ((security_version > 0))
);


--
-- Name: user_identities_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_identities_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_identities_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_identities_id_seq OWNED BY public.user_identities.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    displayname character varying(100),
    enabled boolean NOT NULL,
    password character varying(255) NOT NULL,
    remark character varying(500),
    role character varying(20),
    username character varying(50) NOT NULL,
    default_role_id bigint,
    is_deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by bigint,
    company_id bigint DEFAULT 1 NOT NULL,
    must_change_password boolean DEFAULT false NOT NULL,
    security_version bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.users FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE users; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.users IS 'Company user accounts only. Platform administrators are stored exclusively in platform_users.';


--
-- Name: COLUMN users.role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.role IS 'Deprecated legacy role. Authorization uses sys_user_role exclusively.';


--
-- Name: COLUMN users.is_deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.is_deleted IS 'Logical deletion marker. Deleted users remain for historical audit links.';


--
-- Name: COLUMN users.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.deleted_at IS 'UTC timestamp when the user was logically deleted.';


--
-- Name: COLUMN users.deleted_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.deleted_by IS 'Administrator user ID that performed the logical deletion.';


--
-- Name: COLUMN users.company_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';


--
-- Name: COLUMN users.must_change_password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.must_change_password IS 'TRUE after an admin password reset; the account may only call the change-password endpoint until the user sets a new password';


--
-- Name: COLUMN users.security_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.security_version IS 'Monotonic JWT authorization context version; incremented after credential, account, role, or effective permission changes.';


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: warehouses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warehouses (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    address character varying(255),
    contact character varying(50),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    isactive boolean DEFAULT true NOT NULL,
    phone character varying(20),
    company_id bigint DEFAULT 1 NOT NULL
);

ALTER TABLE ONLY public.warehouses FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE warehouses; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.warehouses IS 'Warehouse master data table';


--
-- Name: warehouses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.warehouses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: warehouses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.warehouses_id_seq OWNED BY public.warehouses.id;


--
-- Name: backorder_line id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.backorder_line ALTER COLUMN id SET DEFAULT nextval('public.backorder_line_id_seq'::regclass);


--
-- Name: categories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);


--
-- Name: channel_inventory_state id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_inventory_state ALTER COLUMN id SET DEFAULT nextval('public.channel_inventory_state_id_seq'::regclass);


--
-- Name: channel_raw_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_raw_events ALTER COLUMN id SET DEFAULT nextval('public.channel_raw_events_id_seq'::regclass);


--
-- Name: channel_sku_mapping id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_sku_mapping ALTER COLUMN id SET DEFAULT nextval('public.channel_sku_mapping_id_seq'::regclass);


--
-- Name: customer_fact_summary id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_fact_summary ALTER COLUMN id SET DEFAULT nextval('public.customer_fact_summary_id_seq'::regclass);


--
-- Name: customer_product_summary id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_product_summary ALTER COLUMN id SET DEFAULT nextval('public.customer_product_summary_id_seq'::regclass);


--
-- Name: customers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers ALTER COLUMN id SET DEFAULT nextval('public.customers_id_seq'::regclass);


--
-- Name: domain_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domain_outbox ALTER COLUMN id SET DEFAULT nextval('public.domain_outbox_id_seq'::regclass);


--
-- Name: email_verification_challenges id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_challenges ALTER COLUMN id SET DEFAULT nextval('public.email_verification_challenges_id_seq'::regclass);


--
-- Name: emergency_stock_correction id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.emergency_stock_correction ALTER COLUMN id SET DEFAULT nextval('public.emergency_stock_correction_id_seq'::regclass);


--
-- Name: historical_test_data_archive_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_test_data_archive_audit ALTER COLUMN id SET DEFAULT nextval('public.historical_test_data_archive_audit_id_seq'::regclass);


--
-- Name: historical_test_data_registry id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_test_data_registry ALTER COLUMN id SET DEFAULT nextval('public.historical_test_data_registry_id_seq'::regclass);


--
-- Name: idempotency_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_request ALTER COLUMN id SET DEFAULT nextval('public.idempotency_request_id_seq'::regclass);


--
-- Name: inbound_order_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_order_items ALTER COLUMN id SET DEFAULT nextval('public.inbound_order_items_id_seq'::regclass);


--
-- Name: inbound_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_orders ALTER COLUMN id SET DEFAULT nextval('public.inbound_orders_id_seq'::regclass);


--
-- Name: integration_configs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_configs ALTER COLUMN id SET DEFAULT nextval('public.integration_configs_id_seq'::regclass);


--
-- Name: inventory id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory ALTER COLUMN id SET DEFAULT nextval('public.inventory_id_seq'::regclass);


--
-- Name: inventory_batch id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_batch ALTER COLUMN id SET DEFAULT nextval('public.inventory_batch_id_seq'::regclass);


--
-- Name: inventory_reservations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_reservations ALTER COLUMN id SET DEFAULT nextval('public.inventory_reservations_id_seq'::regclass);


--
-- Name: locations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations ALTER COLUMN id SET DEFAULT nextval('public.locations_id_seq'::regclass);


--
-- Name: outbound_tasks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbound_tasks ALTER COLUMN id SET DEFAULT nextval('public.outbound_tasks_id_seq'::regclass);


--
-- Name: pending_sku_mapping id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_sku_mapping ALTER COLUMN id SET DEFAULT nextval('public.pending_sku_mapping_id_seq'::regclass);


--
-- Name: plan_limits id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_limits ALTER COLUMN id SET DEFAULT nextval('public.plan_limits_id_seq'::regclass);


--
-- Name: platform_access_grants id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_access_grants ALTER COLUMN id SET DEFAULT nextval('public.platform_access_grants_id_seq'::regclass);


--
-- Name: platform_admin_commands id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_commands ALTER COLUMN id SET DEFAULT nextval('public.platform_admin_commands_id_seq'::regclass);


--
-- Name: platform_admin_invitation_activations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitation_activations ALTER COLUMN id SET DEFAULT nextval('public.platform_admin_invitation_activations_id_seq'::regclass);


--
-- Name: platform_admin_invitations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitations ALTER COLUMN id SET DEFAULT nextval('public.platform_admin_invitations_id_seq'::regclass);


--
-- Name: platform_audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_audit_logs ALTER COLUMN id SET DEFAULT nextval('public.platform_audit_logs_id_seq'::regclass);


--
-- Name: platform_export_jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_export_jobs ALTER COLUMN id SET DEFAULT nextval('public.platform_export_jobs_id_seq'::regclass);


--
-- Name: platform_mfa_challenges id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_mfa_challenges ALTER COLUMN id SET DEFAULT nextval('public.platform_mfa_challenges_id_seq'::regclass);


--
-- Name: platform_operation_authorizations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_operation_authorizations ALTER COLUMN id SET DEFAULT nextval('public.platform_operation_authorizations_id_seq'::regclass);


--
-- Name: platform_roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_roles ALTER COLUMN id SET DEFAULT nextval('public.platform_roles_id_seq'::regclass);


--
-- Name: platform_user_roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_user_roles ALTER COLUMN id SET DEFAULT nextval('public.platform_user_roles_id_seq'::regclass);


--
-- Name: platform_users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_users ALTER COLUMN id SET DEFAULT nextval('public.platform_users_id_seq'::regclass);


--
-- Name: product_skus id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_skus ALTER COLUMN id SET DEFAULT nextval('public.product_skus_id_seq'::regclass);


--
-- Name: purchase_order id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order ALTER COLUMN id SET DEFAULT nextval('public.purchase_order_id_seq'::regclass);


--
-- Name: purchase_order_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_item ALTER COLUMN id SET DEFAULT nextval('public.purchase_order_item_id_seq'::regclass);


--
-- Name: sales_daily_summary id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_daily_summary ALTER COLUMN id SET DEFAULT nextval('public.sales_daily_summary_id_seq'::regclass);


--
-- Name: sales_order_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_items ALTER COLUMN id SET DEFAULT nextval('public.sales_order_items_id_seq'::regclass);


--
-- Name: sales_order_shipments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_shipments ALTER COLUMN id SET DEFAULT nextval('public.sales_order_shipments_id_seq'::regclass);


--
-- Name: sales_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders ALTER COLUMN id SET DEFAULT nextval('public.sales_orders_id_seq'::regclass);


--
-- Name: session_handoff_codes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_handoff_codes ALTER COLUMN id SET DEFAULT nextval('public.session_handoff_codes_id_seq'::regclass);


--
-- Name: signup_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.signup_requests ALTER COLUMN id SET DEFAULT nextval('public.signup_requests_id_seq'::regclass);


--
-- Name: stock_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transactions ALTER COLUMN id SET DEFAULT nextval('public.stock_transactions_id_seq'::regclass);


--
-- Name: stocktake_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items ALTER COLUMN id SET DEFAULT nextval('public.stocktake_items_id_seq'::regclass);


--
-- Name: stocktake_tasks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_tasks ALTER COLUMN id SET DEFAULT nextval('public.stocktake_tasks_id_seq'::regclass);


--
-- Name: subscription_plans id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_plans ALTER COLUMN id SET DEFAULT nextval('public.subscription_plans_id_seq'::regclass);


--
-- Name: suppliers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers ALTER COLUMN id SET DEFAULT nextval('public.suppliers_id_seq'::regclass);


--
-- Name: sys_approval_template id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_approval_template ALTER COLUMN id SET DEFAULT nextval('public.sys_approval_template_id_seq'::regclass);


--
-- Name: sys_excel_templates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_excel_templates ALTER COLUMN id SET DEFAULT nextval('public.sys_excel_templates_id_seq'::regclass);


--
-- Name: sys_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission ALTER COLUMN id SET DEFAULT nextval('public.sys_permission_id_seq'::regclass);


--
-- Name: sys_permission_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission_request ALTER COLUMN id SET DEFAULT nextval('public.sys_permission_request_id_seq'::regclass);


--
-- Name: sys_permission_request_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission_request_audit ALTER COLUMN id SET DEFAULT nextval('public.sys_permission_request_audit_id_seq'::regclass);


--
-- Name: sys_permission_request_warehouse id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission_request_warehouse ALTER COLUMN id SET DEFAULT nextval('public.sys_permission_request_warehouse_id_seq'::regclass);


--
-- Name: sys_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role ALTER COLUMN id SET DEFAULT nextval('public.sys_role_id_seq'::regclass);


--
-- Name: sys_role_copy_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_copy_audit ALTER COLUMN id SET DEFAULT nextval('public.sys_role_copy_audit_id_seq'::regclass);


--
-- Name: sys_role_governance_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_governance_audit ALTER COLUMN id SET DEFAULT nextval('public.sys_role_governance_audit_id_seq'::regclass);


--
-- Name: sys_role_inherit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_inherit ALTER COLUMN id SET DEFAULT nextval('public.sys_role_inherit_id_seq'::regclass);


--
-- Name: sys_role_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_permission ALTER COLUMN id SET DEFAULT nextval('public.sys_role_permission_id_seq'::regclass);


--
-- Name: sys_user_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_role ALTER COLUMN id SET DEFAULT nextval('public.sys_user_role_id_seq'::regclass);


--
-- Name: sys_user_warehouse id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_warehouse ALTER COLUMN id SET DEFAULT nextval('public.sys_user_warehouse_id_seq'::regclass);


--
-- Name: system_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_config ALTER COLUMN id SET DEFAULT nextval('public.system_config_id_seq'::regclass);


--
-- Name: tenant_domains id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_domains ALTER COLUMN id SET DEFAULT nextval('public.tenant_domains_id_seq'::regclass);


--
-- Name: tenant_memberships id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_memberships ALTER COLUMN id SET DEFAULT nextval('public.tenant_memberships_id_seq'::regclass);


--
-- Name: tenant_provisioning_jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_provisioning_jobs ALTER COLUMN id SET DEFAULT nextval('public.tenant_provisioning_jobs_id_seq'::regclass);


--
-- Name: tenant_purge_audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_purge_audit_logs ALTER COLUMN id SET DEFAULT nextval('public.tenant_purge_audit_logs_id_seq'::regclass);


--
-- Name: tenant_purge_jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_purge_jobs ALTER COLUMN id SET DEFAULT nextval('public.tenant_purge_jobs_id_seq'::regclass);


--
-- Name: tenant_session_security_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_session_security_audit ALTER COLUMN id SET DEFAULT nextval('public.tenant_session_security_audit_id_seq'::regclass);


--
-- Name: tenant_subscriptions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_subscriptions ALTER COLUMN id SET DEFAULT nextval('public.tenant_subscriptions_id_seq'::regclass);


--
-- Name: tenants id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants ALTER COLUMN id SET DEFAULT nextval('public.tenants_id_seq'::regclass);


--
-- Name: user_identities id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_identities ALTER COLUMN id SET DEFAULT nextval('public.user_identities_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: warehouses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses ALTER COLUMN id SET DEFAULT nextval('public.warehouses_id_seq'::regclass);


--
-- Name: backorder_line backorder_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.backorder_line
    ADD CONSTRAINT backorder_line_pkey PRIMARY KEY (id);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: channel_inventory_state channel_inventory_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_inventory_state
    ADD CONSTRAINT channel_inventory_state_pkey PRIMARY KEY (id);


--
-- Name: channel_raw_events channel_raw_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_raw_events
    ADD CONSTRAINT channel_raw_events_pkey PRIMARY KEY (id);


--
-- Name: channel_sku_mapping channel_sku_mapping_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_sku_mapping
    ADD CONSTRAINT channel_sku_mapping_pkey PRIMARY KEY (id);


--
-- Name: channel_webhook_routes channel_webhook_routes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_webhook_routes
    ADD CONSTRAINT channel_webhook_routes_pkey PRIMARY KEY (integration_config_id);


--
-- Name: customer_fact_summary customer_fact_summary_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_fact_summary
    ADD CONSTRAINT customer_fact_summary_pkey PRIMARY KEY (id);


--
-- Name: customer_product_summary customer_product_summary_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_product_summary
    ADD CONSTRAINT customer_product_summary_pkey PRIMARY KEY (id);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);


--
-- Name: domain_outbox domain_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domain_outbox
    ADD CONSTRAINT domain_outbox_pkey PRIMARY KEY (id);


--
-- Name: email_verification_challenges email_verification_challenges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_challenges
    ADD CONSTRAINT email_verification_challenges_pkey PRIMARY KEY (id);


--
-- Name: emergency_stock_correction emergency_stock_correction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.emergency_stock_correction
    ADD CONSTRAINT emergency_stock_correction_pkey PRIMARY KEY (id);


--
-- Name: historical_test_data_archive_audit historical_test_data_archive_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_test_data_archive_audit
    ADD CONSTRAINT historical_test_data_archive_audit_pkey PRIMARY KEY (id);


--
-- Name: historical_test_data_registry historical_test_data_registry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_test_data_registry
    ADD CONSTRAINT historical_test_data_registry_pkey PRIMARY KEY (id);


--
-- Name: idempotency_request idempotency_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_request
    ADD CONSTRAINT idempotency_request_pkey PRIMARY KEY (id);


--
-- Name: inbound_order_items inbound_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_order_items
    ADD CONSTRAINT inbound_order_items_pkey PRIMARY KEY (id);


--
-- Name: inbound_orders inbound_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_orders
    ADD CONSTRAINT inbound_orders_pkey PRIMARY KEY (id);


--
-- Name: integration_configs integration_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_configs
    ADD CONSTRAINT integration_configs_pkey PRIMARY KEY (id);


--
-- Name: inventory_batch inventory_batch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_batch
    ADD CONSTRAINT inventory_batch_pkey PRIMARY KEY (id);


--
-- Name: inventory inventory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_pkey PRIMARY KEY (id);


--
-- Name: inventory_reservations inventory_reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_reservations
    ADD CONSTRAINT inventory_reservations_pkey PRIMARY KEY (id);


--
-- Name: locations locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_pkey PRIMARY KEY (id);


--
-- Name: outbound_tasks outbound_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbound_tasks
    ADD CONSTRAINT outbound_tasks_pkey PRIMARY KEY (id);


--
-- Name: pending_sku_mapping pending_sku_mapping_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_sku_mapping
    ADD CONSTRAINT pending_sku_mapping_pkey PRIMARY KEY (id);


--
-- Name: plan_limits plan_limits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_limits
    ADD CONSTRAINT plan_limits_pkey PRIMARY KEY (id);


--
-- Name: platform_access_grants platform_access_grants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_access_grants
    ADD CONSTRAINT platform_access_grants_pkey PRIMARY KEY (id);


--
-- Name: platform_admin_commands platform_admin_commands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_commands
    ADD CONSTRAINT platform_admin_commands_pkey PRIMARY KEY (id);


--
-- Name: platform_admin_invitation_activations platform_admin_invitation_activations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitation_activations
    ADD CONSTRAINT platform_admin_invitation_activations_pkey PRIMARY KEY (id);


--
-- Name: platform_admin_invitation_active_emails platform_admin_invitation_active_emails_invitation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitation_active_emails
    ADD CONSTRAINT platform_admin_invitation_active_emails_invitation_id_key UNIQUE (invitation_id);


--
-- Name: platform_admin_invitation_active_emails platform_admin_invitation_active_emails_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitation_active_emails
    ADD CONSTRAINT platform_admin_invitation_active_emails_pkey PRIMARY KEY (normalized_email);


--
-- Name: platform_admin_invitations platform_admin_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitations
    ADD CONSTRAINT platform_admin_invitations_pkey PRIMARY KEY (id);


--
-- Name: platform_audit_logs platform_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_audit_logs
    ADD CONSTRAINT platform_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: platform_export_jobs platform_export_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_export_jobs
    ADD CONSTRAINT platform_export_jobs_pkey PRIMARY KEY (id);


--
-- Name: platform_mfa_challenges platform_mfa_challenges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_mfa_challenges
    ADD CONSTRAINT platform_mfa_challenges_pkey PRIMARY KEY (id);


--
-- Name: platform_operation_authorizations platform_operation_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_operation_authorizations
    ADD CONSTRAINT platform_operation_authorizations_pkey PRIMARY KEY (id);


--
-- Name: platform_roles platform_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_roles
    ADD CONSTRAINT platform_roles_pkey PRIMARY KEY (id);


--
-- Name: platform_user_roles platform_user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_user_roles
    ADD CONSTRAINT platform_user_roles_pkey PRIMARY KEY (id);


--
-- Name: platform_users platform_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_users
    ADD CONSTRAINT platform_users_pkey PRIMARY KEY (id);


--
-- Name: product_skus product_skus_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_skus
    ADD CONSTRAINT product_skus_pkey PRIMARY KEY (id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_item purchase_order_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_item
    ADD CONSTRAINT purchase_order_item_pkey PRIMARY KEY (id);


--
-- Name: purchase_order purchase_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order
    ADD CONSTRAINT purchase_order_pkey PRIMARY KEY (id);


--
-- Name: sales_daily_summary sales_daily_summary_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_daily_summary
    ADD CONSTRAINT sales_daily_summary_pkey PRIMARY KEY (id);


--
-- Name: sales_order_items sales_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_items
    ADD CONSTRAINT sales_order_items_pkey PRIMARY KEY (id);


--
-- Name: sales_order_shipments sales_order_shipments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_shipments
    ADD CONSTRAINT sales_order_shipments_pkey PRIMARY KEY (id);


--
-- Name: sales_orders sales_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders
    ADD CONSTRAINT sales_orders_pkey PRIMARY KEY (id);


--
-- Name: session_handoff_codes session_handoff_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_handoff_codes
    ADD CONSTRAINT session_handoff_codes_pkey PRIMARY KEY (id);


--
-- Name: signup_requests signup_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.signup_requests
    ADD CONSTRAINT signup_requests_pkey PRIMARY KEY (id);


--
-- Name: stock_transactions stock_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transactions
    ADD CONSTRAINT stock_transactions_pkey PRIMARY KEY (id);


--
-- Name: stocktake_items stocktake_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT stocktake_items_pkey PRIMARY KEY (id);


--
-- Name: stocktake_tasks stocktake_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_tasks
    ADD CONSTRAINT stocktake_tasks_pkey PRIMARY KEY (id);


--
-- Name: subscription_plans subscription_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_plans
    ADD CONSTRAINT subscription_plans_pkey PRIMARY KEY (id);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);


--
-- Name: sys_approval_template sys_approval_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_approval_template
    ADD CONSTRAINT sys_approval_template_pkey PRIMARY KEY (id);


--
-- Name: sys_excel_templates sys_excel_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_excel_templates
    ADD CONSTRAINT sys_excel_templates_pkey PRIMARY KEY (id);


--
-- Name: sys_permission sys_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission
    ADD CONSTRAINT sys_permission_pkey PRIMARY KEY (id);


--
-- Name: sys_permission_request_audit sys_permission_request_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission_request_audit
    ADD CONSTRAINT sys_permission_request_audit_pkey PRIMARY KEY (id);


--
-- Name: sys_permission_request sys_permission_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission_request
    ADD CONSTRAINT sys_permission_request_pkey PRIMARY KEY (id);


--
-- Name: sys_permission_request_warehouse sys_permission_request_warehouse_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission_request_warehouse
    ADD CONSTRAINT sys_permission_request_warehouse_pkey PRIMARY KEY (id);


--
-- Name: sys_role_copy_audit sys_role_copy_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_copy_audit
    ADD CONSTRAINT sys_role_copy_audit_pkey PRIMARY KEY (id);


--
-- Name: sys_role_governance_audit sys_role_governance_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_governance_audit
    ADD CONSTRAINT sys_role_governance_audit_pkey PRIMARY KEY (id);


--
-- Name: sys_role_inherit sys_role_inherit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_inherit
    ADD CONSTRAINT sys_role_inherit_pkey PRIMARY KEY (id);


--
-- Name: sys_role_permission sys_role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_permission
    ADD CONSTRAINT sys_role_permission_pkey PRIMARY KEY (id);


--
-- Name: sys_role sys_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role
    ADD CONSTRAINT sys_role_pkey PRIMARY KEY (id);


--
-- Name: sys_user_role sys_user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_role
    ADD CONSTRAINT sys_user_role_pkey PRIMARY KEY (id);


--
-- Name: sys_user_warehouse sys_user_warehouse_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_warehouse
    ADD CONSTRAINT sys_user_warehouse_pkey PRIMARY KEY (id);


--
-- Name: system_config system_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_config
    ADD CONSTRAINT system_config_pkey PRIMARY KEY (id);


--
-- Name: tenant_domains tenant_domains_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_domains
    ADD CONSTRAINT tenant_domains_pkey PRIMARY KEY (id);


--
-- Name: tenant_memberships tenant_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT tenant_memberships_pkey PRIMARY KEY (id);


--
-- Name: tenant_provisioning_jobs tenant_provisioning_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_provisioning_jobs
    ADD CONSTRAINT tenant_provisioning_jobs_pkey PRIMARY KEY (id);


--
-- Name: tenant_purge_audit_logs tenant_purge_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_purge_audit_logs
    ADD CONSTRAINT tenant_purge_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: tenant_purge_jobs tenant_purge_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_purge_jobs
    ADD CONSTRAINT tenant_purge_jobs_pkey PRIMARY KEY (id);


--
-- Name: tenant_session_security_audit tenant_session_security_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_session_security_audit
    ADD CONSTRAINT tenant_session_security_audit_pkey PRIMARY KEY (id);


--
-- Name: tenant_subscriptions tenant_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_subscriptions
    ADD CONSTRAINT tenant_subscriptions_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: sys_approval_template uk_approval_template_company_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_approval_template
    ADD CONSTRAINT uk_approval_template_company_code UNIQUE (company_id, template_code);


--
-- Name: categories uk_categories_company_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT uk_categories_company_code UNIQUE (company_id, category_code);


--
-- Name: customer_fact_summary uk_customer_fact_summary_company_customer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_fact_summary
    ADD CONSTRAINT uk_customer_fact_summary_company_customer UNIQUE (company_id, customer_id);


--
-- Name: customer_product_summary uk_customer_product_summary_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_product_summary
    ADD CONSTRAINT uk_customer_product_summary_scope UNIQUE (company_id, customer_id, product_sku_id);


--
-- Name: historical_test_data_registry uk_historical_test_registry_order_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_test_data_registry
    ADD CONSTRAINT uk_historical_test_registry_order_id UNIQUE (company_id, sales_order_id);


--
-- Name: historical_test_data_registry uk_historical_test_registry_order_no; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_test_data_registry
    ADD CONSTRAINT uk_historical_test_registry_order_no UNIQUE (company_id, order_no);


--
-- Name: channel_inventory_state uk_inventory_state_mapping; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_inventory_state
    ADD CONSTRAINT uk_inventory_state_mapping UNIQUE (mapping_id);


--
-- Name: pending_sku_mapping uk_pending_sku; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_sku_mapping
    ADD CONSTRAINT uk_pending_sku UNIQUE (company_id, channel, external_sku);


--
-- Name: sys_permission_request_warehouse uk_permission_request_warehouse; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission_request_warehouse
    ADD CONSTRAINT uk_permission_request_warehouse UNIQUE (company_id, permission_request_id, warehouse_id);


--
-- Name: plan_limits uk_plan_limits_plan_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_limits
    ADD CONSTRAINT uk_plan_limits_plan_key UNIQUE (plan_id, limit_key);


--
-- Name: platform_admin_commands uk_platform_admin_command_actor_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_commands
    ADD CONSTRAINT uk_platform_admin_command_actor_key UNIQUE (actor_platform_user_id, idempotency_key);


--
-- Name: platform_admin_invitation_activations uk_platform_admin_invitation_activation_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitation_activations
    ADD CONSTRAINT uk_platform_admin_invitation_activation_token UNIQUE (token_hash);


--
-- Name: platform_admin_invitations uk_platform_admin_invitation_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitations
    ADD CONSTRAINT uk_platform_admin_invitation_token UNIQUE (token_hash);


--
-- Name: platform_export_jobs uk_platform_export_jobs_public; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_export_jobs
    ADD CONSTRAINT uk_platform_export_jobs_public UNIQUE (public_id);


--
-- Name: platform_mfa_challenges uk_platform_mfa_challenge_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_mfa_challenges
    ADD CONSTRAINT uk_platform_mfa_challenge_token UNIQUE (token_hash);


--
-- Name: platform_operation_authorizations uk_platform_operation_auth_public_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_operation_authorizations
    ADD CONSTRAINT uk_platform_operation_auth_public_id UNIQUE (public_id);


--
-- Name: platform_roles uk_platform_roles_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_roles
    ADD CONSTRAINT uk_platform_roles_code UNIQUE (role_code);


--
-- Name: platform_user_roles uk_platform_user_roles_user_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_user_roles
    ADD CONSTRAINT uk_platform_user_roles_user_role UNIQUE (platform_user_id, platform_role_id);


--
-- Name: platform_users uk_platform_users_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_users
    ADD CONSTRAINT uk_platform_users_email UNIQUE (normalized_email);


--
-- Name: tenant_provisioning_jobs uk_provisioning_jobs_signup; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_provisioning_jobs
    ADD CONSTRAINT uk_provisioning_jobs_signup UNIQUE (signup_request_id);


--
-- Name: sales_daily_summary uk_sales_daily_summary_company_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_daily_summary
    ADD CONSTRAINT uk_sales_daily_summary_company_date UNIQUE (company_id, summary_date);


--
-- Name: sales_order_shipments uk_sales_order_shipment_tracking; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_shipments
    ADD CONSTRAINT uk_sales_order_shipment_tracking UNIQUE (company_id, sales_order_id, tracking_no);


--
-- Name: session_handoff_codes uk_session_handoff_codes_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_handoff_codes
    ADD CONSTRAINT uk_session_handoff_codes_hash UNIQUE (code_hash);


--
-- Name: session_handoff_codes uk_session_handoff_codes_signup; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_handoff_codes
    ADD CONSTRAINT uk_session_handoff_codes_signup UNIQUE (signup_request_id);


--
-- Name: signup_requests uk_signup_requests_id_tenant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.signup_requests
    ADD CONSTRAINT uk_signup_requests_id_tenant UNIQUE (id, tenant_id);


--
-- Name: signup_requests uk_signup_requests_idempotency_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.signup_requests
    ADD CONSTRAINT uk_signup_requests_idempotency_key UNIQUE (idempotency_key);


--
-- Name: signup_requests uk_signup_requests_public_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.signup_requests
    ADD CONSTRAINT uk_signup_requests_public_id UNIQUE (public_id);


--
-- Name: channel_sku_mapping uk_sku_mapping; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_sku_mapping
    ADD CONSTRAINT uk_sku_mapping UNIQUE (company_id, channel, external_sku);


--
-- Name: subscription_plans uk_subscription_plans_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_plans
    ADD CONSTRAINT uk_subscription_plans_code UNIQUE (plan_code);


--
-- Name: sys_user_warehouse uk_sys_user_warehouse_company_user_warehouse; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_warehouse
    ADD CONSTRAINT uk_sys_user_warehouse_company_user_warehouse UNIQUE (company_id, user_id, warehouse_id);


--
-- Name: tenant_domains uk_tenant_domains_hostname; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_domains
    ADD CONSTRAINT uk_tenant_domains_hostname UNIQUE (hostname);


--
-- Name: tenant_memberships uk_tenant_memberships_identity_tenant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT uk_tenant_memberships_identity_tenant UNIQUE (identity_id, tenant_id);


--
-- Name: tenant_memberships uk_tenant_memberships_tenant_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT uk_tenant_memberships_tenant_user UNIQUE (tenant_id, tenant_user_id);


--
-- Name: tenant_purge_jobs uk_tenant_purge_jobs_tenant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_purge_jobs
    ADD CONSTRAINT uk_tenant_purge_jobs_tenant UNIQUE (tenant_id);


--
-- Name: tenant_subscriptions uk_tenant_subscriptions_current; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_subscriptions
    ADD CONSTRAINT uk_tenant_subscriptions_current UNIQUE (tenant_id);


--
-- Name: tenants uk_tenants_slug; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT uk_tenants_slug UNIQUE (slug);


--
-- Name: tenants uk_tenants_tenant_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT uk_tenants_tenant_code UNIQUE (tenant_code);


--
-- Name: user_identities uk_user_identities_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_identities
    ADD CONSTRAINT uk_user_identities_email UNIQUE (normalized_email);


--
-- Name: user_identities user_identities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_identities
    ADD CONSTRAINT user_identities_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: warehouses warehouses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_pkey PRIMARY KEY (id);


--
-- Name: idx_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_active ON public.inventory_batch USING btree (active);


--
-- Name: idx_approval_template_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_approval_template_lookup ON public.sys_approval_template USING btree (company_id, object_type, approval_mode, status);


--
-- Name: idx_backorder_priority_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backorder_priority_created ON public.backorder_line USING btree (priority, created_at);


--
-- Name: idx_backorder_product_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backorder_product_status ON public.backorder_line USING btree (product_sku_id, status);


--
-- Name: idx_backorder_sales_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backorder_sales_order ON public.backorder_line USING btree (sales_order_id);


--
-- Name: idx_batch_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_code ON public.inventory_batch USING btree (batch_code);


--
-- Name: idx_batch_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_location ON public.inventory_batch USING btree (batch_code, location_code);


--
-- Name: idx_categories_company_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_company_enabled ON public.categories USING btree (company_id, enabled);


--
-- Name: idx_categories_company_parent_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_company_parent_sort ON public.categories USING btree (company_id, parent_id, sort_order, id);


--
-- Name: idx_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_created_at ON public.stock_transactions USING btree (created_at);


--
-- Name: idx_customer_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_active ON public.customers USING btree (is_active);


--
-- Name: idx_customer_fact_summary_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_fact_summary_customer ON public.customer_fact_summary USING btree (customer_id);


--
-- Name: idx_customer_fact_summary_refreshed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_fact_summary_refreshed ON public.customer_fact_summary USING btree (refreshed_at);


--
-- Name: idx_customer_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_owner ON public.customers USING btree (owner_id);


--
-- Name: idx_customer_product_summary_customer_rank; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_product_summary_customer_rank ON public.customer_product_summary USING btree (company_id, customer_id, total_amount DESC);


--
-- Name: idx_customer_product_summary_refreshed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_product_summary_refreshed ON public.customer_product_summary USING btree (refreshed_at);


--
-- Name: idx_customer_product_summary_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_product_summary_sku ON public.customer_product_summary USING btree (company_id, product_sku_id);


--
-- Name: idx_customers_company_type_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customers_company_type_active ON public.customers USING btree (company_id, customer_type, is_active) WHERE (is_deleted = false);


--
-- Name: idx_email_challenges_signup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_challenges_signup ON public.email_verification_challenges USING btree (signup_request_id);


--
-- Name: idx_emergency_correction_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_emergency_correction_product ON public.emergency_stock_correction USING btree (product_sku_id);


--
-- Name: idx_emergency_correction_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_emergency_correction_status ON public.emergency_stock_correction USING btree (status);


--
-- Name: idx_entry_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_entry_date ON public.inventory_batch USING btree (entry_date);


--
-- Name: idx_expiry_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expiry_date ON public.purchase_order_item USING btree (expiry_date);


--
-- Name: idx_historical_archive_audit_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_historical_archive_audit_order ON public.historical_test_data_archive_audit USING btree (company_id, sales_order_id, created_at DESC);


--
-- Name: idx_idempotency_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_idempotency_status ON public.idempotency_request USING btree (status);


--
-- Name: idx_inbound_applicant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inbound_applicant ON public.inbound_orders USING btree (applicant_id);


--
-- Name: idx_inbound_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inbound_created_at ON public.inbound_orders USING btree (created_at);


--
-- Name: idx_inbound_item_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inbound_item_batch ON public.inbound_order_items USING btree (batch_code);


--
-- Name: idx_inbound_item_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inbound_item_expiry ON public.inbound_order_items USING btree (expiry_date);


--
-- Name: idx_inbound_item_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inbound_item_order ON public.inbound_order_items USING btree (inbound_order_id);


--
-- Name: idx_inbound_item_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inbound_item_product ON public.inbound_order_items USING btree (product_sku_id);


--
-- Name: idx_inbound_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inbound_status ON public.inbound_orders USING btree (status);


--
-- Name: idx_inbound_supplier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inbound_supplier ON public.inbound_orders USING btree (supplier_id);


--
-- Name: idx_integration_configs_company_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_integration_configs_company_active ON public.integration_configs USING btree (company_id, platform, is_active);


--
-- Name: idx_inventory_batch_company_product_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_batch_company_product_sku ON public.inventory_batch USING btree (company_id, product_sku_id);


--
-- Name: idx_inventory_batch_fefo_fragment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_batch_fefo_fragment ON public.inventory_batch USING btree (product_sku_id, active, expiry_date, quantity, id);


--
-- Name: idx_inventory_batch_reserved_quantity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_batch_reserved_quantity ON public.inventory_batch USING btree (reserved_quantity);


--
-- Name: idx_inventory_company_product_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_company_product_sku ON public.inventory USING btree (company_id, product_sku_id);


--
-- Name: idx_inventory_reservations_company_product_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_reservations_company_product_sku ON public.inventory_reservations USING btree (company_id, product_sku_id);


--
-- Name: idx_location_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_code ON public.inventory_batch USING btree (location_code);


--
-- Name: idx_location_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_id ON public.inventory USING btree (location_id);


--
-- Name: idx_location_warehouse_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_warehouse_id ON public.locations USING btree (warehouse_id);


--
-- Name: idx_locations_coordinates; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locations_coordinates ON public.locations USING btree (pos_x, pos_y);


--
-- Name: idx_locations_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locations_status ON public.locations USING btree (status);


--
-- Name: idx_outbound_task_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbound_task_batch ON public.outbound_tasks USING btree (assigned_batch_id);


--
-- Name: idx_outbound_task_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbound_task_item ON public.outbound_tasks USING btree (sales_order_item_id);


--
-- Name: idx_outbound_task_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbound_task_order ON public.outbound_tasks USING btree (sales_order_id);


--
-- Name: idx_outbound_task_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbound_task_reservation ON public.outbound_tasks USING btree (reservation_id);


--
-- Name: idx_outbound_task_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbound_task_status ON public.outbound_tasks USING btree (status);


--
-- Name: idx_outbound_tasks_archive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbound_tasks_archive ON public.outbound_tasks USING btree (company_id, archived_at DESC) WHERE ((status)::text = 'VOIDED'::text);


--
-- Name: idx_outbound_tasks_company_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbound_tasks_company_status ON public.outbound_tasks USING btree (company_id, status);


--
-- Name: idx_outbox_aggregate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_aggregate ON public.domain_outbox USING btree (aggregate_type, aggregate_id);


--
-- Name: idx_outbox_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_status_created ON public.domain_outbox USING btree (status, created_at);


--
-- Name: idx_pending_sku_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pending_sku_status ON public.pending_sku_mapping USING btree (company_id, channel, status);


--
-- Name: idx_permission_governance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_governance ON public.sys_permission USING btree (company_id, risk_level, custom_assignable);


--
-- Name: idx_permission_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_parent ON public.sys_permission USING btree (parent_id);


--
-- Name: idx_permission_request_audit_operator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_request_audit_operator ON public.sys_permission_request_audit USING btree (company_id, operator_id, created_at DESC);


--
-- Name: idx_permission_request_audit_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_request_audit_request ON public.sys_permission_request_audit USING btree (company_id, permission_request_id, created_at DESC);


--
-- Name: idx_permission_request_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_request_role ON public.sys_permission_request USING btree (company_id, requested_role_id, submitted_at DESC);


--
-- Name: idx_permission_request_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_request_status ON public.sys_permission_request USING btree (company_id, status, submitted_at DESC);


--
-- Name: idx_permission_request_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_request_target ON public.sys_permission_request USING btree (company_id, target_user_id, submitted_at DESC);


--
-- Name: idx_permission_request_warehouse_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_request_warehouse_request ON public.sys_permission_request_warehouse USING btree (permission_request_id);


--
-- Name: idx_permission_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_resource ON public.sys_permission USING btree (resource_path, http_method);


--
-- Name: idx_permission_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_type_status ON public.sys_permission USING btree (permission_type, status);


--
-- Name: idx_platform_access_grant_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_access_grant_lookup ON public.platform_access_grants USING btree (grantee_platform_user_id, capability, tenant_id, dataset_code, effective_from, expires_at);


--
-- Name: idx_platform_access_grants_active_grantee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_access_grants_active_grantee ON public.platform_access_grants USING btree (grantee_platform_user_id, capability, effective_from, expires_at) WHERE (revoked_at IS NULL);


--
-- Name: idx_platform_admin_command_target_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_admin_command_target_created ON public.platform_admin_commands USING btree (target_platform_user_id, created_at DESC);


--
-- Name: idx_platform_admin_invitation_activation_invitation_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_admin_invitation_activation_invitation_created ON public.platform_admin_invitation_activations USING btree (invitation_id, created_at DESC);


--
-- Name: idx_platform_admin_invitation_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_admin_invitation_email ON public.platform_admin_invitations USING btree (normalized_email, created_at DESC);


--
-- Name: idx_platform_admin_invitation_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_admin_invitation_expiry ON public.platform_admin_invitations USING btree (expires_at);


--
-- Name: idx_platform_audit_actor_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_audit_actor_created ON public.platform_audit_logs USING btree (platform_user_id, created_at);


--
-- Name: idx_platform_audit_target_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_audit_target_user_created ON public.platform_audit_logs USING btree (target_platform_user_id, created_at DESC);


--
-- Name: idx_platform_audit_tenant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_audit_tenant_created ON public.platform_audit_logs USING btree (target_tenant_id, created_at);


--
-- Name: idx_platform_export_jobs_actor_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_export_jobs_actor_created ON public.platform_export_jobs USING btree (platform_user_id, created_at DESC);


--
-- Name: idx_platform_export_jobs_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_export_jobs_expiry ON public.platform_export_jobs USING btree (expires_at) WHERE ((status)::text = 'COMPLETED'::text);


--
-- Name: idx_platform_mfa_challenge_target_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_mfa_challenge_target_user ON public.platform_mfa_challenges USING btree (target_platform_user_id);


--
-- Name: idx_platform_mfa_challenge_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_mfa_challenge_user_created ON public.platform_mfa_challenges USING btree (platform_user_id, created_at);


--
-- Name: idx_platform_operation_auth_company_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_operation_auth_company_status ON public.platform_operation_authorizations USING btree (tenant_id, status, created_at DESC);


--
-- Name: idx_platform_operation_auth_tenant_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_operation_auth_tenant_expiry ON public.platform_operation_authorizations USING btree (tenant_id, expires_at);


--
-- Name: idx_platform_user_roles_role_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_user_roles_role_user ON public.platform_user_roles USING btree (platform_role_id, platform_user_id);


--
-- Name: idx_platform_users_admin_directory; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_users_admin_directory ON public.platform_users USING btree (created_at DESC, id DESC);


--
-- Name: idx_product_expiry_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_expiry_active ON public.inventory_batch USING btree (product_sku_id, expiry_date, active);


--
-- Name: idx_product_skus_company_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_skus_company_name ON public.product_skus USING btree (company_id, name);


--
-- Name: idx_product_skus_company_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_skus_company_product ON public.product_skus USING btree (company_id, product_id);


--
-- Name: idx_products_company_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_company_category ON public.products USING btree (company_id, category_id);


--
-- Name: idx_products_company_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_company_name ON public.products USING btree (company_id, product_name);


--
-- Name: idx_purchase_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_purchase_order_id ON public.purchase_order_item USING btree (purchase_order_id);


--
-- Name: idx_purchase_order_supplier_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_purchase_order_supplier_id ON public.purchase_order USING btree (supplier_id);


--
-- Name: idx_raw_events_channel_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_raw_events_channel_status ON public.channel_raw_events USING btree (channel, status);


--
-- Name: idx_raw_events_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_raw_events_created ON public.channel_raw_events USING btree (created_at);


--
-- Name: idx_raw_events_external; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_raw_events_external ON public.channel_raw_events USING btree (channel, event_type, external_id);


--
-- Name: idx_raw_events_webhook_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_raw_events_webhook_id ON public.channel_raw_events USING btree (webhook_event_id);


--
-- Name: idx_reservation_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_batch ON public.inventory_reservations USING btree (inventory_batch_id);


--
-- Name: idx_reservation_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_item ON public.inventory_reservations USING btree (sales_order_item_id);


--
-- Name: idx_reservation_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_order ON public.inventory_reservations USING btree (sales_order_id);


--
-- Name: idx_reservation_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_status ON public.inventory_reservations USING btree (status);


--
-- Name: idx_role_copy_audit_operator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_copy_audit_operator ON public.sys_role_copy_audit USING btree (company_id, operator_id, created_at DESC);


--
-- Name: idx_role_copy_audit_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_copy_audit_source ON public.sys_role_copy_audit USING btree (company_id, source_role_id, created_at DESC);


--
-- Name: idx_role_copy_audit_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_copy_audit_target ON public.sys_role_copy_audit USING btree (company_id, target_role_id);


--
-- Name: idx_role_governance_audit_operator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_governance_audit_operator ON public.sys_role_governance_audit USING btree (company_id, operator_id, created_at DESC);


--
-- Name: idx_role_governance_audit_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_governance_audit_role ON public.sys_role_governance_audit USING btree (company_id, role_id, created_at DESC);


--
-- Name: idx_role_inherit_child; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_inherit_child ON public.sys_role_inherit USING btree (child_role_id);


--
-- Name: idx_role_inherit_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_inherit_parent ON public.sys_role_inherit USING btree (parent_role_id);


--
-- Name: idx_role_permission_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_permission_permission ON public.sys_role_permission USING btree (permission_id);


--
-- Name: idx_role_permission_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_permission_role ON public.sys_role_permission USING btree (role_id);


--
-- Name: idx_role_review_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_review_queue ON public.sys_role USING btree (company_id, role_type, review_status, status);


--
-- Name: idx_role_template_governance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_template_governance ON public.sys_role USING btree (company_id, system_category, import_allowed);


--
-- Name: idx_role_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_type_status ON public.sys_role USING btree (role_type, status);


--
-- Name: idx_sales_applicant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_applicant ON public.sales_orders USING btree (applicant_id);


--
-- Name: idx_sales_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_created_at ON public.sales_orders USING btree (created_at);


--
-- Name: idx_sales_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_customer ON public.sales_orders USING btree (customer_id);


--
-- Name: idx_sales_daily_summary_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_daily_summary_date ON public.sales_daily_summary USING btree (summary_date);


--
-- Name: idx_sales_daily_summary_refreshed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_daily_summary_refreshed ON public.sales_daily_summary USING btree (refreshed_at);


--
-- Name: idx_sales_item_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_item_order ON public.sales_order_items USING btree (sales_order_id);


--
-- Name: idx_sales_item_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_item_product ON public.sales_order_items USING btree (product_sku_id);


--
-- Name: idx_sales_order_items_fulfillment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_items_fulfillment_status ON public.sales_order_items USING btree (fulfillment_status);


--
-- Name: idx_sales_order_shipment_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_shipment_order ON public.sales_order_shipments USING btree (company_id, sales_order_id, status);


--
-- Name: idx_sales_orders_commercial_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_orders_commercial_status ON public.sales_orders USING btree (commercial_status);


--
-- Name: idx_sales_orders_company_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_orders_company_status ON public.sales_orders USING btree (company_id, status);


--
-- Name: idx_sales_orders_fulfillment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_orders_fulfillment_status ON public.sales_orders USING btree (fulfillment_status);


--
-- Name: idx_sales_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_status ON public.sales_orders USING btree (status);


--
-- Name: idx_session_handoff_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_session_handoff_expiry ON public.session_handoff_codes USING btree (expires_at) WHERE (consumed_at IS NULL);


--
-- Name: idx_shelf_position; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shelf_position ON public.locations USING btree (shelfnumber, positionnumber);


--
-- Name: idx_signup_requests_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_signup_requests_email ON public.signup_requests USING btree (normalized_email);


--
-- Name: idx_signup_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_signup_requests_status ON public.signup_requests USING btree (status);


--
-- Name: idx_sku_mapping_normalized; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sku_mapping_normalized ON public.channel_sku_mapping USING btree (company_id, channel, normalized_sku);


--
-- Name: idx_source_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_order_id ON public.stock_transactions USING btree (sourceorderid);


--
-- Name: idx_source_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_type ON public.stock_transactions USING btree (sourcetype);


--
-- Name: idx_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_status ON public.purchase_order USING btree (status);


--
-- Name: idx_stock_transactions_company_product_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_transactions_company_product_sku ON public.stock_transactions USING btree (company_id, product_sku_id);


--
-- Name: idx_stocktake_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_created_at ON public.stocktake_tasks USING btree (created_at);


--
-- Name: idx_stocktake_cycle_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_cycle_type ON public.stocktake_tasks USING btree (cycle_type);


--
-- Name: idx_stocktake_item_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_item_batch ON public.stocktake_items USING btree (batch_id);


--
-- Name: idx_stocktake_item_counted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_item_counted ON public.stocktake_items USING btree (is_counted);


--
-- Name: idx_stocktake_item_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_item_location ON public.stocktake_items USING btree (location_id);


--
-- Name: idx_stocktake_item_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_item_product ON public.stocktake_items USING btree (product_sku_id);


--
-- Name: idx_stocktake_item_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_item_task ON public.stocktake_items USING btree (task_id);


--
-- Name: idx_stocktake_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_status ON public.stocktake_tasks USING btree (status);


--
-- Name: idx_stocktake_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_warehouse ON public.stocktake_tasks USING btree (warehouse_id);


--
-- Name: idx_supplier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supplier ON public.purchase_order USING btree (supplier);


--
-- Name: idx_supplier_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supplier_active ON public.suppliers USING btree (is_active);


--
-- Name: idx_supplier_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supplier_code ON public.suppliers USING btree (code);


--
-- Name: idx_suppliers_company_active_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_suppliers_company_active_deleted ON public.suppliers USING btree (company_id, is_active, is_deleted);


--
-- Name: idx_template_type_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_template_type_active ON public.sys_excel_templates USING btree (template_type, is_active);


--
-- Name: idx_tenant_domains_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_domains_status ON public.tenant_domains USING btree (status);


--
-- Name: idx_tenant_domains_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_domains_tenant ON public.tenant_domains USING btree (tenant_id);


--
-- Name: idx_tenant_memberships_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_memberships_tenant ON public.tenant_memberships USING btree (tenant_id);


--
-- Name: idx_tenant_purge_audit_tenant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_purge_audit_tenant_created ON public.tenant_purge_audit_logs USING btree (tenant_id, created_at DESC);


--
-- Name: idx_tenant_purge_jobs_status_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_purge_jobs_status_lease ON public.tenant_purge_jobs USING btree (status, lease_expires_at);


--
-- Name: idx_tenant_session_security_audit_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_session_security_audit_target ON public.tenant_session_security_audit USING btree (company_id, target_user_id, created_at DESC);


--
-- Name: idx_tenant_subscriptions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_subscriptions_status ON public.tenant_subscriptions USING btree (status);


--
-- Name: idx_tenants_purge_due_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenants_purge_due_at ON public.tenants USING btree (purge_due_at);


--
-- Name: idx_tenants_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenants_status ON public.tenants USING btree (status);


--
-- Name: idx_transaction_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaction_type ON public.stock_transactions USING btree (transactiontype);


--
-- Name: idx_user_role_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_role_role ON public.sys_user_role USING btree (role_id);


--
-- Name: idx_user_role_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_role_user ON public.sys_user_role USING btree (user_id);


--
-- Name: idx_user_warehouse_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_warehouse_user ON public.sys_user_warehouse USING btree (user_id);


--
-- Name: idx_user_warehouse_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_warehouse_warehouse ON public.sys_user_warehouse USING btree (warehouse_id);


--
-- Name: idx_users_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_active ON public.users USING btree (is_deleted, enabled);


--
-- Name: idx_users_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_deleted_at ON public.users USING btree (deleted_at) WHERE (is_deleted = true);


--
-- Name: idx_warehouse_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warehouse_active ON public.warehouses USING btree (is_active);


--
-- Name: idx_warehouse_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warehouse_code ON public.warehouses USING btree (code);


--
-- Name: idx_zone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_zone ON public.locations USING btree (zone);


--
-- Name: uk_categories_company_parent_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_categories_company_parent_name ON public.categories USING btree (company_id, COALESCE(parent_id, (0)::bigint), lower((category_name)::text));


--
-- Name: uk_channel_raw_events_company_webhook_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_channel_raw_events_company_webhook_id ON public.channel_raw_events USING btree (company_id, webhook_event_id) WHERE (webhook_event_id IS NOT NULL);


--
-- Name: uk_channel_webhook_routes_active_store; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_channel_webhook_routes_active_store ON public.channel_webhook_routes USING btree (platform, canonical_store_identifier) WHERE (active = true);


--
-- Name: uk_customers_company_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_customers_company_code ON public.customers USING btree (company_id, code);


--
-- Name: uk_emergency_correction_company_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_emergency_correction_company_no ON public.emergency_stock_correction USING btree (company_id, correction_no);


--
-- Name: uk_idempotency_company_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_idempotency_company_key ON public.idempotency_request USING btree (company_id, idempotency_key);


--
-- Name: uk_inbound_orders_company_order_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_inbound_orders_company_order_no ON public.inbound_orders USING btree (company_id, order_no);


--
-- Name: uk_integration_configs_active_store_route; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_integration_configs_active_store_route ON public.integration_configs USING btree (platform, canonical_store_identifier) WHERE (is_active = true);


--
-- Name: uk_inventory_batch_company_batch_location; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_inventory_batch_company_batch_location ON public.inventory_batch USING btree (company_id, batch_code, location_code);


--
-- Name: uk_inventory_company_product_sku_location; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_inventory_company_product_sku_location ON public.inventory USING btree (company_id, product_sku_id, location_id);


--
-- Name: uk_locations_company_locationcode; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_locations_company_locationcode ON public.locations USING btree (company_id, locationcode);


--
-- Name: uk_locations_company_warehouseposition; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_locations_company_warehouseposition ON public.locations USING btree (company_id, warehousecode, zone, shelfnumber, positionnumber);


--
-- Name: uk_permission_request_pending_target_role; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_permission_request_pending_target_role ON public.sys_permission_request USING btree (company_id, target_user_id, requested_role_id) WHERE ((status)::text = 'PENDING_REVIEW'::text);


--
-- Name: uk_platform_operation_auth_execution_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_platform_operation_auth_execution_key ON public.platform_operation_authorizations USING btree (execution_key) WHERE (execution_key IS NOT NULL);


--
-- Name: uk_product_skus_company_barcode; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_product_skus_company_barcode ON public.product_skus USING btree (company_id, barcode);


--
-- Name: uk_product_skus_company_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_product_skus_company_code ON public.product_skus USING btree (company_id, sku_code);


--
-- Name: uk_products_company_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_products_company_code ON public.products USING btree (company_id, product_code);


--
-- Name: uk_purchase_order_company_po_number; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_purchase_order_company_po_number ON public.purchase_order USING btree (company_id, po_number);


--
-- Name: uk_sales_orders_company_order_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sales_orders_company_order_no ON public.sales_orders USING btree (company_id, order_no);


--
-- Name: uk_signup_requests_active_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_signup_requests_active_email ON public.signup_requests USING btree (normalized_email) WHERE ((status)::text = ANY ((ARRAY['EMAIL_PENDING'::character varying, 'EMAIL_VERIFIED'::character varying, 'DETAILS_COMPLETED'::character varying, 'PROVISIONING'::character varying, 'ACTIVE'::character varying])::text[]));


--
-- Name: uk_stocktake_tasks_company_task_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_stocktake_tasks_company_task_no ON public.stocktake_tasks USING btree (company_id, task_no);


--
-- Name: uk_suppliers_company_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_suppliers_company_code ON public.suppliers USING btree (company_id, code);


--
-- Name: uk_sys_permission_company_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sys_permission_company_code ON public.sys_permission USING btree (company_id, permission_code);


--
-- Name: uk_sys_role_company_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sys_role_company_code ON public.sys_role USING btree (company_id, role_code);


--
-- Name: uk_sys_role_inherit_company_child_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sys_role_inherit_company_child_parent ON public.sys_role_inherit USING btree (company_id, child_role_id, parent_role_id);


--
-- Name: uk_sys_role_permission_company_role_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sys_role_permission_company_role_permission ON public.sys_role_permission USING btree (company_id, role_id, permission_id);


--
-- Name: uk_sys_user_role_company_user_role; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sys_user_role_company_user_role ON public.sys_user_role USING btree (company_id, user_id, role_id);


--
-- Name: uk_system_config_company_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_system_config_company_key ON public.system_config USING btree (company_id, config_key);


--
-- Name: uk_tenant_domains_primary; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_tenant_domains_primary ON public.tenant_domains USING btree (tenant_id) WHERE is_primary;


--
-- Name: uk_tenant_memberships_single_live_company; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_tenant_memberships_single_live_company ON public.tenant_memberships USING btree (identity_id) WHERE ((status)::text = ANY ((ARRAY['INVITED'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[]));


--
-- Name: uk_users_company_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_users_company_id ON public.users USING btree (company_id, id);


--
-- Name: uk_users_company_username; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_users_company_username ON public.users USING btree (company_id, username);


--
-- Name: uk_warehouses_company_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_warehouses_company_code ON public.warehouses USING btree (company_id, code);


--
-- Name: uq_customers_company_consumer_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customers_company_consumer_email ON public.customers USING btree (company_id, normalized_email) WHERE (((customer_type)::text = 'CONSUMER'::text) AND (is_deleted = false) AND (normalized_email IS NOT NULL));


--
-- Name: uq_customers_company_consumer_external_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customers_company_consumer_external_id ON public.customers USING btree (company_id, external_customer_id) WHERE (((customer_type)::text = 'CONSUMER'::text) AND (is_deleted = false) AND (external_customer_id IS NOT NULL));


--
-- Name: uq_sales_orders_company_channel_external_order; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_sales_orders_company_channel_external_order ON public.sales_orders USING btree (company_id, channel, external_order_id) WHERE (external_order_id IS NOT NULL);


--
-- Name: INDEX uq_sales_orders_company_channel_external_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_sales_orders_company_channel_external_order IS 'Prevents duplicate channel orders within a company while allowing the same external id across channels or companies.';


--
-- Name: historical_test_data_archive_audit trg_historical_archive_audit_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_historical_archive_audit_immutable BEFORE DELETE OR UPDATE ON public.historical_test_data_archive_audit FOR EACH ROW EXECUTE FUNCTION public.prevent_historical_archive_audit_mutation();


--
-- Name: historical_test_data_registry trg_historical_test_registry_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_historical_test_registry_immutable BEFORE DELETE OR UPDATE ON public.historical_test_data_registry FOR EACH ROW EXECUTE FUNCTION public.prevent_historical_test_registry_mutation();


--
-- Name: sys_permission_request_audit trg_permission_request_audit_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_permission_request_audit_immutable BEFORE DELETE OR UPDATE ON public.sys_permission_request_audit FOR EACH ROW EXECUTE FUNCTION public.prevent_permission_request_audit_mutation();


--
-- Name: platform_audit_logs trg_platform_audit_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_platform_audit_append_only BEFORE DELETE OR UPDATE ON public.platform_audit_logs FOR EACH ROW EXECUTE FUNCTION public._wms_reject_platform_audit_mutation();


--
-- Name: tenant_purge_audit_logs trg_tenant_purge_audit_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tenant_purge_audit_immutable BEFORE DELETE OR UPDATE ON public.tenant_purge_audit_logs FOR EACH ROW EXECUTE FUNCTION public.prevent_tenant_purge_audit_mutation();


--
-- Name: channel_inventory_state channel_inventory_state_mapping_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_inventory_state
    ADD CONSTRAINT channel_inventory_state_mapping_id_fkey FOREIGN KEY (mapping_id) REFERENCES public.channel_sku_mapping(id) ON DELETE CASCADE;


--
-- Name: stocktake_items fk4wfypywwrnvyxq4hq80xiv1wk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT fk4wfypywwrnvyxq4hq80xiv1wk FOREIGN KEY (batch_id) REFERENCES public.inventory_batch(id);


--
-- Name: outbound_tasks fk7esbu7o1rkbs33bqv8njurfe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbound_tasks
    ADD CONSTRAINT fk7esbu7o1rkbs33bqv8njurfe FOREIGN KEY (sales_order_item_id) REFERENCES public.sales_order_items(id);


--
-- Name: users fk8levk3k150bxaink6uaw9ofid; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk8levk3k150bxaink6uaw9ofid FOREIGN KEY (default_role_id) REFERENCES public.sys_role(id);


--
-- Name: sys_role_permission fk9q28ewrhntqeipl1t04kh1be7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_permission
    ADD CONSTRAINT fk9q28ewrhntqeipl1t04kh1be7 FOREIGN KEY (role_id) REFERENCES public.sys_role(id);


--
-- Name: backorder_line fk_backorder_line_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.backorder_line
    ADD CONSTRAINT fk_backorder_line_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: inventory_batch fk_batch_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_batch
    ADD CONSTRAINT fk_batch_location FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: inventory_batch fk_batch_po_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_batch
    ADD CONSTRAINT fk_batch_po_item FOREIGN KEY (purchase_order_item_id) REFERENCES public.purchase_order_item(id);


--
-- Name: categories fk_categories_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES public.categories(id) ON DELETE RESTRICT;


--
-- Name: channel_sku_mapping fk_channel_sku_mapping_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_sku_mapping
    ADD CONSTRAINT fk_channel_sku_mapping_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: channel_webhook_routes fk_channel_webhook_routes_config; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_webhook_routes
    ADD CONSTRAINT fk_channel_webhook_routes_config FOREIGN KEY (integration_config_id) REFERENCES public.integration_configs(id) ON DELETE CASCADE;


--
-- Name: channel_webhook_routes fk_channel_webhook_routes_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.channel_webhook_routes
    ADD CONSTRAINT fk_channel_webhook_routes_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: email_verification_challenges fk_email_challenges_signup; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_challenges
    ADD CONSTRAINT fk_email_challenges_signup FOREIGN KEY (signup_request_id) REFERENCES public.signup_requests(id);


--
-- Name: emergency_stock_correction fk_emergency_correction_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.emergency_stock_correction
    ADD CONSTRAINT fk_emergency_correction_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: inbound_order_items fk_inbound_item_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_order_items
    ADD CONSTRAINT fk_inbound_item_location FOREIGN KEY (target_location_id) REFERENCES public.locations(id);


--
-- Name: inbound_order_items fk_inbound_item_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_order_items
    ADD CONSTRAINT fk_inbound_item_order FOREIGN KEY (inbound_order_id) REFERENCES public.inbound_orders(id);


--
-- Name: inbound_order_items fk_inbound_item_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_order_items
    ADD CONSTRAINT fk_inbound_item_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: inbound_order_items fk_inbound_item_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_order_items
    ADD CONSTRAINT fk_inbound_item_warehouse FOREIGN KEY (target_warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: inbound_orders fk_inbound_supplier; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_orders
    ADD CONSTRAINT fk_inbound_supplier FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: inventory_batch fk_inventory_batch_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_batch
    ADD CONSTRAINT fk_inventory_batch_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: inventory fk_inventory_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT fk_inventory_location FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: inventory fk_inventory_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT fk_inventory_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: inventory_reservations fk_inventory_reservation_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_reservations
    ADD CONSTRAINT fk_inventory_reservation_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: locations fk_location_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: sys_permission_request_warehouse fk_permission_request_warehouse_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission_request_warehouse
    ADD CONSTRAINT fk_permission_request_warehouse_request FOREIGN KEY (permission_request_id) REFERENCES public.sys_permission_request(id) ON DELETE CASCADE;


--
-- Name: plan_limits fk_plan_limits_plan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_limits
    ADD CONSTRAINT fk_plan_limits_plan FOREIGN KEY (plan_id) REFERENCES public.subscription_plans(id);


--
-- Name: platform_admin_commands fk_platform_admin_command_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_commands
    ADD CONSTRAINT fk_platform_admin_command_actor FOREIGN KEY (actor_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_admin_commands fk_platform_admin_command_target; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_commands
    ADD CONSTRAINT fk_platform_admin_command_target FOREIGN KEY (target_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_admin_invitations fk_platform_admin_invitation_accepted_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitations
    ADD CONSTRAINT fk_platform_admin_invitation_accepted_user FOREIGN KEY (accepted_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_admin_invitation_activations fk_platform_admin_invitation_activation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitation_activations
    ADD CONSTRAINT fk_platform_admin_invitation_activation FOREIGN KEY (invitation_id) REFERENCES public.platform_admin_invitations(id);


--
-- Name: platform_admin_invitation_active_emails fk_platform_admin_invitation_active_email; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitation_active_emails
    ADD CONSTRAINT fk_platform_admin_invitation_active_email FOREIGN KEY (invitation_id) REFERENCES public.platform_admin_invitations(id);


--
-- Name: platform_admin_invitations fk_platform_admin_invitation_inviter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitations
    ADD CONSTRAINT fk_platform_admin_invitation_inviter FOREIGN KEY (invited_by_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_admin_invitations fk_platform_admin_invitation_revoker; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_admin_invitations
    ADD CONSTRAINT fk_platform_admin_invitation_revoker FOREIGN KEY (revoked_by_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_audit_logs fk_platform_audit_target_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_audit_logs
    ADD CONSTRAINT fk_platform_audit_target_user FOREIGN KEY (target_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_audit_logs fk_platform_audit_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_audit_logs
    ADD CONSTRAINT fk_platform_audit_tenant FOREIGN KEY (target_tenant_id) REFERENCES public.tenants(id);


--
-- Name: platform_audit_logs fk_platform_audit_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_audit_logs
    ADD CONSTRAINT fk_platform_audit_user FOREIGN KEY (platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_export_jobs fk_platform_export_jobs_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_export_jobs
    ADD CONSTRAINT fk_platform_export_jobs_tenant FOREIGN KEY (target_tenant_id) REFERENCES public.tenants(id);


--
-- Name: platform_export_jobs fk_platform_export_jobs_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_export_jobs
    ADD CONSTRAINT fk_platform_export_jobs_user FOREIGN KEY (platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_mfa_challenges fk_platform_mfa_challenge_target_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_mfa_challenges
    ADD CONSTRAINT fk_platform_mfa_challenge_target_user FOREIGN KEY (target_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_mfa_challenges fk_platform_mfa_challenge_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_mfa_challenges
    ADD CONSTRAINT fk_platform_mfa_challenge_user FOREIGN KEY (platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_operation_authorizations fk_platform_operation_auth_approver; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_operation_authorizations
    ADD CONSTRAINT fk_platform_operation_auth_approver FOREIGN KEY (tenant_id, approved_by_tenant_user_id) REFERENCES public.users(company_id, id);


--
-- Name: platform_operation_authorizations fk_platform_operation_auth_executor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_operation_authorizations
    ADD CONSTRAINT fk_platform_operation_auth_executor FOREIGN KEY (executed_by_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_operation_authorizations fk_platform_operation_auth_requester; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_operation_authorizations
    ADD CONSTRAINT fk_platform_operation_auth_requester FOREIGN KEY (requested_by_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_operation_authorizations fk_platform_operation_auth_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_operation_authorizations
    ADD CONSTRAINT fk_platform_operation_auth_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: platform_user_roles fk_platform_user_roles_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_user_roles
    ADD CONSTRAINT fk_platform_user_roles_role FOREIGN KEY (platform_role_id) REFERENCES public.platform_roles(id);


--
-- Name: platform_user_roles fk_platform_user_roles_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_user_roles
    ADD CONSTRAINT fk_platform_user_roles_user FOREIGN KEY (platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: purchase_order_item fk_po_item_po; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_item
    ADD CONSTRAINT fk_po_item_po FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_order(id);


--
-- Name: product_skus fk_product_skus_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_skus
    ADD CONSTRAINT fk_product_skus_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: products fk_products_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES public.categories(id) ON DELETE RESTRICT;


--
-- Name: tenant_provisioning_jobs fk_provisioning_jobs_signup_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_provisioning_jobs
    ADD CONSTRAINT fk_provisioning_jobs_signup_tenant FOREIGN KEY (signup_request_id, tenant_id) REFERENCES public.signup_requests(id, tenant_id);


--
-- Name: tenant_provisioning_jobs fk_provisioning_jobs_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_provisioning_jobs
    ADD CONSTRAINT fk_provisioning_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_order_item fk_purchase_order_item_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_item
    ADD CONSTRAINT fk_purchase_order_item_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: purchase_order fk_purchase_order_supplier; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order
    ADD CONSTRAINT fk_purchase_order_supplier FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE RESTRICT;


--
-- Name: sales_order_items fk_sales_order_item_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_items
    ADD CONSTRAINT fk_sales_order_item_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: session_handoff_codes fk_session_handoff_identity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_handoff_codes
    ADD CONSTRAINT fk_session_handoff_identity FOREIGN KEY (identity_id) REFERENCES public.user_identities(id);


--
-- Name: session_handoff_codes fk_session_handoff_signup; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_handoff_codes
    ADD CONSTRAINT fk_session_handoff_signup FOREIGN KEY (signup_request_id) REFERENCES public.signup_requests(id);


--
-- Name: session_handoff_codes fk_session_handoff_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_handoff_codes
    ADD CONSTRAINT fk_session_handoff_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: session_handoff_codes fk_session_handoff_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_handoff_codes
    ADD CONSTRAINT fk_session_handoff_user FOREIGN KEY (tenant_id, tenant_user_id) REFERENCES public.users(company_id, id);


--
-- Name: signup_requests fk_signup_requests_identity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.signup_requests
    ADD CONSTRAINT fk_signup_requests_identity FOREIGN KEY (identity_id) REFERENCES public.user_identities(id);


--
-- Name: signup_requests fk_signup_requests_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.signup_requests
    ADD CONSTRAINT fk_signup_requests_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: stock_transactions fk_stock_transaction_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transactions
    ADD CONSTRAINT fk_stock_transaction_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: stocktake_items fk_stocktake_item_product_sku; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT fk_stocktake_item_product_sku FOREIGN KEY (product_sku_id) REFERENCES public.product_skus(id);


--
-- Name: tenant_domains fk_tenant_domains_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_domains
    ADD CONSTRAINT fk_tenant_domains_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: tenant_memberships fk_tenant_memberships_identity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT fk_tenant_memberships_identity FOREIGN KEY (identity_id) REFERENCES public.user_identities(id);


--
-- Name: tenant_memberships fk_tenant_memberships_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT fk_tenant_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: tenant_memberships fk_tenant_memberships_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT fk_tenant_memberships_user FOREIGN KEY (tenant_id, tenant_user_id) REFERENCES public.users(company_id, id);


--
-- Name: tenant_purge_audit_logs fk_tenant_purge_audit_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_purge_audit_logs
    ADD CONSTRAINT fk_tenant_purge_audit_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: tenant_purge_jobs fk_tenant_purge_jobs_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_purge_jobs
    ADD CONSTRAINT fk_tenant_purge_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: tenant_session_security_audit fk_tenant_session_audit_company; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_session_security_audit
    ADD CONSTRAINT fk_tenant_session_audit_company FOREIGN KEY (company_id) REFERENCES public.tenants(id);


--
-- Name: tenant_subscriptions fk_tenant_subscriptions_plan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_subscriptions
    ADD CONSTRAINT fk_tenant_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES public.subscription_plans(id);


--
-- Name: tenant_subscriptions fk_tenant_subscriptions_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_subscriptions
    ADD CONSTRAINT fk_tenant_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: stock_transactions fk_transaction_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transactions
    ADD CONSTRAINT fk_transaction_location FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: sys_user_warehouse fk_user_warehouse_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_warehouse
    ADD CONSTRAINT fk_user_warehouse_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: sys_user_warehouse fk_user_warehouse_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_warehouse
    ADD CONSTRAINT fk_user_warehouse_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id) ON DELETE RESTRICT;


--
-- Name: outbound_tasks fkdetu6wh3rll9ovqpv5p53qe4u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbound_tasks
    ADD CONSTRAINT fkdetu6wh3rll9ovqpv5p53qe4u FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: outbound_tasks fkdyhu92nq9haytitn1bm01q8t9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbound_tasks
    ADD CONSTRAINT fkdyhu92nq9haytitn1bm01q8t9 FOREIGN KEY (sales_order_id) REFERENCES public.sales_orders(id);


--
-- Name: sales_orders fkfs1owechmxg3lvej5vq1s8t8i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders
    ADD CONSTRAINT fkfs1owechmxg3lvej5vq1s8t8i FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: sys_user_role fkgr4o2eovp7ujd7bo0qmdo06nv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_role
    ADD CONSTRAINT fkgr4o2eovp7ujd7bo0qmdo06nv FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: sys_user_role fkhh52n8vd4ny9ff4x9fb8v65qx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_role
    ADD CONSTRAINT fkhh52n8vd4ny9ff4x9fb8v65qx FOREIGN KEY (role_id) REFERENCES public.sys_role(id);


--
-- Name: outbound_tasks fklxf5mkr39j8rr6oeynfw797gw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbound_tasks
    ADD CONSTRAINT fklxf5mkr39j8rr6oeynfw797gw FOREIGN KEY (assigned_batch_id) REFERENCES public.inventory_batch(id);


--
-- Name: sys_role_inherit fkmwd3gx7ao9074hfnlnamn4xpf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_inherit
    ADD CONSTRAINT fkmwd3gx7ao9074hfnlnamn4xpf FOREIGN KEY (child_role_id) REFERENCES public.sys_role(id);


--
-- Name: stocktake_tasks fkn847ciq22vbf43xh3oqvip3tk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_tasks
    ADD CONSTRAINT fkn847ciq22vbf43xh3oqvip3tk FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: stocktake_items fkokqph588390ruu9osup65pb03; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT fkokqph588390ruu9osup65pb03 FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: sys_role_permission fkomxrs8a388bknvhjokh440waq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_permission
    ADD CONSTRAINT fkomxrs8a388bknvhjokh440waq FOREIGN KEY (permission_id) REFERENCES public.sys_permission(id);


--
-- Name: stocktake_items fksndlw36a2e5te43vttvu0vx0k; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT fksndlw36a2e5te43vttvu0vx0k FOREIGN KEY (task_id) REFERENCES public.stocktake_tasks(id);


--
-- Name: sys_role_inherit fktfdk94ssaydm5nry8lpdeotqw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_inherit
    ADD CONSTRAINT fktfdk94ssaydm5nry8lpdeotqw FOREIGN KEY (parent_role_id) REFERENCES public.sys_role(id);


--
-- Name: sales_order_items fktrge001xfy0fc9961g11411re; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_items
    ADD CONSTRAINT fktrge001xfy0fc9961g11411re FOREIGN KEY (sales_order_id) REFERENCES public.sales_orders(id);


--
-- Name: historical_test_data_registry historical_test_data_registry_sales_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_test_data_registry
    ADD CONSTRAINT historical_test_data_registry_sales_order_id_fkey FOREIGN KEY (sales_order_id) REFERENCES public.sales_orders(id) ON DELETE RESTRICT;


--
-- Name: platform_access_grants platform_access_grants_granted_by_platform_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_access_grants
    ADD CONSTRAINT platform_access_grants_granted_by_platform_user_id_fkey FOREIGN KEY (granted_by_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_access_grants platform_access_grants_grantee_platform_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_access_grants
    ADD CONSTRAINT platform_access_grants_grantee_platform_user_id_fkey FOREIGN KEY (grantee_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_access_grants platform_access_grants_revoked_by_platform_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_access_grants
    ADD CONSTRAINT platform_access_grants_revoked_by_platform_user_id_fkey FOREIGN KEY (revoked_by_platform_user_id) REFERENCES public.platform_users(id);


--
-- Name: platform_access_grants platform_access_grants_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_access_grants
    ADD CONSTRAINT platform_access_grants_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: sales_order_shipments sales_order_shipments_sales_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_shipments
    ADD CONSTRAINT sales_order_shipments_sales_order_id_fkey FOREIGN KEY (sales_order_id) REFERENCES public.sales_orders(id) ON DELETE RESTRICT;


--
-- Name: backorder_line; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.backorder_line ENABLE ROW LEVEL SECURITY;

--
-- Name: categories; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;

--
-- Name: channel_inventory_state; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.channel_inventory_state ENABLE ROW LEVEL SECURITY;

--
-- Name: channel_raw_events; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.channel_raw_events ENABLE ROW LEVEL SECURITY;

--
-- Name: channel_sku_mapping; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.channel_sku_mapping ENABLE ROW LEVEL SECURITY;

--
-- Name: backorder_line company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.backorder_line USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: categories company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.categories USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: channel_inventory_state company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.channel_inventory_state USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: channel_raw_events company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.channel_raw_events USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: channel_sku_mapping company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.channel_sku_mapping USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: customer_fact_summary company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.customer_fact_summary USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: customer_product_summary company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.customer_product_summary USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: customers company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.customers USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: domain_outbox company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.domain_outbox USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: emergency_stock_correction company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.emergency_stock_correction USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: historical_test_data_archive_audit company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.historical_test_data_archive_audit USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: historical_test_data_registry company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.historical_test_data_registry USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: idempotency_request company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.idempotency_request USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: inbound_order_items company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.inbound_order_items USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: inbound_orders company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.inbound_orders USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: integration_configs company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.integration_configs USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: inventory company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.inventory USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: inventory_batch company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.inventory_batch USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: inventory_reservations company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.inventory_reservations USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: locations company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.locations USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: outbound_tasks company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.outbound_tasks USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: pending_sku_mapping company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.pending_sku_mapping USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: product_skus company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.product_skus USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: products company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.products USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: purchase_order company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.purchase_order USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: purchase_order_item company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.purchase_order_item USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sales_daily_summary company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sales_daily_summary USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sales_order_items company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sales_order_items USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sales_order_shipments company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sales_order_shipments USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sales_orders company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sales_orders USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: stock_transactions company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.stock_transactions USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: stocktake_items company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.stocktake_items USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: stocktake_tasks company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.stocktake_tasks USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: suppliers company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.suppliers USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_approval_template company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_approval_template USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_excel_templates company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_excel_templates USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_permission company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_permission USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_permission_request company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_permission_request USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_permission_request_audit company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_permission_request_audit USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_permission_request_warehouse company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_permission_request_warehouse USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_role company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_role USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_role_copy_audit company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_role_copy_audit USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_role_governance_audit company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_role_governance_audit USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_role_inherit company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_role_inherit USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_role_permission company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_role_permission USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_user_role company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_user_role USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: sys_user_warehouse company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.sys_user_warehouse USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: system_config company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.system_config USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: tenant_session_security_audit company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.tenant_session_security_audit USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: users company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.users USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: warehouses company_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY company_isolation ON public.warehouses USING ((company_id = public.bcwms_current_company_id())) WITH CHECK ((company_id = public.bcwms_current_company_id()));


--
-- Name: customer_fact_summary; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.customer_fact_summary ENABLE ROW LEVEL SECURITY;

--
-- Name: customer_product_summary; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.customer_product_summary ENABLE ROW LEVEL SECURITY;

--
-- Name: customers; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;

--
-- Name: domain_outbox; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.domain_outbox ENABLE ROW LEVEL SECURITY;

--
-- Name: emergency_stock_correction; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.emergency_stock_correction ENABLE ROW LEVEL SECURITY;

--
-- Name: historical_test_data_archive_audit; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.historical_test_data_archive_audit ENABLE ROW LEVEL SECURITY;

--
-- Name: historical_test_data_registry; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.historical_test_data_registry ENABLE ROW LEVEL SECURITY;

--
-- Name: idempotency_request; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.idempotency_request ENABLE ROW LEVEL SECURITY;

--
-- Name: inbound_order_items; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inbound_order_items ENABLE ROW LEVEL SECURITY;

--
-- Name: inbound_orders; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inbound_orders ENABLE ROW LEVEL SECURITY;

--
-- Name: integration_configs; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.integration_configs ENABLE ROW LEVEL SECURITY;

--
-- Name: inventory; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inventory ENABLE ROW LEVEL SECURITY;

--
-- Name: inventory_batch; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inventory_batch ENABLE ROW LEVEL SECURITY;

--
-- Name: inventory_reservations; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.inventory_reservations ENABLE ROW LEVEL SECURITY;

--
-- Name: locations; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;

--
-- Name: outbound_tasks; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.outbound_tasks ENABLE ROW LEVEL SECURITY;

--
-- Name: pending_sku_mapping; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.pending_sku_mapping ENABLE ROW LEVEL SECURITY;

--
-- Name: product_skus; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.product_skus ENABLE ROW LEVEL SECURITY;

--
-- Name: products; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

--
-- Name: purchase_order; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.purchase_order ENABLE ROW LEVEL SECURITY;

--
-- Name: purchase_order_item; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.purchase_order_item ENABLE ROW LEVEL SECURITY;

--
-- Name: sales_daily_summary; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sales_daily_summary ENABLE ROW LEVEL SECURITY;

--
-- Name: sales_order_items; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sales_order_items ENABLE ROW LEVEL SECURITY;

--
-- Name: sales_order_shipments; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sales_order_shipments ENABLE ROW LEVEL SECURITY;

--
-- Name: sales_orders; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sales_orders ENABLE ROW LEVEL SECURITY;

--
-- Name: stock_transactions; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.stock_transactions ENABLE ROW LEVEL SECURITY;

--
-- Name: stocktake_items; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.stocktake_items ENABLE ROW LEVEL SECURITY;

--
-- Name: stocktake_tasks; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.stocktake_tasks ENABLE ROW LEVEL SECURITY;

--
-- Name: suppliers; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.suppliers ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_approval_template; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_approval_template ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_excel_templates; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_excel_templates ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_permission; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_permission ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_permission_request; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_permission_request ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_permission_request_audit; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_permission_request_audit ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_permission_request_warehouse; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_permission_request_warehouse ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_role; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_role ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_role_copy_audit; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_role_copy_audit ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_role_governance_audit; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_role_governance_audit ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_role_inherit; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_role_inherit ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_role_permission; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_role_permission ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_user_role; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_user_role ENABLE ROW LEVEL SECURITY;

--
-- Name: sys_user_warehouse; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sys_user_warehouse ENABLE ROW LEVEL SECURITY;

--
-- Name: system_config; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.system_config ENABLE ROW LEVEL SECURITY;

--
-- Name: tenant_session_security_audit; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.tenant_session_security_audit ENABLE ROW LEVEL SECURITY;

--
-- Name: users; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

--
-- Name: warehouses; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.warehouses ENABLE ROW LEVEL SECURITY;

--

-- Required global platform role catalogue. No administrator account is created.
INSERT INTO public.platform_roles (role_code, display_name)
VALUES
    ('PLATFORM_SUPER_ADMIN', '平台超级管理员'),
    ('PLATFORM_OPERATIONS_ADMIN', '平台运营管理员'),
    ('PLATFORM_SECURITY_AUDITOR', '安全审计员'),
    ('PLATFORM_TENANT_READ', '公司数据读取'),
    ('PLATFORM_TENANT_EXPORT', '公司数据导出'),
    ('PLATFORM_TENANT_WRITE', '公司数据修改'),
    ('PLATFORM_TENANT_DELETE', '公司数据删除')
ON CONFLICT (role_code) DO NOTHING;

-- Required signup plan catalogue. No tenant or subscription is created.
INSERT INTO public.subscription_plans (
    plan_code, display_name, active, trial_days, fallback_plan_code
)
VALUES
    ('FREE', '免费版', TRUE, NULL, NULL),
    ('TRIAL', '30 天全功能试用版', TRUE, 30, NULL)
ON CONFLICT (plan_code) DO NOTHING;

INSERT INTO public.plan_limits (
    plan_id, limit_key, limit_kind, enabled_value, numeric_value
)
SELECT id, 'ALL_SERVICES', 'ENTITLEMENT', TRUE, NULL
FROM public.subscription_plans
WHERE plan_code = 'TRIAL'
ON CONFLICT (plan_id, limit_key) DO NOTHING;
