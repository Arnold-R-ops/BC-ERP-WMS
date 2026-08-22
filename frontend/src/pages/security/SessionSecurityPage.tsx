import {
  AuditOutlined,
  ClockCircleOutlined,
  LogoutOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../../api/errors';
import { listUsers, revokeUserSessions, type UserAccount } from '../../api/iam';
import {
  listSessionSecurityAudits,
  SESSION_SECURITY_AUDITS_QUERY_KEY,
  type SessionSecurityAudit,
} from '../../api/sessionSecurity';
import { useAuth } from '../../auth/AuthProvider';
import { formatDateTime } from '../workflowUtils';

interface OwnRevokeForm {
  currentPassword: string;
}

interface AdminRevokeForm {
  targetUserId: number;
  reason: string;
}

interface PendingAdminRevoke {
  target: UserAccount;
  reason: string;
}

export function SessionSecurityPage(): JSX.Element {
  const { session, revokeAllSessions } = useAuth();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [ownRevokeOpen, setOwnRevokeOpen] = useState(false);
  const [ownRevokeForm] = Form.useForm<OwnRevokeForm>();
  const [adminRevokeForm] = Form.useForm<AdminRevokeForm>();
  const [pendingAdminRevoke, setPendingAdminRevoke] = useState<PendingAdminRevoke>();
  const canAdministerSessionSecurity = session?.currentRole === 'TENANT_ADMIN';

  const usersQuery = useQuery({
    enabled: canAdministerSessionSecurity,
    queryFn: listUsers,
    queryKey: ['session-security', 'users'],
  });
  const auditsQuery = useQuery({
    enabled: canAdministerSessionSecurity,
    queryFn: () => listSessionSecurityAudits(100),
    queryKey: SESSION_SECURITY_AUDITS_QUERY_KEY,
  });
  const ownRevokeMutation = useMutation({ mutationFn: revokeAllSessions });
  const adminRevokeMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => revokeUserSessions(id, reason),
  });

  const manageableUsers = useMemo(() => (usersQuery.data ?? [])
    .filter((user) => user.id !== undefined && user.username !== session?.username), [
      session?.username,
      usersQuery.data,
    ]);

  const userOptions = useMemo(() => manageableUsers.map((user) => ({
    label: `${user.displayName || user.username || '-'} (${user.username || '-'})`,
    value: user.id as number,
  })), [manageableUsers]);

  const auditColumns = useMemo<TableColumnsType<SessionSecurityAudit>>(() => [
    {
      dataIndex: 'createdAt',
      title: t('sessionSecurity.audit.fields.time'),
      width: 180,
      render: (value: string) => formatDateTime(value, i18n.language),
    },
    {
      dataIndex: 'action',
      title: t('sessionSecurity.audit.fields.action'),
      width: 180,
      render: (value: SessionSecurityAudit['action']) => (
        <Tag color={value === 'ADMIN_REVOKE_ALL_SESSIONS' ? 'orange' : 'blue'}>
          {t(`sessionSecurity.audit.actions.${value}`)}
        </Tag>
      ),
    },
    { dataIndex: 'operatorUsername', title: t('sessionSecurity.audit.fields.operator'), width: 150 },
    { dataIndex: 'targetUsername', title: t('sessionSecurity.audit.fields.target'), width: 180 },
    { dataIndex: 'reason', title: t('sessionSecurity.audit.fields.reason'), ellipsis: true },
    {
      dataIndex: 'result',
      title: t('sessionSecurity.audit.fields.result'),
      width: 110,
      render: (value: SessionSecurityAudit['result']) => (
        <Tag color={value === 'SUCCESS' ? 'success' : 'error'}>
          {t(`sessionSecurity.audit.results.${value}`)}
        </Tag>
      ),
    },
  ], [i18n.language, t]);

  const submitOwnRevoke = async (): Promise<void> => {
    try {
      const values = await ownRevokeForm.validateFields();
      await ownRevokeMutation.mutateAsync(values.currentPassword);
      ownRevokeForm.resetFields();
      setOwnRevokeOpen(false);
      message.success(t('auth.revokeAllSuccess'));
      navigate('/login', { replace: true });
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(getErrorMessage(error, t));
    }
  };

  const prepareAdminRevoke = (values: AdminRevokeForm): void => {
    const target = manageableUsers.find((user) => user.id === values.targetUserId);
    if (!target) {
      message.error(t('sessionSecurity.admin.targetUnavailable'));
      return;
    }
    setPendingAdminRevoke({ target, reason: values.reason.trim() });
  };

  const confirmAdminRevoke = async (): Promise<void> => {
    if (pendingAdminRevoke?.target.id === undefined) return;
    try {
      await adminRevokeMutation.mutateAsync({
        id: pendingAdminRevoke.target.id,
        reason: pendingAdminRevoke.reason,
      });
      message.success(t('sessionSecurity.admin.success'));
      setPendingAdminRevoke(undefined);
      adminRevokeForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: SESSION_SECURITY_AUDITS_QUERY_KEY });
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  return (
    <section className="data-page">
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <div>
          <Typography.Title level={2} style={{ marginBottom: 4 }}>{t('sessionSecurity.title')}</Typography.Title>
          <Typography.Text type="secondary">{t('sessionSecurity.subtitle')}</Typography.Text>
        </div>

        <Alert
          description={t('sessionSecurity.boundaryDescription')}
          message={t('sessionSecurity.boundaryTitle')}
          showIcon
          type="info"
        />

        <Card title={<Space><SafetyCertificateOutlined />{t('sessionSecurity.personal.title')}</Space>}>
          <Row gutter={[16, 16]}>
            <Col lg={8} md={12} xs={24}>
              <Statistic prefix={<ClockCircleOutlined />} suffix={t('sessionSecurity.policy.days')} title={t('sessionSecurity.policy.absoluteTitle')} value={7} />
              <Typography.Paragraph type="secondary">{t('sessionSecurity.policy.absoluteDescription')}</Typography.Paragraph>
            </Col>
            <Col lg={8} md={12} xs={24}>
              <Statistic prefix={<ClockCircleOutlined />} suffix={t('sessionSecurity.policy.minutes')} title={t('sessionSecurity.policy.idleTitle')} value={60} />
              <Typography.Paragraph type="secondary">{t('sessionSecurity.policy.idleDescription')}</Typography.Paragraph>
            </Col>
            <Col lg={8} md={24} xs={24}>
              <Statistic prefix={<SafetyCertificateOutlined />} suffix={t('sessionSecurity.policy.hours')} title={t('sessionSecurity.policy.tokenTitle')} value={24} />
              <Typography.Paragraph type="secondary">{t('sessionSecurity.policy.tokenDescription')}</Typography.Paragraph>
            </Col>
          </Row>
          <Space direction="vertical">
            <Typography.Text strong>{t('sessionSecurity.personal.revokeTitle')}</Typography.Text>
            <Typography.Text type="secondary">{t('sessionSecurity.personal.revokeDescription')}</Typography.Text>
            <Button danger icon={<LogoutOutlined />} onClick={() => setOwnRevokeOpen(true)}>
              {t('auth.revokeAllSessions')}
            </Button>
          </Space>
        </Card>

        {canAdministerSessionSecurity ? (
          <>
            <Card title={<Space><StopOutlined />{t('sessionSecurity.admin.title')}</Space>}>
              <Alert
                description={t('sessionSecurity.admin.description')}
                message={t('sessionSecurity.admin.notice')}
                showIcon
                style={{ marginBottom: 20 }}
                type="warning"
              />
              <Form<AdminRevokeForm>
                form={adminRevokeForm}
                layout="vertical"
                onFinish={prepareAdminRevoke}
              >
                <Form.Item
                  label={t('sessionSecurity.admin.target')}
                  name="targetUserId"
                  rules={[{ required: true, message: t('sessionSecurity.admin.targetRequired') }]}
                >
                  <Select
                    loading={usersQuery.isLoading}
                    optionFilterProp="label"
                    options={userOptions}
                    placeholder={t('sessionSecurity.admin.targetPlaceholder')}
                    showSearch
                  />
                </Form.Item>
                <Form.Item
                  label={t('sessionSecurity.admin.reason')}
                  name="reason"
                  rules={[
                    { required: true, message: t('sessionSecurity.admin.reasonRequired') },
                    { min: 5, message: t('sessionSecurity.admin.reasonLength') },
                    { max: 500 },
                  ]}
                >
                  <Input.TextArea maxLength={500} placeholder={t('sessionSecurity.admin.reasonPlaceholder')} rows={4} showCount />
                </Form.Item>
                <Button danger htmlType="submit" icon={<StopOutlined />}>
                  {t('sessionSecurity.admin.submit')}
                </Button>
              </Form>
            </Card>

            <Card
              extra={<Button icon={<AuditOutlined />} loading={auditsQuery.isFetching} onClick={() => void auditsQuery.refetch()}>{t('common.refresh')}</Button>}
              title={<Space><AuditOutlined />{t('sessionSecurity.audit.title')}</Space>}
            >
              <Table<SessionSecurityAudit>
                columns={auditColumns}
                dataSource={auditsQuery.data ?? []}
                loading={auditsQuery.isLoading}
                locale={{ emptyText: auditsQuery.isError ? t('sessionSecurity.audit.loadFailed') : undefined }}
                pagination={{ defaultPageSize: 20, showSizeChanger: true }}
                rowKey="id"
                scroll={{ x: 1000 }}
              />
            </Card>
          </>
        ) : null}
      </Space>

      <Modal
        confirmLoading={ownRevokeMutation.isPending}
        okButtonProps={{ danger: true }}
        okText={t('auth.revokeAllConfirm')}
        onCancel={() => { setOwnRevokeOpen(false); ownRevokeForm.resetFields(); }}
        onOk={() => void submitOwnRevoke()}
        open={ownRevokeOpen}
        title={t('auth.revokeAllTitle')}
      >
        <Typography.Paragraph>{t('auth.revokeAllDescription')}</Typography.Paragraph>
        <Form form={ownRevokeForm} layout="vertical">
          <Form.Item
            label={t('auth.oldPassword')}
            name="currentPassword"
            rules={[{ required: true, message: t('auth.oldPasswordRequired') }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        confirmLoading={adminRevokeMutation.isPending}
        okButtonProps={{ danger: true }}
        okText={t('sessionSecurity.admin.confirm')}
        onCancel={() => setPendingAdminRevoke(undefined)}
        onOk={() => void confirmAdminRevoke()}
        open={pendingAdminRevoke !== undefined}
        title={t('sessionSecurity.admin.confirmTitle')}
      >
        <Alert message={t('sessionSecurity.admin.confirmWarning')} showIcon type="error" />
        <Descriptions column={1} size="small" style={{ marginTop: 16 }}>
          <Descriptions.Item label={t('sessionSecurity.admin.target')}>
            {pendingAdminRevoke?.target.displayName || pendingAdminRevoke?.target.username} ({pendingAdminRevoke?.target.username})
          </Descriptions.Item>
          <Descriptions.Item label={t('sessionSecurity.admin.reason')}>
            {pendingAdminRevoke?.reason}
          </Descriptions.Item>
        </Descriptions>
      </Modal>
    </section>
  );
}
