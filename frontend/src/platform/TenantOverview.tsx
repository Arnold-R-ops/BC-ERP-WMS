import { Alert, Button, Card, Descriptions, Spin, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import type { PlatformTenant } from './api';

interface TenantOverviewProps {
  tenant: PlatformTenant | null;
  loading: boolean;
  error: boolean;
  onRetry: () => void;
}

function displayDate(value: string | null | undefined, english: boolean): string {
  if (!value) return '-';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString(english ? 'en-US' : 'zh-CN');
}

export function TenantOverview({ tenant, loading, error, onRetry }: TenantOverviewProps): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';

  return <Card title={english ? 'Tenant overview' : '租户概览'}>
    {error ? <Alert
      action={<Button onClick={onRetry} size="small">{english ? 'Retry' : '重试'}</Button>}
      message={english ? 'Unable to load the tenant overview.' : '租户概览加载失败。'}
      showIcon
      type="error"
    /> : <Spin spinning={loading}>
      {tenant ? <Descriptions column={{ xs: 1, sm: 2 }} size="small">
        <Descriptions.Item label={english ? 'Tenant name' : '租户名称'}>{tenant.displayName}</Descriptions.Item>
        <Descriptions.Item label={english ? 'Tenant code' : '租户编码'}>{tenant.tenantCode}</Descriptions.Item>
        <Descriptions.Item label={english ? 'Status' : '状态'}><Tag color={tenant.status === 'ACTIVE' ? 'green' : 'default'}>{tenant.status}</Tag></Descriptions.Item>
        <Descriptions.Item label={english ? 'Created at' : '创建时间'}>{displayDate(tenant.createdAt, english)}</Descriptions.Item>
        <Descriptions.Item label={english ? 'Closed at' : '关闭时间'}>{displayDate(tenant.closedAt, english)}</Descriptions.Item>
        <Descriptions.Item label={english ? 'Purge due at' : '计划清除时间'}>{displayDate(tenant.purgeDueAt, english)}</Descriptions.Item>
      </Descriptions> : null}
    </Spin>}
  </Card>;
}
