import { Alert, App as AntdApp, Button, Card, Descriptions, Drawer, Empty, Form, Input, Modal, Pagination, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getPlatformAdmin,
  changePlatformAdminRole,
  changePlatformAdminStatus,
  listPlatformAdmins,
  resetPlatformAdminMfa,
  revokePlatformAdminSessions,
  startPlatformAdminMfaReset,
  startPlatformAdminRoleChange,
  startPlatformAdminSessionRevoke,
  startPlatformAdminStatusChange,
  type PlatformAdminDetail,
  type PlatformAdminFilters,
  type PlatformAdminMfaStatus,
  type PlatformAdminPage,
  type PlatformAdminRole,
  type PlatformAdminRoleType,
  type PlatformAdminSummary,
} from './api';
import { usePlatformAuth } from './PlatformAuthProvider';

const ADMIN_PAGE_SIZE = 20;
const ROLE_OPTIONS = [
  'PLATFORM_SUPER_ADMIN',
  'PLATFORM_OPERATIONS_ADMIN',
  'PLATFORM_SECURITY_AUDITOR',
  'PLATFORM_TENANT_READ',
  'PLATFORM_TENANT_EXPORT',
] as const;
const MFA_OPTIONS: PlatformAdminMfaStatus[] = ['NOT_ENROLLED', 'ENROLLED', 'TEMPORARILY_LOCKED'];

function formatDate(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '-';
}

function roleTags(roles: PlatformAdminRole[], types: PlatformAdminRoleType | PlatformAdminRoleType[]): JSX.Element {
  const includedTypes = Array.isArray(types) ? types : [types];
  const matching = roles.filter((role) => includedTypes.includes(role.type));
  if (matching.length === 0) return <Typography.Text type="secondary">-</Typography.Text>;
  return <Space size={[0, 4]} wrap>{matching.map((role) =>
    <Tag
      color={role.type === 'JOB_ROLE' ? 'red' : role.type === 'TECHNICAL_CAPABILITY' ? 'blue' : 'gold'}
      key={role.code}
      title={role.code}
    >
      {role.name}
    </Tag>)}</Space>;
}

export function PlatformAdminDirectory(): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const { message } = AntdApp.useApp();
  const { session } = usePlatformAuth();
  const superAdmin = Boolean(session?.roles.includes('PLATFORM_SUPER_ADMIN'));
  const [operationForm] = Form.useForm<{
    password: string;
    code: string;
    reason: string;
    jobRoleCode?: 'PLATFORM_OPERATIONS_ADMIN' | 'PLATFORM_SECURITY_AUDITOR';
  }>();
  const [operation, setOperation] = useState<'status' | 'sessions' | 'mfa' | 'role' | null>(null);
  const [operationLoading, setOperationLoading] = useState(false);
  const [draftKeyword, setDraftKeyword] = useState('');
  const [draftEnabled, setDraftEnabled] = useState<boolean>();
  const [draftRole, setDraftRole] = useState<string>();
  const [draftMfaStatus, setDraftMfaStatus] = useState<PlatformAdminMfaStatus>();
  const [appliedFilters, setAppliedFilters] = useState<PlatformAdminFilters>({});
  const [page, setPage] = useState(0);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [admins, setAdmins] = useState<PlatformAdminPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [selectedAdmin, setSelectedAdmin] = useState<PlatformAdminSummary | null>(null);
  const [detail, setDetail] = useState<PlatformAdminDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(false);
  const detailRequestId = useRef(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    void listPlatformAdmins(page, ADMIN_PAGE_SIZE, appliedFilters)
      .then((result) => { if (active) setAdmins(result); })
      .catch(() => { if (active) setError(true); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [appliedFilters, loadAttempt, page]);

  const search = () => {
    setPage(0);
    setAppliedFilters({
      keyword: draftKeyword.trim() || undefined,
      enabled: draftEnabled,
      role: draftRole,
      mfaStatus: draftMfaStatus,
    });
    setLoadAttempt((current) => current + 1);
  };

  const reset = () => {
    setDraftKeyword('');
    setDraftEnabled(undefined);
    setDraftRole(undefined);
    setDraftMfaStatus(undefined);
    setAppliedFilters({});
    setPage(0);
    setLoadAttempt((current) => current + 1);
  };

  const openDetail = (admin: PlatformAdminSummary) => {
    const requestId = detailRequestId.current + 1;
    detailRequestId.current = requestId;
    setSelectedAdmin(admin);
    setDetail(null);
    setDetailError(false);
    setDetailLoading(true);
    void getPlatformAdmin(admin.id)
      .then((result) => { if (detailRequestId.current === requestId) setDetail(result); })
      .catch(() => { if (detailRequestId.current === requestId) setDetailError(true); })
      .finally(() => { if (detailRequestId.current === requestId) setDetailLoading(false); });
  };

  const closeDetail = () => {
    detailRequestId.current += 1;
    setSelectedAdmin(null);
    setDetail(null);
    setDetailError(false);
    setDetailLoading(false);
  };

  const openOperation = (next: 'status' | 'sessions' | 'mfa' | 'role') => {
    operationForm.resetFields();
    setOperation(next);
  };

  const closeOperation = () => {
    if (operationLoading) return;
    setOperation(null);
    operationForm.resetFields();
  };

  const submitOperation = async (values: {
    password: string;
    code: string;
    reason: string;
    jobRoleCode?: 'PLATFORM_OPERATIONS_ADMIN' | 'PLATFORM_SECURITY_AUDITOR';
  }) => {
    if (!detail || !operation) return;
    setOperationLoading(true);
    try {
      const idempotencyKey = `admin-${operation}-${detail.id}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      if (operation === 'status') {
        const desiredEnabled = !detail.enabled;
        const challenge = await startPlatformAdminStatusChange(detail.id, desiredEnabled);
        await changePlatformAdminStatus(
          detail.id, desiredEnabled, challenge.challengeToken,
          values.password, values.code, values.reason.trim(), idempotencyKey,
        );
      } else if (operation === 'sessions') {
        const challenge = await startPlatformAdminSessionRevoke(detail.id);
        await revokePlatformAdminSessions(
          detail.id, challenge.challengeToken, values.password,
          values.code, values.reason.trim(), idempotencyKey,
        );
      } else if (operation === 'mfa') {
        const challenge = await startPlatformAdminMfaReset(detail.id);
        await resetPlatformAdminMfa(
          detail.id, challenge.challengeToken, values.password,
          values.code, values.reason.trim(), idempotencyKey,
        );
      } else if (values.jobRoleCode) {
        const challenge = await startPlatformAdminRoleChange(detail.id, values.jobRoleCode);
        await changePlatformAdminRole(
          detail.id, values.jobRoleCode, challenge.challengeToken,
          values.password, values.code, values.reason.trim(), idempotencyKey,
        );
      }
      message.success(english ? 'Administrator operation completed.' : '管理员操作已完成。');
      setOperation(null);
      operationForm.resetFields();
      setLoadAttempt((current) => current + 1);
      if (selectedAdmin) openDetail(selectedAdmin);
    } catch {
      message.error(english
        ? 'Operation failed. Check the account state, grants, password, and fresh MFA code.'
        : '操作失败，请检查账号状态、委派授权、当前密码及最新 MFA 验证码。');
    } finally {
      setOperationLoading(false);
    }
  };

  const mfaTag = (admin: PlatformAdminSummary) => {
    const labels: Record<PlatformAdminMfaStatus, string> = english
      ? { NOT_ENROLLED: 'Not enrolled', ENROLLED: 'Enrolled', TEMPORARILY_LOCKED: 'Temporarily locked' }
      : { NOT_ENROLLED: '未绑定', ENROLLED: '已绑定', TEMPORARILY_LOCKED: '临时锁定' };
    const color = admin.mfaStatus === 'ENROLLED' ? 'green'
      : admin.mfaStatus === 'TEMPORARILY_LOCKED' ? 'red' : 'gold';
    return <Space direction="vertical" size={2}>
      <Tag color={color}>{labels[admin.mfaStatus]}</Tag>
      {admin.mfaLockedUntil ? <Typography.Text type="secondary">
        {english ? 'Until ' : '至 '}{formatDate(admin.mfaLockedUntil)}
      </Typography.Text> : null}
    </Space>;
  };

  const columns: ColumnsType<PlatformAdminSummary> = [
    {
      title: english ? 'Administrator' : '管理员',
      width: 250,
      render: (_, admin) => <div className="platform-admin-identity">
        <strong>{admin.displayName}</strong>
        <Typography.Text type="secondary">{admin.email}</Typography.Text>
        <Space size={[0, 4]} wrap>
          {admin.currentUser ? <Tag color="blue">{english ? 'Current account' : '当前账号'}</Tag> : null}
          {admin.lastEnabledSuperAdmin ? <Tag color="red">{english ? 'Last enabled super admin' : '最后启用的超级管理员'}</Tag> : null}
        </Space>
      </div>,
    },
    {
      title: english ? 'Account' : '账号状态',
      width: 110,
      render: (_, admin) => <Tag color={admin.enabled ? 'green' : 'default'}>
        {admin.enabled ? (english ? 'Enabled' : '启用') : (english ? 'Disabled' : '停用')}
      </Tag>,
    },
    {
      title: english ? 'Job role / other' : '岗位／其他角色',
      width: 230,
      render: (_, admin) => roleTags(admin.roles, ['JOB_ROLE', 'UNCLASSIFIED']),
    },
    {
      title: english ? 'Technical capabilities' : '技术能力',
      width: 210,
      render: (_, admin) => roleTags(admin.roles, 'TECHNICAL_CAPABILITY'),
    },
    { title: 'MFA', width: 170, render: (_, admin) => mfaTag(admin) },
    {
      title: english ? 'Active grants' : '有效授权',
      dataIndex: 'activeGrantCount',
      width: 110,
      align: 'right',
    },
    {
      title: english ? 'Created' : '创建时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => formatDate(value),
    },
    {
      title: english ? 'Action' : '操作',
      fixed: 'right',
      width: 110,
      render: (_, admin) => <Button onClick={() => openDetail(admin)} size="small">
        {english ? 'View details' : '查看详情'}
      </Button>,
    },
  ];

  const detailRoleCodes = detail?.roles.map((role) => role.code) ?? [];
  const ordinaryTarget = detailRoleCodes.length > 0
    && detailRoleCodes.every((role) => [
      'PLATFORM_OPERATIONS_ADMIN',
      'PLATFORM_SECURITY_AUDITOR',
      'PLATFORM_TENANT_READ',
      'PLATFORM_TENANT_EXPORT',
    ].includes(role));
  const operationTitle = operation === 'status'
    ? (detail?.enabled ? (english ? 'Disable administrator' : '停用管理员') : (english ? 'Enable administrator' : '启用管理员'))
    : operation === 'sessions' ? (english ? 'Revoke all sessions' : '撤销全部会话')
      : operation === 'mfa' ? (english ? 'Reset MFA' : '重置 MFA')
        : (english ? 'Change ordinary job role' : '修改普通管理员岗位');

  return <>
    <Alert
      className="platform-readonly-alert"
      message={superAdmin
        ? (english
          ? 'Open an administrator detail to manage account status, sessions, MFA, or an ordinary job role. Every change requires your password and a fresh MFA code.'
          : '打开管理员详情可管理账号状态、会话、MFA 或普通岗位；每次变更都必须验证你的当前密码和最新 MFA 验证码。')
        : (english
          ? 'Security auditors can review this directory and security state, but cannot change accounts.'
          : '安全审计员可查看管理员目录与安全状态，但不能修改账号。')}
      showIcon
      type="info"
    />
    <Card className="platform-admin-directory-card" title={english ? 'Administrator directory' : '管理员目录'}>
      <div className="platform-admin-filters">
        <Input
          allowClear
          aria-label={english ? 'Administrator name or email' : '管理员姓名或邮箱'}
          maxLength={100}
          onChange={(event) => setDraftKeyword(event.target.value)}
          placeholder={english ? 'Name or email' : '姓名或邮箱'}
          value={draftKeyword}
        />
        <Select
          allowClear
          aria-label={english ? 'Account status' : '账号状态'}
          onChange={setDraftEnabled}
          options={[
            { value: true, label: english ? 'Enabled' : '启用' },
            { value: false, label: english ? 'Disabled' : '停用' },
          ]}
          placeholder={english ? 'All account states' : '全部账号状态'}
          value={draftEnabled}
        />
        <Select
          allowClear
          aria-label={english ? 'Role or capability' : '岗位或技术能力'}
          onChange={setDraftRole}
          options={ROLE_OPTIONS.map((role) => ({ value: role, label: role }))}
          placeholder={english ? 'All roles and capabilities' : '全部岗位和技术能力'}
          value={draftRole}
        />
        <Select
          allowClear
          aria-label="MFA"
          onChange={setDraftMfaStatus}
          options={MFA_OPTIONS.map((status) => ({
            value: status,
            label: english
              ? { NOT_ENROLLED: 'Not enrolled', ENROLLED: 'Enrolled', TEMPORARILY_LOCKED: 'Temporarily locked' }[status]
              : { NOT_ENROLLED: '未绑定', ENROLLED: '已绑定', TEMPORARILY_LOCKED: '临时锁定' }[status],
          }))}
          placeholder={english ? 'All MFA states' : '全部 MFA 状态'}
          value={draftMfaStatus}
        />
        <Space wrap>
          <Button loading={loading} onClick={search} type="primary">{english ? 'Search' : '查询'}</Button>
          <Button disabled={loading} onClick={reset}>{english ? 'Reset' : '重置'}</Button>
        </Space>
      </div>
      {error ? <Alert
        action={<Button onClick={() => setLoadAttempt((current) => current + 1)} size="small">
          {english ? 'Retry' : '重试'}
        </Button>}
        message={english ? 'Unable to load the administrator directory.' : '管理员目录加载失败。'}
        showIcon
        type="error"
      /> : null}
      <Table
        columns={columns}
        dataSource={admins?.content ?? []}
        loading={loading}
        locale={{ emptyText: <Empty description={english ? 'No administrators' : '暂无管理员'} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
        pagination={false}
        rowKey="id"
        scroll={{ x: 1390 }}
        size="small"
      />
      {admins ? <Typography.Text className="platform-directory-summary" type="secondary">
        {english
          ? `${admins.totalElements} administrators · page ${admins.totalPages === 0 ? 0 : page + 1} of ${admins.totalPages}`
          : `共 ${admins.totalElements} 名管理员 · 第 ${admins.totalPages === 0 ? 0 : page + 1} / ${admins.totalPages} 页`}
      </Typography.Text> : null}
      {(admins?.totalElements ?? 0) > ADMIN_PAGE_SIZE ? <Pagination
        current={page + 1}
        pageSize={ADMIN_PAGE_SIZE}
        showSizeChanger={false}
        total={admins?.totalElements ?? 0}
        onChange={(nextPage) => setPage(nextPage - 1)}
        size="small"
      /> : null}
    </Card>
    <Drawer
      className="platform-admin-detail-drawer"
      onClose={closeDetail}
      open={selectedAdmin !== null}
      title={english ? 'Administrator details' : '管理员详情'}
      width={600}
    >
      {detailError ? <Alert
        action={selectedAdmin ? <Button onClick={() => openDetail(selectedAdmin)} size="small">
          {english ? 'Retry' : '重试'}
        </Button> : undefined}
        message={english ? 'Unable to load administrator details.' : '管理员详情加载失败。'}
        showIcon
        type="error"
      /> : null}
      <Spin spinning={detailLoading}>
        {detail ? <>
          <div className="platform-admin-detail-heading">
            <Typography.Title level={4}>{detail.displayName}</Typography.Title>
            <Typography.Text type="secondary">{detail.email}</Typography.Text>
          </div>
          <Descriptions bordered column={1} size="small" items={[
            { key: 'account', label: english ? 'Account status' : '账号状态', children: detail.enabled ? (english ? 'Enabled' : '启用') : (english ? 'Disabled' : '停用') },
            { key: 'current', label: english ? 'Protection markers' : '保护标记', children: <Space wrap>{detail.currentUser ? <Tag color="blue">{english ? 'Current account' : '当前账号'}</Tag> : null}{detail.lastEnabledSuperAdmin ? <Tag color="red">{english ? 'Last enabled super admin' : '最后启用的超级管理员'}</Tag> : null}{!detail.currentUser && !detail.lastEnabledSuperAdmin ? '-' : null}</Space> },
            { key: 'jobs', label: english ? 'Job roles' : '平台岗位', children: roleTags(detail.roles, 'JOB_ROLE') },
            { key: 'capabilities', label: english ? 'Technical capabilities' : '技术能力', children: roleTags(detail.roles, 'TECHNICAL_CAPABILITY') },
            { key: 'unknown', label: english ? 'Unclassified roles' : '未分类角色', children: roleTags(detail.roles, 'UNCLASSIFIED') },
            { key: 'mfa', label: 'MFA', children: mfaTag(detail) },
            { key: 'mfaEnrolled', label: english ? 'MFA enrolled at' : 'MFA 绑定时间', children: formatDate(detail.mfaEnrolledAt) },
            { key: 'grants', label: english ? 'Active delegated grants' : '有效委派授权', children: english ? `${detail.activeGrantCount} total · READ ${detail.activeGrantCounts.READ} · EXPORT ${detail.activeGrantCounts.EXPORT}` : `共 ${detail.activeGrantCount} 条 · 读取 ${detail.activeGrantCounts.READ} · 导出 ${detail.activeGrantCounts.EXPORT}` },
            { key: 'created', label: english ? 'Created at' : '创建时间', children: formatDate(detail.createdAt) },
            { key: 'updated', label: english ? 'Updated at' : '更新时间', children: formatDate(detail.updatedAt) },
          ]} />
          {superAdmin ? <Card
            className="platform-admin-detail-actions"
            size="small"
            title={english ? 'Protected operations' : '受保护操作'}
          >
            <Alert
              message={english
                ? 'Each button opens its own confirmation. The current signed-in account cannot be targeted.'
                : '每个按钮都会打开独立确认流程；不能操作当前登录账号。'}
              showIcon
              type="warning"
            />
            <Space style={{ marginTop: 12 }} wrap>
              <Button
                danger={detail.enabled}
                disabled={detail.currentUser || (detail.enabled && detail.lastEnabledSuperAdmin)}
                onClick={() => openOperation('status')}
              >
                {detail.enabled ? (english ? 'Disable account' : '停用账号') : (english ? 'Enable account' : '启用账号')}
              </Button>
              <Button
                disabled={detail.currentUser || !detail.enabled}
                onClick={() => openOperation('sessions')}
              >
                {english ? 'Revoke all sessions' : '撤销全部会话'}
              </Button>
              <Button
                danger
                disabled={detail.currentUser || !detail.enabled || detail.mfaStatus === 'NOT_ENROLLED'}
                onClick={() => openOperation('mfa')}
              >
                {english ? 'Reset MFA' : '重置 MFA'}
              </Button>
              <Button
                disabled={detail.currentUser || !ordinaryTarget}
                onClick={() => openOperation('role')}
              >
                {english ? 'Change job role' : '修改普通岗位'}
              </Button>
            </Space>
          </Card> : null}
        </> : null}
      </Spin>
    </Drawer>
    <Modal
      destroyOnClose
      footer={null}
      onCancel={closeOperation}
      open={operation !== null}
      title={operationTitle}
    >
      {operation === 'role' ? <Alert
        message={english
          ? 'Switching to security auditor is blocked until all current and future delegated data grants are revoked. A disabled account remains disabled.'
          : '改为安全审计员前，必须先撤销所有当前有效及未来生效的数据委派；停用账号改岗后仍保持停用。'}
        showIcon
        type="info"
      /> : null}
      {operation === 'mfa' ? <Alert
        message={english
          ? 'The target loses all sessions, authenticator binding, and recovery codes, then must bind MFA again at the next sign-in.'
          : '目标账号的全部会话、认证器绑定和恢复码会立即失效；下次登录时必须重新绑定 MFA。'}
        showIcon
        type="warning"
      /> : null}
      <Form
        form={operationForm}
        layout="vertical"
        onFinish={(values) => void submitOperation(values)}
        style={{ marginTop: 16 }}
      >
        {operation === 'role' ? <Form.Item
          label={english ? 'New job role' : '新岗位'}
          name="jobRoleCode"
          rules={[{ required: true }]}
        >
          <Select options={[
            { value: 'PLATFORM_OPERATIONS_ADMIN', label: english ? 'Platform operations administrator' : '平台运营管理员' },
            { value: 'PLATFORM_SECURITY_AUDITOR', label: english ? 'Platform security auditor' : '平台安全审计员' },
          ]} />
        </Form.Item> : null}
        <Form.Item label={english ? 'Operation reason' : '操作原因'} name="reason" rules={[{ required: true }, { max: 500 }]}>
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
        <Form.Item label={english ? 'Your current password' : '你的当前密码'} name="password" rules={[{ required: true }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item label={english ? 'Your fresh 6-digit MFA code' : '你的最新 6 位 MFA 验证码'} name="code" rules={[{ required: true }, { pattern: /^\d{6}$/ }]}>
          <Input autoComplete="one-time-code" inputMode="numeric" maxLength={6} />
        </Form.Item>
        <Space>
          <Button htmlType="submit" loading={operationLoading} type="primary">{english ? 'Confirm operation' : '确认执行'}</Button>
          <Button disabled={operationLoading} onClick={closeOperation}>{english ? 'Cancel' : '取消'}</Button>
        </Space>
      </Form>
    </Modal>
  </>;
}
