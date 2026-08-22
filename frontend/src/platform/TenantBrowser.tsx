import { Alert, Card, Empty, Typography } from 'antd';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { PlatformEffectiveAccess, PlatformTenant } from './api';
import { TenantDetail } from './TenantDetail';
import { TenantDirectory } from './TenantDirectory';

export { effectiveDatasetAccess, effectiveDatasets } from './TenantDatasetBrowser';

export function TenantBrowser({ access }: { access: PlatformEffectiveAccess }): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const [selectedTenant, setSelectedTenant] = useState<PlatformTenant | null>(null);
  const selectTenant = useCallback((tenant: PlatformTenant | null) => setSelectedTenant(tenant), []);

  return <section className="platform-tenant-browser" aria-labelledby="tenant-browser-title">
    <div className="platform-section-heading">
      <div>
        <Typography.Title id="tenant-browser-title" level={3}>{english ? 'Tenant data browser' : '租户数据浏览'}</Typography.Title>
        <Typography.Text type="secondary">{english ? 'Access is limited to the effective delegated scope. Reads and exports are recorded in the internal platform audit log.' : '仅显示当前有效委派范围；读取和导出都会写入平台内部审计日志。'}</Typography.Text>
      </div>
    </div>
    <Alert className="platform-readonly-alert" message={english ? 'Direct write operations are not available. Exports require a selected tenant and dataset.' : '当前版本不提供直接写入操作；导出必须先选择租户和数据集。'} showIcon type="info" />
    <div className="platform-browser-grid">
      <TenantDirectory onSelectTenant={selectTenant} selectedTenantId={selectedTenant?.id} />
      <div className="platform-data-region">
        {selectedTenant
          ? <TenantDetail access={access} directoryTenant={selectedTenant} key={selectedTenant.id} />
          : <Card><Empty description={english ? 'Select a tenant' : '请选择租户'} /></Card>}
      </div>
    </div>
  </section>;
}
