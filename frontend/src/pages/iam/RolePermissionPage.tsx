import {
  AuditOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  EditOutlined,
  EyeOutlined,
  HistoryOutlined,
  LockOutlined,
  PlusOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Descriptions, Drawer, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  copyRole,
  activateRolePackage,
  deactivateRolePackage,
  createRolePackageDraft,
  getRoleCopyPreview,
  getRoleGovernanceHistory,
  listPermissions,
  listRolePermissions,
  listRoles,
  listUsers,
  reviewRolePackage,
  submitRolePackageReview,
  updateRolePackageDraft,
  PERMISSIONS_QUERY_KEY,
  ROLES_QUERY_KEY,
  USERS_QUERY_KEY,
  type Permission,
  type Role,
  type RoleCopyRequest,
  type RoleCopyResult,
  type RoleGovernanceResult,
  type RolePackageCreateRequest,
  type RolePackageDraftRequest,
} from '../../api/iam';
import { paginateArray } from '../../api/pagination';
import { useAuth } from '../../auth/AuthProvider';
import { getIamCapabilities } from './capabilities';
import { roleLabel } from './iamUtils';
import { RoleCopyModal, type RoleCopyFormValues } from './RoleCopyModal';
import { RoleGovernanceModal, type RoleGovernanceMode } from './RoleGovernanceModal';
import { RolePackageCreateModal } from './RolePackageCreateModal';

interface RoleTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  status?: 'ALL' | 'ACTIVE' | 'DISABLED';
  roleType?: 'ALL' | 'SYSTEM' | 'CUSTOM';
  reviewStatus?: 'ALL' | 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED';
  roleDataVersion?: number;
  userDataVersion?: number;
}

interface RoleRow extends Role {
  userCount: number;
}

export function RolePermissionPage(): JSX.Element {
  const [selectedRole, setSelectedRole] = useState<RoleRow>();
  const [copySource, setCopySource] = useState<RoleRow>();
  const [copyResult, setCopyResult] = useState<RoleCopyResult>();
  const [createOpen, setCreateOpen] = useState(false);
  const [governanceTarget, setGovernanceTarget] = useState<RoleRow>();
  const [governanceMode, setGovernanceMode] = useState<RoleGovernanceMode>();
  const { t } = useTranslation();
  const { session } = useAuth();
  const capabilities = getIamCapabilities(session?.currentRole ?? '');
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const rolesQuery = useQuery({ queryKey: ROLES_QUERY_KEY, queryFn: () => listRoles(false) });
  const usersQuery = useQuery({ queryKey: USERS_QUERY_KEY, queryFn: listUsers });
  const permissionsQuery = useQuery({ queryKey: PERMISSIONS_QUERY_KEY, queryFn: () => listPermissions() });
  const rolePermissionsQuery = useQuery({
    queryKey: ['iam', 'roles', selectedRole?.id, 'permissions'],
    queryFn: () => listRolePermissions(selectedRole?.id as number),
    enabled: selectedRole?.id !== undefined,
  });
  const copyPreviewQuery = useQuery({
    queryKey: ['iam', 'roles', copySource?.id, 'copy-preview'],
    queryFn: () => getRoleCopyPreview(copySource?.id as number),
    enabled: copySource?.id !== undefined && copyResult === undefined,
    retry: false,
  });
  const governancePermissionsQuery = useQuery({
    queryKey: ['iam', 'roles', governanceTarget?.id, 'permissions'],
    queryFn: () => listRolePermissions(governanceTarget?.id as number),
    enabled: governanceTarget?.id !== undefined && governanceMode !== 'HISTORY',
  });
  const governanceHistoryQuery = useQuery({
    queryKey: ['iam', 'roles', governanceTarget?.id, 'governance-history'],
    queryFn: () => getRoleGovernanceHistory(governanceTarget?.id as number),
    enabled: governanceTarget?.id !== undefined && governanceMode === 'HISTORY',
  });
  const copyMutation = useMutation({
    mutationFn: ({ sourceRoleId, payload }: { sourceRoleId: number; payload: RoleCopyRequest }) => copyRole(sourceRoleId, payload),
  });
  const createMutation = useMutation({ mutationFn: createRolePackageDraft });
  const draftMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: RolePackageDraftRequest }) => updateRolePackageDraft(id, payload) });
  const submitReviewMutation = useMutation({ mutationFn: ({ id, reason }: { id: number; reason?: string }) => submitRolePackageReview(id, { reason }) });
  const reviewMutation = useMutation({ mutationFn: ({ id, approved, comment }: { id: number; approved: boolean; comment?: string }) => reviewRolePackage(id, { approved, comment }) });
  const runtimeMutation = useMutation({
    mutationFn: ({ id, mode, reason, confirmationCode }: { id: number; mode: 'ACTIVATE' | 'DEACTIVATE'; reason: string; confirmationCode: string }) => (
      mode === 'ACTIVATE'
        ? activateRolePackage(id, { reason, confirmationCode })
        : deactivateRolePackage(id, { reason, confirmationCode })
    ),
  });

  const closeCopyModal = (): void => {
    setCopySource(undefined);
    setCopyResult(undefined);
    copyMutation.reset();
  };

  const closeGovernanceModal = (): void => {
    setGovernanceTarget(undefined);
    setGovernanceMode(undefined);
    draftMutation.reset();
    submitReviewMutation.reset();
    reviewMutation.reset();
    runtimeMutation.reset();
  };

  const refreshGovernanceData = async (result: RoleGovernanceResult): Promise<void> => {
    message.success(t(`iam.roles.governance.messages.${result.action ?? 'UPDATE_DRAFT'}`));
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ROLES_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: ['iam', 'roles', governanceTarget?.id, 'permissions'] }),
      queryClient.invalidateQueries({ queryKey: ['iam', 'roles', governanceTarget?.id, 'governance-history'] }),
    ]);
    closeGovernanceModal();
  };

  const saveDraft = async (payload: RolePackageDraftRequest): Promise<boolean> => {
    if (governanceTarget?.id === undefined) return false;
    try {
      await refreshGovernanceData(await draftMutation.mutateAsync({ id: governanceTarget.id, payload }));
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const submitGovernanceReview = async (reason?: string): Promise<boolean> => {
    if (governanceTarget?.id === undefined) return false;
    try {
      await refreshGovernanceData(await submitReviewMutation.mutateAsync({ id: governanceTarget.id, reason }));
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const reviewGovernance = async (approved: boolean, comment?: string): Promise<boolean> => {
    if (governanceTarget?.id === undefined) return false;
    try {
      await refreshGovernanceData(await reviewMutation.mutateAsync({ id: governanceTarget.id, approved, comment }));
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const changeRuntimeStatus = async (reason: string, confirmationCode: string): Promise<boolean> => {
    if (governanceTarget?.id === undefined || (governanceMode !== 'ACTIVATE' && governanceMode !== 'DEACTIVATE')) return false;
    try {
      await refreshGovernanceData(await runtimeMutation.mutateAsync({
        id: governanceTarget.id,
        mode: governanceMode,
        reason,
        confirmationCode,
      }));
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const openGovernance = (role: RoleRow, mode: RoleGovernanceMode): void => {
    setGovernanceTarget(role);
    setGovernanceMode(mode);
  };

  const submitCopy = async (values: RoleCopyFormValues): Promise<boolean> => {
    const sourceRoleId = copySource?.id;
    const snapshotFingerprint = copyPreviewQuery.data?.snapshotFingerprint;
    if (sourceRoleId === undefined || !snapshotFingerprint) return false;
    try {
      const result = await copyMutation.mutateAsync({
        sourceRoleId,
        payload: { ...values, snapshotFingerprint },
      });
      setCopyResult(result);
      message.success(t('iam.roles.copy.created'));
      await queryClient.invalidateQueries({ queryKey: ROLES_QUERY_KEY });
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      if ((error as { status?: number }).status === 409) {
        await copyPreviewQuery.refetch();
      }
      return false;
    }
  };

  const submitBlankDraft = async (payload: RolePackageCreateRequest): Promise<boolean> => {
    try {
      const result = await createMutation.mutateAsync(payload);
      message.success(t(`iam.roles.governance.messages.${result.action ?? 'CREATE_DRAFT'}`));
      setCreateOpen(false);
      await queryClient.invalidateQueries({ queryKey: ROLES_QUERY_KEY });
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

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
    {
      title: t('iam.roles.fields.reviewStatus'), dataIndex: 'reviewStatus', hideInTable: true, initialValue: 'ALL', valueType: 'select',
      valueEnum: {
        ALL: { text: t('iam.filters.all') },
        DRAFT: { text: t('iam.roles.reviewStatuses.DRAFT') },
        PENDING_REVIEW: { text: t('iam.roles.reviewStatuses.PENDING_REVIEW') },
        APPROVED: { text: t('iam.roles.reviewStatuses.APPROVED') },
      },
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
    {
      title: t('iam.roles.fields.reviewStatus'), dataIndex: 'reviewStatus', width: 120, search: false,
      render: (_, row) => row.roleType === 'CUSTOM'
        ? <Tag color={row.reviewStatus === 'APPROVED' ? 'success' : row.reviewStatus === 'PENDING_REVIEW' ? 'processing' : 'gold'}>{t(`iam.roles.reviewStatuses.${row.reviewStatus ?? 'DRAFT'}`)}</Tag>
        : <Tag color="blue">{t('iam.roles.reviewStatuses.SYSTEM_MANAGED')}</Tag>,
    },
    { title: t('iam.roles.fields.users'), dataIndex: 'userCount', width: 100, align: 'right', search: false },
    { title: t('iam.roles.fields.directPermissions'), dataIndex: 'permissionIds', width: 120, align: 'right', search: false, renderText: (value) => Array.isArray(value) ? value.length : 0 },
    { title: t('iam.roles.fields.inheritedRoles'), dataIndex: 'parentRoleIds', width: 120, align: 'right', search: false, renderText: (value) => Array.isArray(value) ? value.length : 0 },
    { title: t('iam.roles.fields.description'), dataIndex: 'description', width: 280, search: false, ellipsis: true, renderText: (value) => value || '-' },
    {
      title: t('common.actions'), valueType: 'option', width: 230, fixed: 'right',
      render: (_, row) => {
        const canCopy = row.id !== undefined && row.status === 'ACTIVE' && row.importAllowed === true && row.privilegedRole !== true;
        const actions = [
          <Tooltip key="view" title={t('iam.roles.actions.review')}>
            <Button aria-label={t('iam.roles.actions.review')} icon={<EyeOutlined />} onClick={() => setSelectedRole(row)} size="small" type="text" />
          </Tooltip>,
          canCopy ? (
            <Tooltip key="copy" title={t('iam.roles.actions.copy')}>
              <Button
                aria-label={t('iam.roles.actions.copy')}
                icon={<CopyOutlined />}
                onClick={() => { setCopyResult(undefined); setCopySource(row); }}
                size="small"
                type="text"
              />
            </Tooltip>
          ) : null,
        ];
        if (row.roleType === 'CUSTOM') {
          if (row.status === 'DISABLED' && row.reviewStatus === 'DRAFT') {
            actions.push(
              <Tooltip key="edit" title={t('iam.roles.governance.edit')}>
                <Button aria-label={t('iam.roles.governance.edit')} icon={<EditOutlined />} onClick={() => openGovernance(row, 'EDIT')} size="small" type="text" />
              </Tooltip>,
              <Tooltip key="submit" title={t('iam.roles.governance.submit')}>
                <Button aria-label={t('iam.roles.governance.submit')} icon={<SendOutlined />} onClick={() => openGovernance(row, 'SUBMIT')} size="small" type="text" />
              </Tooltip>,
            );
          }
          if (row.reviewStatus === 'PENDING_REVIEW') {
            actions.push(
              <Tooltip key="governance-review" title={t('iam.roles.governance.review')}>
                <Button aria-label={t('iam.roles.governance.review')} icon={<AuditOutlined />} onClick={() => openGovernance(row, 'REVIEW')} size="small" type="text" />
              </Tooltip>,
            );
          }
          if (row.status === 'DISABLED' && row.reviewStatus === 'APPROVED') {
            actions.push(
              <Tooltip key="activate" title={t('iam.roles.governance.activate')}>
                <Button aria-label={t('iam.roles.governance.activate')} icon={<CheckCircleOutlined />} onClick={() => openGovernance(row, 'ACTIVATE')} size="small" type="text" />
              </Tooltip>,
            );
          }
          if (row.status === 'ACTIVE') {
            actions.push(
              <Tooltip key="deactivate" title={t('iam.roles.governance.deactivate')}>
                <Button aria-label={t('iam.roles.governance.deactivate')} danger icon={<StopOutlined />} onClick={() => openGovernance(row, 'DEACTIVATE')} size="small" type="text" />
              </Tooltip>,
            );
          }
          actions.push(
            <Tooltip key="history" title={t('iam.roles.governance.history.title')}>
              <Button aria-label={t('iam.roles.governance.history.title')} icon={<HistoryOutlined />} onClick={() => openGovernance(row, 'HISTORY')} size="small" type="text" />
            </Tooltip>,
          );
        }
        return actions;
      },
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
      title: t('iam.permissions.fields.risk'), dataIndex: 'riskLevel', width: 90,
      render: (value: string | undefined) => (
        <Tag color={value === 'CRITICAL' ? 'magenta' : value === 'HIGH' ? 'red' : 'default'}>
          {t(`iam.permissions.risks.${value ?? 'NORMAL'}`)}
        </Tag>
      ),
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
            if (params.reviewStatus !== 'ALL' && params.reviewStatus && role.reviewStatus !== params.reviewStatus) return false;
            return !keyword || [role.roleCode, role.roleName, role.description]
              .some((value) => value?.toLowerCase().includes(keyword));
          });
          return paginateArray(filtered, params);
        }}
        params={{ roleDataVersion: roles.length, userDataVersion: usersQuery.data?.length ?? 0 }}
        rowKey={(row) => String(row.id ?? row.roleCode)}
        scroll={{ x: 1740 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <Tag color="blue" key="catalog">{t('iam.roles.catalogMode')}</Tag>,
          capabilities.canManage ? (
            <Button icon={<PlusOutlined />} key="create-blank" onClick={() => setCreateOpen(true)} type="primary">
              {t('iam.roles.actions.createBlank')}
            </Button>
          ) : null,
        ]}
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
              <Descriptions.Item label={t('iam.roles.fields.reviewStatus')}>
                {selectedRole.roleType === 'CUSTOM'
                  ? <Tag color={selectedRole.reviewStatus === 'APPROVED' ? 'success' : selectedRole.reviewStatus === 'PENDING_REVIEW' ? 'processing' : 'gold'}>{t(`iam.roles.reviewStatuses.${selectedRole.reviewStatus ?? 'DRAFT'}`)}</Tag>
                  : <Tag color="blue">{t('iam.roles.reviewStatuses.SYSTEM_MANAGED')}</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label={t('iam.roles.fields.approvalTemplate')}>
                {selectedRole.approvalTemplateCode
                  ? t(`iam.roles.approvalTemplates.${selectedRole.approvalTemplateCode}`, { defaultValue: selectedRole.approvalTemplateCode })
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('iam.roles.governance.submitter')}>{selectedRole.reviewSubmittedByUsername || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('iam.roles.governance.reviewer')}>{selectedRole.reviewedByUsername || '-'}</Descriptions.Item>
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
                scroll={{ x: 810 }}
                size="small"
              />
            ) : <Empty description={t('iam.roles.noDirectPermissions')} image={Empty.PRESENTED_IMAGE_SIMPLE} />}
          </>
        )}
      </Drawer>

      <RoleGovernanceModal
        canApproveHighRisk={capabilities.canApproveHighRiskPackages}
        directPermissions={governancePermissionsQuery.data ?? []}
        history={governanceHistoryQuery.data ?? []}
        loading={governancePermissionsQuery.isLoading || governanceHistoryQuery.isLoading}
        mode={governanceMode}
        onClose={closeGovernanceModal}
        onReview={reviewGovernance}
        onRuntimeChange={changeRuntimeStatus}
        onSaveDraft={saveDraft}
        onSubmitReview={submitGovernanceReview}
        permissionCatalog={permissionsQuery.data ?? []}
        role={governanceTarget}
        submitting={draftMutation.isPending || submitReviewMutation.isPending || reviewMutation.isPending || runtimeMutation.isPending}
      />

      <RolePackageCreateModal
        loading={permissionsQuery.isLoading}
        onClose={() => { setCreateOpen(false); createMutation.reset(); }}
        onSubmit={submitBlankDraft}
        open={createOpen}
        permissionCatalog={permissionsQuery.data ?? []}
        submitting={createMutation.isPending}
      />

      <RoleCopyModal
        errorMessage={copyPreviewQuery.error ? getErrorMessage(copyPreviewQuery.error, t) : undefined}
        loading={copyPreviewQuery.isLoading}
        onClose={closeCopyModal}
        onSubmit={submitCopy}
        open={copySource !== undefined}
        preview={copyPreviewQuery.data}
        result={copyResult}
        source={copySource}
        submitting={copyMutation.isPending}
      />
    </section>
  );
}
