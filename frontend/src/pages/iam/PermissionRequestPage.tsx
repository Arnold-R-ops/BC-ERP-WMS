import { CheckOutlined, CloseOutlined, PlusOutlined, SafetyCertificateOutlined, StopOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Space, Tag, Tooltip } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ASSIGNABLE_WAREHOUSES_QUERY_KEY,
  PERMISSIONS_QUERY_KEY,
  PERMISSION_REQUESTS_QUERY_KEY,
  ROLES_QUERY_KEY,
  USERS_QUERY_KEY,
  createPermissionRequest,
  listAssignableWarehouses,
  listPermissionRequests,
  listPermissions,
  listRoles,
  listUsers,
  reviewPermissionRequest,
  revokePermissionRequest,
  type PermissionRequest,
  type PermissionRequestStatus,
} from '../../api/iam';
import { useAuth } from '../../auth/AuthProvider';
import { formatDateTime } from '../workflowUtils';
import { getIamCapabilities } from './capabilities';
import { PermissionRequestDecisionModal } from './PermissionRequestDecisionModal';
import { PermissionRequestModal } from './PermissionRequestModal';
import {
  isProtectedIdentity,
  isRequestableRole,
  type PermissionRequestDecision,
  type PermissionRequestFormValues,
} from './permissionRequestModel';

interface RequestTableParams {
  current?: number;
  pageSize?: number;
  status?: 'ALL' | PermissionRequestStatus;
  targetUserId?: number;
  requestedRoleId?: number;
}

export function PermissionRequestPage(): JSX.Element {
  const { i18n, t } = useTranslation();
  const { session } = useAuth();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const actionRef = useRef<ActionType>();
  const [createOpen, setCreateOpen] = useState(false);
  const [decisionTarget, setDecisionTarget] = useState<PermissionRequest>();
  const [decision, setDecision] = useState<PermissionRequestDecision>();
  const capabilities = getIamCapabilities(session?.currentRole ?? '');
  const rolesQuery = useQuery({ queryKey: ROLES_QUERY_KEY, queryFn: () => listRoles(false) });
  const usersQuery = useQuery({ queryKey: USERS_QUERY_KEY, queryFn: listUsers });
  const permissionsQuery = useQuery({ queryKey: PERMISSIONS_QUERY_KEY, queryFn: () => listPermissions() });
  const warehousesQuery = useQuery({ queryKey: ASSIGNABLE_WAREHOUSES_QUERY_KEY, queryFn: listAssignableWarehouses });

  const roles = rolesQuery.data ?? [];
  const permissions = permissionsQuery.data ?? [];
  const assignableRoles = useMemo(() => roles.filter(isRequestableRole), [roles]);
  const assignableUsers = useMemo(() => (usersQuery.data ?? []).filter((user) => (
    user.id !== undefined
    && user.enabled === true
    && user.username !== session?.username
    && (capabilities.canManageProtectedIdentities || !isProtectedIdentity(user))
  )), [capabilities.canManageProtectedIdentities, session?.username, usersQuery.data]);

  const refreshRequests = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: PERMISSION_REQUESTS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const createMutation = useMutation({
    mutationFn: createPermissionRequest,
    onSuccess: async () => {
      setCreateOpen(false);
      message.success(t('iam.permissionRequests.messages.submitted'));
      await refreshRequests();
    },
  });
  const reviewMutation = useMutation({
    mutationFn: ({ id, approved, comment }: { id: number; approved: boolean; comment?: string }) => (
      reviewPermissionRequest(id, approved, comment)
    ),
    onSuccess: async (_, variables) => {
      message.success(t(variables.approved
        ? 'iam.permissionRequests.messages.approved'
        : 'iam.permissionRequests.messages.rejected'));
      setDecision(undefined);
      setDecisionTarget(undefined);
      await refreshRequests();
    },
  });
  const revokeMutation = useMutation({
    mutationFn: ({ id, comment }: { id: number; comment: string }) => revokePermissionRequest(id, comment),
    onSuccess: async () => {
      message.success(t('iam.permissionRequests.messages.revoked'));
      setDecision(undefined);
      setDecisionTarget(undefined);
      await queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY });
      await refreshRequests();
    },
  });

  const createRequest = async (values: PermissionRequestFormValues): Promise<boolean> => {
    try {
      await createMutation.mutateAsync({
        ...values,
        requestReason: values.requestReason?.trim() || undefined,
      });
      return true;
    } catch {
      return false;
    }
  };

  const decideRequest = async (comment?: string): Promise<boolean> => {
    if (!decisionTarget || !decision) return false;
    try {
      if (decision === 'REVOKED') {
        await revokeMutation.mutateAsync({ id: decisionTarget.id, comment: comment?.trim() ?? '' });
      } else {
        await reviewMutation.mutateAsync({
          id: decisionTarget.id,
          approved: decision === 'APPROVED',
          comment: comment?.trim() || undefined,
        });
      }
      return true;
    } catch {
      return false;
    }
  };

  const columns = useMemo<ProColumns<PermissionRequest>[]>(() => [
    {
      title: t('iam.permissionRequests.fields.status'), dataIndex: 'status', hideInTable: true,
      initialValue: 'ALL', valueType: 'select',
      valueEnum: {
        ALL: { text: t('iam.filters.all') },
        PENDING_REVIEW: { text: t('iam.permissionRequests.statuses.PENDING_REVIEW') },
        APPROVED: { text: t('iam.permissionRequests.statuses.APPROVED') },
        REJECTED: { text: t('iam.permissionRequests.statuses.REJECTED') },
        REVOKED: { text: t('iam.permissionRequests.statuses.REVOKED') },
      },
    },
    {
      title: t('iam.permissionRequests.fields.targetUser'), dataIndex: 'targetUserId', hideInTable: true,
      valueType: 'select', fieldProps: {
        options: assignableUsers.flatMap((user) => user.id === undefined ? [] : [{
          label: user.username ?? String(user.id), value: user.id,
        }]),
        showSearch: true,
      },
    },
    {
      title: t('iam.permissionRequests.fields.requestedPackage'), dataIndex: 'requestedRoleId', hideInTable: true,
      valueType: 'select', fieldProps: {
        options: assignableRoles.flatMap((role) => role.id === undefined ? [] : [{
          label: `${role.roleName ?? role.roleCode} (${role.roleCode})`, value: role.id,
        }]),
        showSearch: true,
      },
    },
    { title: t('iam.permissionRequests.fields.targetUser'), dataIndex: 'targetUsername', width: 180, fixed: 'left', search: false },
    {
      title: t('iam.permissionRequests.fields.requestedPackage'), dataIndex: 'requestedRoleName', width: 230, search: false,
      render: (_, request) => <Space direction="vertical" size={0}><span>{request.requestedRoleName}</span><Tag>{request.requestedRoleCode}</Tag></Space>,
    },
    {
      title: t('iam.permissionRequests.fields.warehouses'), dataIndex: 'warehouses', width: 240, search: false,
      render: (_, request) => request.warehouses.length
        ? <Space size={[4, 4]} wrap>{request.warehouses.map((warehouse) => <Tag key={warehouse.id}>{warehouse.code ?? '-'} · {warehouse.name ?? '-'}</Tag>)}</Space>
        : '-',
    },
    {
      title: t('iam.permissionRequests.fields.risk'), dataIndex: 'highRiskPermissionCount', width: 120, search: false,
      render: (_, request) => <Tag color={request.highRiskPermissionCount ? 'red' : 'default'}>{t('iam.permissionRequests.highRiskCount', { count: request.highRiskPermissionCount })}</Tag>,
    },
    { title: t('iam.permissionRequests.fields.requester'), dataIndex: 'submittedByUsername', width: 150, search: false },
    { title: t('iam.permissionRequests.fields.requestedAt'), dataIndex: 'submittedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string, i18n.language) },
    {
      title: t('iam.permissionRequests.fields.status'), dataIndex: 'status', width: 130, search: false,
      render: (_, request) => <Tag color={request.status === 'APPROVED' ? 'success' : request.status === 'REJECTED' ? 'error' : request.status === 'REVOKED' ? 'default' : 'processing'}>{t(`iam.permissionRequests.statuses.${request.status}`)}</Tag>,
    },
    { title: t('iam.permissionRequests.fields.reviewer'), dataIndex: 'reviewedByUsername', width: 150, search: false, renderText: (value) => value || '-' },
    {
      title: t('common.actions'), valueType: 'option', width: 160, fixed: 'right',
      render: (_, request) => {
        if (request.status === 'APPROVED') {
          return <Tooltip title={t('iam.permissionRequests.actions.revoke')}><Button aria-label={t('iam.permissionRequests.actions.revoke')} danger icon={<StopOutlined />} onClick={() => { setDecisionTarget(request); setDecision('REVOKED'); }} size="small" type="text" /></Tooltip>;
        }
        if (request.status !== 'PENDING_REVIEW') return null;
        const highRiskBlocked = request.highRiskPermissionCount > 0
          && (!capabilities.canApproveHighRiskPackages || request.submittedByUsername === session?.username);
        return (
          <Space size={2}>
            <Tooltip title={highRiskBlocked ? t('iam.permissionRequests.highRiskApprovalBlocked') : t('iam.permissionRequests.actions.approve')}>
              <Button aria-label={t('iam.permissionRequests.actions.approve')} disabled={highRiskBlocked} icon={<CheckOutlined />} onClick={() => { setDecisionTarget(request); setDecision('APPROVED'); }} size="small" type="text" />
            </Tooltip>
            <Tooltip title={t('iam.permissionRequests.actions.reject')}>
              <Button aria-label={t('iam.permissionRequests.actions.reject')} danger icon={<CloseOutlined />} onClick={() => { setDecisionTarget(request); setDecision('REJECTED'); }} size="small" type="text" />
            </Tooltip>
          </Space>
        );
      },
    },
  ], [assignableRoles, assignableUsers, capabilities.canApproveHighRiskPackages, i18n.language, session?.username, t]);

  return (
    <section className="data-page">
      <Alert className="page-context-alert" description={t('iam.permissionRequests.liveNotice')} icon={<SafetyCertificateOutlined />} message={t('iam.permissionRequests.liveTitle')} showIcon type="warning" />
      <Alert className="page-context-alert" description={t('iam.permissionRequests.policyNotice')} showIcon type="info" />
      <ProTable<PermissionRequest, RequestTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('iam.permissionRequests.title')}
        loading={rolesQuery.isLoading || usersQuery.isLoading || permissionsQuery.isLoading || warehousesQuery.isLoading}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          const result = await listPermissionRequests({
            status: params.status && params.status !== 'ALL' ? params.status : undefined,
            targetUserId: params.targetUserId,
            requestedRoleId: params.requestedRoleId,
            page: Math.max((params.current ?? 1) - 1, 0),
            size: params.pageSize ?? 20,
          });
          return { data: result.items, success: true, total: result.total };
        }}
        rowKey="id"
        scroll={{ x: 1650 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => capabilities.canManage ? [
          <Button icon={<PlusOutlined />} key="create" onClick={() => setCreateOpen(true)} type="primary">{t('iam.permissionRequests.actions.create')}</Button>,
        ] : []}
      />
      <PermissionRequestModal
        loading={createMutation.isPending}
        onClose={() => setCreateOpen(false)}
        onSubmit={createRequest}
        open={createOpen}
        permissions={permissions}
        roles={assignableRoles}
        users={assignableUsers}
        warehouses={warehousesQuery.data ?? []}
      />
      <PermissionRequestDecisionModal
        decision={decision}
        loading={reviewMutation.isPending || revokeMutation.isPending}
        onClose={() => { setDecision(undefined); setDecisionTarget(undefined); }}
        onConfirm={decideRequest}
        request={decisionTarget}
      />
    </section>
  );
}

