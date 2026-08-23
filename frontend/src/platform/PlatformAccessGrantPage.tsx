import { Alert, Button, Card, DatePicker, Empty, Form, Select, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  createPlatformAccessGrants,
  listPlatformAccessGrants,
  listPlatformManagedUsers,
  listPlatformTenants,
  revokePlatformAccessGrant,
  type PlatformAccessGrant,
  type PlatformManagedUser,
  type PlatformTenant,
} from './api';
import { platformAccessGrantStatus } from './platformAccessGrantStatus';
import { usePlatformAuth } from './PlatformAuthProvider';

const DATASETS = ['users', 'roles', 'warehouses', 'products', 'inventory', 'sales_orders'];

export function PlatformAccessGrantPage(): JSX.Element {
  const { i18n } = useTranslation();
  const { session } = usePlatformAuth();
  const english = i18n.language === 'en-US';
  const superAdmin = Boolean(session?.roles.includes('PLATFORM_SUPER_ADMIN'));
  const [form] = Form.useForm();
  const [users, setUsers] = useState<PlatformManagedUser[]>([]);
  const [tenants, setTenants] = useState<PlatformTenant[]>([]);
  const [grants, setGrants] = useState<PlatformAccessGrant[]>([]);
  const [selected, setSelected] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const load = async (id?: number) => {
    setLoading(true);
    setError(false);
    try {
      const managedUsers = await listPlatformManagedUsers();
      setUsers(managedUsers);
      if (superAdmin) {
        const tenantPage = await listPlatformTenants(0, 100);
        setTenants(tenantPage.content);
      }
      const target = id ?? selected;
      if (target) {
        setSelected(target);
        setGrants(await listPlatformAccessGrants(target));
      }
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, [superAdmin]);

  const submit = async (values: {
    capabilities: string[];
    tenantIds: number[];
    datasets: string[];
    range?: [{ toISOString(): string }, { toISOString(): string }];
  }) => {
    if (!selected || !superAdmin) return;
    setLoading(true);
    setError(false);
    try {
      await createPlatformAccessGrants({
        platformUserId: selected,
        capabilities: values.capabilities as ('READ' | 'EXPORT')[],
        tenantIds: values.tenantIds,
        datasets: values.datasets,
        effectiveFrom: values.range?.[0]?.toISOString(),
        expiresAt: values.range?.[1]?.toISOString(),
      });
      form.resetFields();
      setGrants(await listPlatformAccessGrants(selected));
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  return <main className="platform-home-content platform-audit-page">
    <div className="platform-section-heading"><div>
      <Typography.Title level={2}>{english ? 'Delegated access' : '委派授权'}</Typography.Title>
      <Typography.Text type="secondary">{superAdmin
        ? (english
          ? 'Super administrators can grant or revoke exact tenant, dataset, action and time scopes.'
          : '超级管理员可按租户、数据集、动作和有效期授予或撤销委派。')
        : (english
          ? 'Security auditors can inspect the grant inventory but cannot grant or revoke access.'
          : '安全审计员可查看委派清单，但不能授予或撤销权限。')}</Typography.Text>
    </div></div>
    <Alert
      message={english
        ? 'No tenant or dataset wildcard exists. Read and export are separate capabilities.'
        : '不提供全部租户或全部数据集通配；读取与导出是两项独立能力。'}
      showIcon
      type="info"
    />
    {error ? <Alert message={english ? 'Operation failed.' : '操作失败，请重试。'} showIcon type="error" /> : null}
    <Card loading={loading} title={superAdmin ? (english ? 'Grant access' : '授予访问权限') : (english ? 'Select administrator' : '选择管理员')}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Select
          onChange={(id) => void load(id)}
          options={users
            .filter((user) => !user.superAdmin && (superAdmin ? user.enabled : true))
            .map((user) => ({ value: user.id, label: `${user.displayName} · ${user.email}` }))}
          placeholder={english ? 'Choose a platform administrator' : '选择平台管理员'}
          value={selected}
        />
        {selected && superAdmin ? <Form form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Form.Item label={english ? 'Capability' : '能力'} name="capabilities" rules={[{ required: true }]}>
            <Select mode="multiple" options={[
              { value: 'READ', label: english ? 'Read' : '读取' },
              { value: 'EXPORT', label: english ? 'Export' : '导出' },
            ]} />
          </Form.Item>
          <Form.Item label={english ? 'Tenants' : '适用租户'} name="tenantIds" rules={[{ required: true }]}>
            <Select mode="multiple" options={tenants.map((tenant) => ({
              value: tenant.id,
              label: `${tenant.displayName} · ${tenant.tenantCode}`,
            }))} />
          </Form.Item>
          <Form.Item label={english ? 'Datasets' : '适用数据集'} name="datasets" rules={[{ required: true }]}>
            <Select mode="multiple" options={DATASETS.map((dataset) => ({ value: dataset, label: dataset }))} />
          </Form.Item>
          <Form.Item label={english ? 'Effective period (default 30 days, max 90 days)' : '有效期（默认 30 天，最长 90 天）'} name="range">
            <DatePicker.RangePicker showTime />
          </Form.Item>
          <Button htmlType="submit" loading={loading} type="primary">{english ? 'Grant' : '授予'}</Button>
        </Form> : selected ? <Alert message={english ? 'Read-only grant inventory.' : '当前为只读委派清单。'} showIcon type="info" />
          : <Empty description={english ? 'Select an administrator first' : '请先选择平台管理员'} />}
      </Space>
    </Card>
    <Card title={english ? 'Existing grants' : '现有授权'}>
      {selected ? <Table
        columns={[
          { title: english ? 'Capability' : '能力', dataIndex: 'capability', render: (value) => <Tag color="blue">{value}</Tag> },
          { title: english ? 'Tenant' : '租户', dataIndex: 'tenantName' },
          { title: english ? 'Dataset' : '数据集', dataIndex: 'datasetCode' },
          { title: english ? 'Expires' : '到期时间', dataIndex: 'expiresAt' },
          { title: english ? 'Status' : '状态', render: (_, grant: PlatformAccessGrant) => {
            const status = platformAccessGrantStatus(grant);
            return <Tag color={status === 'ACTIVE' ? 'green' : status === 'SCHEDULED' ? 'blue' : 'default'}>{status}</Tag>;
          } },
          { title: english ? 'Action' : '操作', render: (_, grant: PlatformAccessGrant) => {
            if (!superAdmin) return <Typography.Text type="secondary">{english ? 'Read only' : '只读'}</Typography.Text>;
            const status = platformAccessGrantStatus(grant);
            return <Button
              danger
              disabled={status === 'REVOKED' || status === 'EXPIRED'}
              onClick={() => void revokePlatformAccessGrant(grant.id).then(() => load(selected))}
            >{english ? 'Revoke' : '撤销'}</Button>;
          } },
        ]}
        dataSource={grants}
        pagination={false}
        rowKey="id"
      /> : <Empty />}
    </Card>
  </main>;
}
