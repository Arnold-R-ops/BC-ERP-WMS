import { Alert, Button, Card, Empty, Input, List, Pagination, Select, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { listPlatformTenants, type PlatformTenant, type PlatformTenantPage, type PlatformTenantStatus } from './api';

const TENANT_PAGE_SIZE = 20;
const DIRECTORY_STATUSES: PlatformTenantStatus[] = ['PROVISIONING', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'PURGE_PENDING'];

interface TenantDirectoryProps {
  selectedTenantId?: number;
  onSelectTenant: (tenant: PlatformTenant | null) => void;
}

export function TenantDirectory({ selectedTenantId, onSelectTenant }: TenantDirectoryProps): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const [tenantPage, setTenantPage] = useState(0);
  const [draftKeyword, setDraftKeyword] = useState('');
  const [draftStatus, setDraftStatus] = useState<PlatformTenantStatus>();
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [appliedStatus, setAppliedStatus] = useState<PlatformTenantStatus>();
  const [directoryLoadAttempt, setDirectoryLoadAttempt] = useState(0);
  const [tenants, setTenants] = useState<PlatformTenantPage | null>(null);
  const [tenantLoading, setTenantLoading] = useState(false);
  const [tenantError, setTenantError] = useState(false);
  const selectedTenantIdRef = useRef(selectedTenantId);

  useEffect(() => {
    selectedTenantIdRef.current = selectedTenantId;
  }, [selectedTenantId]);

  useEffect(() => {
    let active = true;
    setTenantLoading(true);
    setTenantError(false);
    void listPlatformTenants(tenantPage, TENANT_PAGE_SIZE, {
      keyword: appliedKeyword || undefined,
      status: appliedStatus,
    })
      .then((result) => {
        if (!active) return;
        setTenants(result);
        const currentTenantId = selectedTenantIdRef.current;
        if (currentTenantId !== undefined && !result.content.some((tenant) => tenant.id === currentTenantId)) {
          onSelectTenant(null);
        }
      })
      .catch(() => { if (active) setTenantError(true); })
      .finally(() => { if (active) setTenantLoading(false); });
    return () => { active = false; };
  }, [appliedKeyword, appliedStatus, directoryLoadAttempt, onSelectTenant, tenantPage]);

  const loadTenantDirectory = () => {
    setTenantPage(0);
    setTenantError(false);
    setAppliedKeyword(draftKeyword.trim());
    setAppliedStatus(draftStatus);
    setDirectoryLoadAttempt((current) => current + 1);
  };

  const resetTenantDirectory = () => {
    setDraftKeyword('');
    setDraftStatus(undefined);
    setAppliedKeyword('');
    setAppliedStatus(undefined);
    setTenantPage(0);
    setTenantError(false);
    setDirectoryLoadAttempt((current) => current + 1);
  };

  return <Card className="platform-tenant-list" title={english ? 'Tenants' : '租户'}>
    <div className="platform-tenant-filters">
      <Input allowClear maxLength={160} onChange={(event) => setDraftKeyword(event.target.value)} placeholder={english ? 'Tenant name or code' : '租户名称或编码'} value={draftKeyword} />
      <Select allowClear onChange={setDraftStatus} options={DIRECTORY_STATUSES.map((status) => ({ value: status, label: status }))} placeholder={english ? 'All statuses' : '全部状态'} value={draftStatus} />
      <Space wrap>
        <Button loading={tenantLoading} onClick={loadTenantDirectory} type="primary">{english ? 'Search' : '查询'}</Button>
        <Button disabled={tenantLoading} onClick={resetTenantDirectory}>{english ? 'Reset' : '重置'}</Button>
      </Space>
    </div>
    {tenantError ? <Alert action={<Button onClick={() => setDirectoryLoadAttempt((current) => current + 1)} size="small">{english ? 'Retry' : '重试'}</Button>} message={english ? 'Unable to load tenants.' : '租户目录加载失败。'} showIcon type="error" /> : null}
    <Spin spinning={tenantLoading}>
      <List
        dataSource={tenants?.content ?? []}
        locale={{ emptyText: <Empty description={english ? 'No tenants' : '暂无租户'} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
        renderItem={(tenant) => <List.Item>
          <button className={`platform-tenant-button${selectedTenantId === tenant.id ? ' is-selected' : ''}`} onClick={() => onSelectTenant(tenant)} type="button">
            <strong>{tenant.displayName}</strong><span>{tenant.tenantCode}</span><Tag color={tenant.status === 'ACTIVE' ? 'green' : 'default'}>{tenant.status}</Tag>
          </button>
        </List.Item>}
      />
      {tenants ? <Typography.Text className="platform-directory-summary" type="secondary">
        {english
          ? `${tenants.totalElements} tenants · page ${tenants.totalPages === 0 ? 0 : tenantPage + 1} of ${tenants.totalPages}`
          : `共 ${tenants.totalElements} 个租户 · 第 ${tenants.totalPages === 0 ? 0 : tenantPage + 1} / ${tenants.totalPages} 页`}
      </Typography.Text> : null}
      {(tenants?.totalElements ?? 0) > TENANT_PAGE_SIZE ? <Pagination current={tenantPage + 1} pageSize={TENANT_PAGE_SIZE} showSizeChanger={false} total={tenants?.totalElements ?? 0} onChange={(page) => setTenantPage(page - 1)} size="small" /> : null}
    </Spin>
  </Card>;
}
