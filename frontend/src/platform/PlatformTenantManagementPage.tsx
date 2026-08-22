import { Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import type { PlatformEffectiveAccess } from './api';
import { TenantBrowser } from './TenantBrowser';

export function PlatformTenantManagementPage({ access }: { access: PlatformEffectiveAccess }): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';

  return <main className="platform-home-content platform-tenant-management-page">
    <Typography.Title level={2}>{english ? 'Tenant management' : '租户管理'}</Typography.Title>
    <Typography.Paragraph type="secondary">
      {english
        ? 'The tenant directory loads when this page opens. No tenant is selected and no tenant details or business data are read until you choose one; datasets still require an explicit selection.'
        : '进入页面后自动显示租户目录，但不会默认选择租户，也不会读取租户详情或业务数据；数据集仍需主动选择后才读取。'}
    </Typography.Paragraph>
    <TenantBrowser access={access} />
  </main>;
}
