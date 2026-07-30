import { EyeOutlined, LockOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Drawer, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  listPermissions,
  listRolePermissions,
  listRoles,
  listUsers,
  PERMISSIONS_QUERY_KEY,
  ROLES_QUERY_KEY,
  USERS_QUERY_KEY,
  type Permission,
  type Role,
} from '../../api/iam';
import { paginateArray } from '../../api/pagination';
import { roleLabel } from './iamUtils';

interface RoleTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  status?: 'ALL' | 'ACTIVE' | 'DISABLED';
  roleType?: 'ALL' | 'SYSTEM' | 'CUSTOM';
  roleDataVersion?: number;
  userDataVersion?: number;
}

interface RoleRow extends Role {
  userCount: number;
}

export function RolePermissionPage(): JSX.Element {
  const [selectedRole, setSelectedRole] = useState<RoleRow>();
  const { t } = useTranslation();
  const rolesQuery = useQuery({ queryKey: ROLES_QUERY_KEY, queryFn: () => listRoles(false) });
  const usersQuery = useQuery({ queryKey: USERS_QUERY_KEY, queryFn: listUsers });
  const permissionsQuery = useQuery({ queryKey: PERMISSIONS_QUERY_KEY, queryFn: () => listPermissions() });
  const rolePermissionsQuery = useQuery({
    queryKey: ['iam', 'roles', selectedRole?.id, 'permissions'],
    queryFn: () => listRolePermissions(selectedRole?.id as number),
    enabled: selectedRole?.id !== undefined,
  });

  const roles = useMemo<RoleRow[]>(() => (rolesQuery.data ?? []).map((role) => ({
    ...role,
    userCount: (usersQuery.data ?? []).filter((user) => role.id !== undefined && user.roleIds?.includes(role.id)).length,
  })), [rolesQuery.data, usersQuery.data]);
  const roleById = useMemo(() => new Map(roles.map((role) => [role.id, role])), [roles]);

  const columns = useMemo<ProColumns<RoleRow>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('iam.roles.searchPlaceholder') } },
    {
      title: t('iam.roles.fields.status'), dataIndex: 'status', hideInTable: true, initialValue: 'ALL', valueType: 'select',
      valueEnum: { ALL: { text: t('iam.filters.all') }, ACTIVE: { text: t('common.enabled') }, DISABLED: { text: t('common.disabled') } },
    },
    {
      title: t('iam.roles.fields.type'), dataIndex: 'roleType', hideInTable: true, initialValue: 'ALL', valueType: 'select',
      valueEnum: { ALL: { text: t('iam.filters.all') }, SYSTEM: { text: t('iam.roles.types.SYSTEM') }, CUSTOM: { text: t('iam.roles.types.CUSTOM') } },
    },
    { title: t('iam.roles.fields.name'), dataIndex: 'roleName', width: 210, fixed: 'left', search: false, renderText: (value, row) => value || row.roleCode || '-' },
    { title: t('iam.roles.fields.code'), dataIndex: 'roleCode', width: 200, search: false, copyable: true },
    {
      title: t('iam.roles.fields.type'), dataIndex: 'roleType', width: 110, search: false,
      render: (_, row) => <Tag color={row.roleType === 'SYSTEM' ? 'blue' : 'purple'}>{t(`iam.roles.types.${row.roleType ?? 'CUSTOM'}`)}</Tag>,
    },
    {
      title: t('iam.roles.fields.status'), dataIndex: 'status', width: 100, search: false,
      render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'success' : 'default'}>{t(row.status === 'ACTIVE' ? 'common.enabled' : 'common.disabled')}</Tag>,
    },
    { title: t('iam.roles.fields.users'), dataIndex: 'userCount', width: 100, align: 'right', search: false },
    { title: t('iam.roles.fields.directPermissions'), dataIndex: 'permissionIds', width: 120, align: 'right', search: false, renderText: (value) => Array.isArray(value) ? value.length : 0 },
    { title: t('iam.roles.fields.inheritedRoles'), dataIndex: 'parentRoleIds', width: 120, align: 'right', search: false, renderText: (value) => Array.isArray(value) ? value.length : 0 },
    { title: t('iam.roles.fields.description'), dataIndex: 'description', width: 280, search: false, ellipsis: true, renderText: (value) => value || '-' },
    {
      title: t('common.actions'), valueType: 'option', width: 80, fixed: 'right',
      render: (_, row) => [
        <Tooltip key="view" title={t('iam.roles.actions.review')}>
          <Button aria-label={t('iam.roles.actions.review')} icon={<EyeOutlined />} onClick={() => setSelectedRole(row)} size="small" type="text" />
        </Tooltip>,
      ],
    },
  ], [t]);

  const permissionColumns = [
    {
      title: t('iam.permissions.fields.type'), dataIndex: 'permissionType', width: 90,
      render: (value: string | undefined) => <Tag>{value ?? '-'}</Tag>,
    },
    {
      title: t('iam.permissions.fields.permission'), dataIndex: 'permissionName', width: 250,
      render: (value: string | undefined, row: Permission) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{value || row.permissionCode || '-'}</Typography.Text>
          <Typography.Text copyable={Boolean(row.permissionCode)} type="secondary">{row.permissionCode}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('iam.permissions.fields.resource'), dataIndex: 'resourcePath',
      render: (value: string | undefined, row: Permission) => value
        ? <Typography.Text code>{[row.httpMethod, value].filter(Boolean).join(' ')}</Typography.Text>
        : row.menuUrl || '-',
    },
    {
      title: t('iam.permissions.fields.status'), dataIndex: 'status', width: 90,
      render: (value: string | undefined) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{t(value === 'ACTIVE' ? 'common.enabled' : 'common.disabled')}</Tag>,
    },
  ];

  return (
    <section className="data-page">
      <Alert
        className="page-context-alert"
        description={t('iam.roles.auditNotice')}
        icon={<LockOutlined />}
        message={t('iam.roles.auditTitle')}
        showIcon
        type="info"
      />
      <ProTable<RoleRow, RoleTableParams>
        columns={columns}
        headerTitle={t('iam.roles.title')}
        loading={rolesQuery.isLoading || usersQuery.isLoading || permissionsQuery.isLoading}
        options={{ density: true, reload: false, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          const keyword = params.search?.trim().toLowerCase();
          const filtered = roles.filter((role) => {
            if (params.status !== 'ALL' && params.status && role.status !== params.status) return false;
            if (params.roleType !== 'ALL' && params.roleType && role.roleType !== params.roleType) return false;
            return !keyword || [role.roleCode, role.roleName, role.description]
              .some((value) => value?.toLowerCase().includes(keyword));
          });
          return paginateArray(filtered, params);
        }}
        params={{ roleDataVersion: roles.length, userDataVersion: usersQuery.data?.length ?? 0 }}
        rowKey={(row) => String(row.id ?? row.roleCode)}
        scroll={{ x: 1450 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [<Tag color="blue" key="catalog">{t('iam.roles.readOnly')}</Tag>]}
      />

      <Drawer
        destroyOnHidden
        onClose={() => setSelectedRole(undefined)}
        open={selectedRole !== undefined}
        title={selectedRole ? roleLabel(selectedRole) : t('iam.roles.detailsTitle')}
        width={820}
      >
        {selectedRole && (
          <>
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label={t('iam.roles.fields.code')}>{selectedRole.roleCode}</Descriptions.Item>
              <Descriptions.Item label={t('iam.roles.fields.status')}>
                <Tag color={selectedRole.status === 'ACTIVE' ? 'success' : 'default'}>{t(selectedRole.status === 'ACTIVE' ? 'common.enabled' : 'common.disabled')}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('iam.roles.fields.type')}>{t(`iam.roles.types.${selectedRole.roleType ?? 'CUSTOM'}`)}</Descriptions.Item>
              <Descriptions.Item label={t('iam.roles.fields.users')}>{selectedRole.userCount}</Descriptions.Item>
              <Descriptions.Item label={t('iam.roles.fields.inheritedRoles')} span={2}>
                {selectedRole.parentRoleIds?.length ? (
                  <Space size={[4, 4]} wrap>
                    {selectedRole.parentRoleIds.map((id) => <Tag key={id}>{roleLabel(roleById.get(id) ?? { id })}</Tag>)}
                  </Space>
                ) : t('iam.roles.noInheritance')}
              </Descriptions.Item>
              <Descriptions.Item label={t('iam.roles.fields.description')} span={2}>{selectedRole.description || '-'}</Descriptions.Item>
            </Descriptions>

            <Typography.Title className="drawer-section-title" level={5}>{t('iam.roles.directPermissionTitle')}</Typography.Title>
            <Typography.Paragraph type="secondary">{t('iam.roles.directPermissionHint')}</Typography.Paragraph>
            {rolePermissionsQuery.data?.length ? (
              <Table<Permission>
                columns={permissionColumns}
                dataSource={rolePermissionsQuery.data}
                loading={rolePermissionsQuery.isLoading}
                pagination={false}
                rowKey={(row) => String(row.id ?? row.permissionCode)}
                scroll={{ x: 720 }}
                size="small"
              />
            ) : <Empty description={t('iam.roles.noDirectPermissions')} image={Empty.PRESENTED_IMAGE_SIMPLE} />}
          </>
        )}
      </Drawer>
    </section>
  );
}
