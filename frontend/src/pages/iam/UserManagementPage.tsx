import {
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Popconfirm, Space, Tag, Tooltip } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  createUser,
  deleteUser,
  listRoles,
  listUsers,
  replaceUserRoles,
  resetUserPassword,
  ROLES_QUERY_KEY,
  updateUser,
  USERS_QUERY_KEY,
  type ResetPasswordResult,
  type Role,
  type UserAccount,
} from '../../api/iam';
import { paginateArray } from '../../api/pagination';
import { useAuth } from '../../auth/AuthProvider';
import { formatDateTime } from '../workflowUtils';
import { getIamCapabilities } from './capabilities';
import { isCurrentUser, orderRoleIds } from './iamUtils';
import { TemporaryPasswordModal } from './TemporaryPasswordModal';
import { UserFormModal, type UserFormValues } from './UserFormModal';
import { UserRolesModal, type UserRoleValues } from './UserRolesModal';

interface UserTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  enabled?: 'ALL' | 'ENABLED' | 'DISABLED';
  roleCode?: string;
}

function translatedRoleName(role: Role | undefined, code: string, t: ReturnType<typeof useTranslation>['t']): string {
  return t(`roles.${code}`, { defaultValue: role?.roleName ?? code });
}

export function UserManagementPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [formOpen, setFormOpen] = useState(false);
  const [rolesOpen, setRolesOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserAccount>();
  const [resetResult, setResetResult] = useState<ResetPasswordResult>();
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const capabilities = getIamCapabilities(session?.currentRole ?? '');

  const rolesQuery = useQuery({ queryKey: ROLES_QUERY_KEY, queryFn: () => listRoles(false) });
  const roles = rolesQuery.data ?? [];
  const roleByCode = useMemo(() => new Map(roles.map((role) => [role.roleCode ?? '', role])), [roles]);

  const saveMutation = useMutation({
    mutationFn: ({ user, values }: { user?: UserAccount; values: UserFormValues }) => {
      if (user?.id !== undefined) {
        return updateUser(user.id, {
          displayName: values.displayName,
          enabled: values.enabled,
          defaultRoleId: values.defaultRoleId,
          remark: values.remark,
        });
      }
      return createUser({
        username: values.username ?? '',
        password: values.password ?? '',
        displayName: values.displayName,
        roleIds: orderRoleIds(values.roleIds ?? [], values.defaultRoleId),
        enabled: values.enabled ?? true,
        remark: values.remark,
      });
    },
  });
  const rolesMutation = useMutation({
    mutationFn: async ({ user, values }: { user: UserAccount; values: UserRoleValues }) => {
      if (user.id === undefined) throw new Error('Missing user ID');
      const orderedIds = orderRoleIds(values.roleIds, values.defaultRoleId);
      return replaceUserRoles(user.id, orderedIds, values.defaultRoleId);
    },
  });
  const resetMutation = useMutation({ mutationFn: resetUserPassword });
  const deleteMutation = useMutation({ mutationFn: deleteUser });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const saveUser = async (values: UserFormValues): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ user: editingUser, values });
      message.success(t(editingUser ? 'iam.users.messages.updated' : 'iam.users.messages.created'));
      setFormOpen(false);
      setEditingUser(undefined);
      await refresh();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const saveRoles = async (values: UserRoleValues): Promise<boolean> => {
    if (!editingUser) return false;
    try {
      await rolesMutation.mutateAsync({ user: editingUser, values });
      message.success(t('iam.users.messages.rolesUpdated'));
      setRolesOpen(false);
      setEditingUser(undefined);
      await refresh();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const resetPassword = async (user: UserAccount): Promise<void> => {
    if (user.id === undefined) return;
    try {
      const result = await resetMutation.mutateAsync(user.id);
      setResetResult(result);
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const removeUser = async (user: UserAccount): Promise<void> => {
    if (user.id === undefined) return;
    try {
      await deleteMutation.mutateAsync(user.id);
      message.success(t('iam.users.messages.deleted'));
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const copyPassword = async (password: string): Promise<void> => {
    try {
      await navigator.clipboard.writeText(password);
      message.success(t('common.copied'));
    } catch {
      message.error(t('iam.users.reset.copyFailed'));
    }
  };

  const columns = useMemo<ProColumns<UserAccount>[]>(() => [
    {
      title: t('common.search'), dataIndex: 'search', hideInTable: true,
      fieldProps: { placeholder: t('iam.users.searchPlaceholder') },
    },
    {
      title: t('iam.users.fields.enabled'), dataIndex: 'enabled', hideInTable: true,
      initialValue: 'ALL', valueType: 'select',
      valueEnum: {
        ALL: { text: t('iam.filters.all') }, ENABLED: { text: t('common.enabled') }, DISABLED: { text: t('common.disabled') },
      },
    },
    {
      title: t('iam.users.fields.roles'), dataIndex: 'roleCode', hideInTable: true, valueType: 'select',
      valueEnum: Object.fromEntries(roles.filter((role) => role.roleCode).map((role) => [
        role.roleCode as string,
        { text: translatedRoleName(role, role.roleCode as string, t) },
      ])),
    },
    { title: t('iam.users.fields.username'), dataIndex: 'username', width: 180, fixed: 'left', search: false, copyable: true },
    { title: t('iam.users.fields.displayName'), dataIndex: 'displayName', width: 180, search: false, renderText: (value) => value || '-' },
    {
      title: t('iam.users.fields.roles'), dataIndex: 'roleCodes', width: 300, search: false,
      render: (_, user) => (
        <Space size={[4, 4]} wrap>
          {(user.roleCodes ?? []).map((code) => (
            <Tag color={code === user.defaultRoleCode ? 'cyan' : 'default'} key={code}>
              {translatedRoleName(roleByCode.get(code), code, t)}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('iam.users.fields.defaultRole'), dataIndex: 'defaultRoleCode', width: 180, search: false,
      renderText: (code) => code ? translatedRoleName(roleByCode.get(String(code)), String(code), t) : '-',
    },
    {
      title: t('iam.users.fields.enabled'), dataIndex: 'enabled', width: 100, search: false,
      render: (_, user) => <Tag color={user.enabled ? 'success' : 'default'}>{t(user.enabled ? 'common.enabled' : 'common.disabled')}</Tag>,
    },
    { title: t('common.updatedAt'), dataIndex: 'updatedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: 190, fixed: 'right',
      render: (_, user) => {
        const self = isCurrentUser(user, session?.username);
        return (
          <Space size={2}>
            <Tooltip title={t('common.edit')}>
              <Button aria-label={t('common.edit')} icon={<EditOutlined />} onClick={() => { setEditingUser(user); setFormOpen(true); }} size="small" type="text" />
            </Tooltip>
            <Tooltip title={self ? t('iam.users.actions.selfProtected') : t('iam.users.actions.roles')}>
              <Button
                aria-label={t('iam.users.actions.roles')}
                disabled={self}
                icon={<SafetyCertificateOutlined />}
                onClick={() => { setEditingUser(user); setRolesOpen(true); }}
                size="small"
                type="text"
              />
            </Tooltip>
            <Popconfirm
              description={t('iam.users.reset.confirmDescription')}
              disabled={self}
              onConfirm={() => void resetPassword(user)}
              title={t('iam.users.reset.confirmTitle')}
            >
              <Tooltip title={self ? t('iam.users.actions.selfProtected') : t('iam.users.actions.resetPassword')}>
                <Button aria-label={t('iam.users.actions.resetPassword')} disabled={self} icon={<KeyOutlined />} loading={resetMutation.isPending} size="small" type="text" />
              </Tooltip>
            </Popconfirm>
            <Popconfirm
              description={t('iam.users.delete.confirmDescription')}
              disabled={self}
              onConfirm={() => void removeUser(user)}
              title={t('iam.users.delete.confirmTitle', { username: user.username ?? '' })}
            >
              <Tooltip title={self ? t('iam.users.actions.selfProtected') : t('common.delete')}>
                <Button aria-label={t('common.delete')} danger disabled={self} icon={<DeleteOutlined />} loading={deleteMutation.isPending} size="small" type="text" />
              </Tooltip>
            </Popconfirm>
          </Space>
        );
      },
    },
  ], [deleteMutation.isPending, i18n.language, resetMutation.isPending, roleByCode, roles, session?.username, t]);

  return (
    <section className="data-page">
      <Alert
        className="page-context-alert"
        description={t('iam.users.securityNotice')}
        message={t('iam.users.securityTitle')}
        showIcon
        type="info"
      />
      <ProTable<UserAccount, UserTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('iam.users.title')}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const users = await listUsers();
            const keyword = params.search?.trim().toLowerCase();
            const filtered = users.filter((user) => {
              if (params.enabled === 'ENABLED' && user.enabled !== true) return false;
              if (params.enabled === 'DISABLED' && user.enabled === true) return false;
              if (params.roleCode && !user.roleCodes?.includes(params.roleCode)) return false;
              return !keyword || [user.username, user.displayName, user.remark, ...(user.roleCodes ?? [])]
                .some((value) => value?.toLowerCase().includes(keyword));
            });
            return paginateArray(filtered, params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey={(row) => String(row.id ?? row.username)}
        scroll={{ x: 1510 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => capabilities.canManage ? [
          <Button icon={<PlusOutlined />} key="create" onClick={() => { setEditingUser(undefined); setFormOpen(true); }} type="primary">
            {t('iam.users.actions.create')}
          </Button>,
        ] : []}
      />

      <UserFormModal
        isSelf={isCurrentUser(editingUser ?? {}, session?.username)}
        loading={saveMutation.isPending}
        onClose={() => { setFormOpen(false); setEditingUser(undefined); }}
        onSubmit={saveUser}
        open={formOpen}
        roles={roles}
        user={editingUser}
      />
      <UserRolesModal
        loading={rolesMutation.isPending}
        onClose={() => { setRolesOpen(false); setEditingUser(undefined); }}
        onSubmit={saveRoles}
        open={rolesOpen}
        roles={roles}
        user={editingUser}
      />
      <TemporaryPasswordModal
        onClose={() => setResetResult(undefined)}
        onCopy={copyPassword}
        open={resetResult !== undefined}
        result={resetResult}
      />
    </section>
  );
}
