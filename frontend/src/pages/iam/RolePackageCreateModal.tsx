import { SafetyCertificateOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, Modal, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Permission, RolePackageCreateRequest } from '../../api/iam';

interface Props {
  loading: boolean;
  onClose: () => void;
  onSubmit: (payload: RolePackageCreateRequest) => Promise<boolean>;
  open: boolean;
  permissionCatalog: Permission[];
  submitting: boolean;
}

interface FormValues {
  roleCode: string;
  roleName: string;
  description: string;
}

export function RolePackageCreateModal({
  loading,
  onClose,
  onSubmit,
  open,
  permissionCatalog,
  submitting,
}: Props): JSX.Element {
  const { t } = useTranslation();
  const [form] = Form.useForm<FormValues>();
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<number[]>([]);
  const [search, setSearch] = useState('');

  const assignablePermissions = useMemo(() => permissionCatalog.filter((permission) => (
    permission.id !== undefined
    && permission.status === 'ACTIVE'
    && permission.customAssignable === true
    && permission.riskLevel !== 'CRITICAL'
  )), [permissionCatalog]);
  const visiblePermissions = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return assignablePermissions;
    return assignablePermissions.filter((permission) => (
      [permission.permissionCode, permission.permissionName, permission.permissionType]
        .some((value) => value?.toLowerCase().includes(keyword))
    ));
  }, [assignablePermissions, search]);
  const highRiskCount = useMemo(() => assignablePermissions.filter((permission) => (
    permission.id !== undefined
    && selectedPermissionIds.includes(permission.id)
    && permission.riskLevel === 'HIGH'
  )).length, [assignablePermissions, selectedPermissionIds]);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setSelectedPermissionIds([]);
    setSearch('');
  }, [form, open]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields().catch(() => undefined);
    if (!values) return;
    await onSubmit({ ...values, permissionIds: selectedPermissionIds });
  };

  const permissionColumns = [
    {
      title: t('iam.permissions.fields.permission'), dataIndex: 'permissionName',
      render: (value: string | undefined, permission: Permission) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{value || permission.permissionCode || '-'}</Typography.Text>
          <Typography.Text code copyable>{permission.permissionCode}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('iam.permissions.fields.type'), dataIndex: 'permissionType', width: 100,
      render: (value: string | undefined) => <Tag>{value ?? '-'}</Tag>,
    },
    {
      title: t('iam.permissions.fields.risk'), dataIndex: 'riskLevel', width: 110,
      render: (value: string | undefined) => (
        <Tag color={value === 'HIGH' ? 'red' : 'default'}>{t(`iam.permissions.risks.${value ?? 'NORMAL'}`)}</Tag>
      ),
    },
  ];

  return (
    <Modal
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={onClose}>{t('common.cancel')}</Button>,
        <Button key="create" loading={submitting} onClick={() => void submit()} type="primary">
          {t('iam.roles.blankCreate.create')}
        </Button>,
      ]}
      maskClosable={false}
      onCancel={onClose}
      open={open}
      title={<Space><SafetyCertificateOutlined />{t('iam.roles.blankCreate.title')}</Space>}
      width={920}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          description={t('iam.roles.blankCreate.notice')}
          message={t('iam.roles.blankCreate.securityTitle')}
          showIcon
          type="info"
        />
        <Form form={form} layout="vertical">
          <Form.Item
            label={t('iam.roles.fields.code')}
            name="roleCode"
            getValueFromEvent={(event) => String(event?.target?.value ?? '').toUpperCase()}
            rules={[
              { required: true, message: t('iam.roles.copy.validation.codeRequired') },
              { pattern: /^[A-Za-z][A-Za-z0-9_]{2,49}$/, message: t('iam.roles.copy.validation.codePattern') },
            ]}
          >
            <Input autoComplete="off" maxLength={50} placeholder="CUSTOM_SALES" />
          </Form.Item>
          <Form.Item
            label={t('iam.roles.fields.name')}
            name="roleName"
            rules={[{ required: true, message: t('iam.roles.copy.validation.nameRequired') }, { max: 100 }]}
          >
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item
            label={t('iam.roles.copy.purpose')}
            name="description"
            rules={[{ required: true, message: t('iam.roles.copy.validation.purposeRequired') }, { max: 500 }]}
          >
            <Input.TextArea maxLength={500} rows={3} showCount />
          </Form.Item>
        </Form>
        <Alert
          description={t('iam.roles.blankCreate.protectedNotice')}
          message={t('iam.roles.governance.selectionSummary', { total: selectedPermissionIds.length, high: highRiskCount })}
          showIcon
          type={highRiskCount ? 'warning' : 'success'}
        />
        <Input.Search
          allowClear
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('iam.roles.blankCreate.searchPlaceholder')}
          value={search}
        />
        <Table<Permission>
          columns={permissionColumns}
          dataSource={visiblePermissions}
          loading={loading}
          pagination={false}
          rowKey={(permission) => permission.id as number}
          rowSelection={{
            preserveSelectedRowKeys: true,
            selectedRowKeys: selectedPermissionIds,
            onChange: (keys) => setSelectedPermissionIds(keys.map(Number)),
          }}
          scroll={{ y: 360 }}
          size="small"
        />
      </Space>
    </Modal>
  );
}
