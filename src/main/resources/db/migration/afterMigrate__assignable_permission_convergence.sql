DO $permission_convergence$
DECLARE company_record RECORD;
BEGIN
    IF to_regclass('public.sys_permission') IS NOT NULL
        AND to_regclass('public.tenants') IS NOT NULL THEN
        FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
            PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);
            UPDATE public.sys_permission
            SET risk_level = 'HIGH', updated_at = CURRENT_TIMESTAMP
            WHERE company_id = company_record.id
              AND risk_level = 'CRITICAL' AND custom_assignable = TRUE
              AND permission_code IN (
                  'inventory_quality:review','inventory_quality:policy',
                  'receiving_inspection:review','receiving_inspection:correct','receiving_inspection:policy',
                  'inventory_disposition:execute','inventory_disposition:review','inventory_disposition:policy'
              );
        END LOOP;
        PERFORM set_config('app.company_id', '', TRUE);
    END IF;
END
$permission_convergence$;

DO $permission_constraint$
BEGIN
    IF to_regclass('public.sys_permission') IS NOT NULL
        AND NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.sys_permission'::regclass
              AND conname = 'ck_sys_permission_critical_not_custom'
        ) THEN
        ALTER TABLE public.sys_permission
            ADD CONSTRAINT ck_sys_permission_critical_not_custom
            CHECK (risk_level <> 'CRITICAL' OR custom_assignable = FALSE);
    END IF;
END
$permission_constraint$;
