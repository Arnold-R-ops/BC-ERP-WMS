import { describe, expect, it } from 'vitest';
import { canAccessModule, hasPermission } from './access';

describe('effective-permission module access', () => {
  it('gives the company administrator full company access', () => {
    expect(canAccessModule('TENANT_ADMIN', 'integrations', [])).toBe(true);
    expect(canAccessModule('TENANT_ADMIN', 'iam', [])).toBe(true);
    expect(hasPermission('TENANT_ADMIN', [], 'sales:void')).toBe(true);
  });

  it('derives IAM access from effective permission codes', () => {
    expect(canAccessModule('SECURITY_ADMIN', 'iam', ['menu:system'])).toBe(true);
    expect(canAccessModule('GENERAL_MANAGER', 'iam', ['global:view'])).toBe(false);
    expect(canAccessModule('CUSTOM_ROLE', 'iam', ['sales:view'])).toBe(false);
  });

  it('allows custom packages to expose modules selected by checkbox', () => {
    expect(canAccessModule('CUSTOM_SALES', 'sales', ['menu:sales', 'sales:view'])).toBe(true);
    expect(canAccessModule('CUSTOM_SALES', 'customers', ['menu:customers', 'customer:view'])).toBe(true);
    expect(canAccessModule('CUSTOM_SALES', 'inventory', ['menu:sales', 'sales:view'])).toBe(false);
  });

  it('keeps warehouse operators on the mobile surface only', () => {
    const permissions = [
      'menu:warehouse-mobile',
      'inbound:view',
      'outbound:view',
      'stocktake:view',
    ];
    expect(canAccessModule('WAREHOUSE_STAFF', 'dashboard', permissions)).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'warehouseMobile', permissions)).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'inbound', permissions)).toBe(false);
    expect(canAccessModule('WAREHOUSE_STAFF', 'sales', permissions)).toBe(false);
    expect(canAccessModule('WAREHOUSE_STAFF', 'warehouseSetup', permissions)).toBe(false);
  });

  it('separates integration administration from read-only reconciliation', () => {
    const permissions = ['menu:integration-reconciliation', 'integration:reconcile:view'];
    expect(canAccessModule('GENERAL_MANAGER', 'integrations', permissions)).toBe(true);
    expect(canAccessModule('GENERAL_MANAGER', 'reconciliation', permissions)).toBe(true);
    expect(canAccessModule('GENERAL_MANAGER', 'integrationAdmin', permissions)).toBe(false);
  });

  it('requires a current role even for the dashboard', () => {
    expect(canAccessModule(undefined, 'dashboard', [])).toBe(false);
    expect(canAccessModule('CUSTOM_ROLE', 'dashboard', [])).toBe(true);
    expect(canAccessModule(undefined, 'security', [])).toBe(false);
    expect(canAccessModule('WAREHOUSE_STAFF', 'security', [])).toBe(true);
  });
});
