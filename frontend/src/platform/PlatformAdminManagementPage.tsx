import { Alert, App as AntdApp, Button, Card, Checkbox, Form, Input, Modal, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  createPlatformAdminInvitation,
  listPlatformAdminInvitations,
  listPlatformManagedUsers,
  revokePlatformAdminInvitation,
  startPlatformAdminInvitation,
  type PlatformAdminInvitation,
  type PlatformManagedUser,
  PlatformRequestError,
} from './api';

interface InvitationFormValues {
  email: string;
  displayName: string;
  reason: string;
  password: string;
  code: string;
  acknowledged: boolean;
}

export function PlatformAdminManagementPage(): JSX.Element {
  const [form] = Form.useForm<InvitationFormValues>();
  const [users, setUsers] = useState<PlatformManagedUser[]>([]);
  const [invitations, setInvitations] = useState<PlatformAdminInvitation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [activationLink, setActivationLink] = useState('');
  const [linkSaved, setLinkSaved] = useState(false);
  const { message } = AntdApp.useApp();
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';

  const load = async () => {
    setLoading(true);
    setError(false);
    try {
      const [nextUsers, nextInvitations] = await Promise.all([
        listPlatformManagedUsers(),
        listPlatformAdminInvitations(),
      ]);
      setUsers(nextUsers);
      setInvitations(nextInvitations);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const submit = async (values: InvitationFormValues) => {
    setLoading(true);
    setError(false);
    try {
      const challenge = await startPlatformAdminInvitation();
      const invitation = await createPlatformAdminInvitation({
        challengeToken: challenge.challengeToken,
        password: values.password,
        code: values.code,
        email: values.email.trim().toLowerCase(),
        displayName: values.displayName.trim(),
        reason: values.reason.trim(),
      });
      if (!invitation.activationPath) throw new Error('MISSING_ACTIVATION_LINK');
      setActivationLink(`${window.location.origin}${invitation.activationPath}`);
      setLinkSaved(false);
      form.resetFields();
      await load();
    } catch (caught) {
      setError(true);
      const reauthenticationFailed = caught instanceof PlatformRequestError && caught.reason === 'reauthentication';
      message.error(reauthenticationFailed
        ? (english
          ? 'Your current password or the fresh authenticator code was not accepted. You remain signed in; use the code currently displayed in your authenticator and try again.'
          : '你的当前密码或最新认证器验证码未通过。当前登录不会退出；请使用认证器此刻显示的新验证码重试。')
        : (english
          ? 'Invitation failed. Check whether the email already exists or an active invitation is pending.'
          : '创建邀请失败，请检查邮箱是否已存在，或是否已有未过期的邀请。'));
    } finally {
      setLoading(false);
    }
  };

  const revoke = async (id: number) => {
    setLoading(true);
    try {
      await revokePlatformAdminInvitation(id);
      message.success(english ? 'Invitation revoked.' : '邀请已撤销。');
      await load();
    } catch {
      setError(true);
      setLoading(false);
    }
  };

  const userColumns: ColumnsType<PlatformManagedUser> = [
    { title: english ? 'Name' : '姓名', dataIndex: 'displayName' },
    { title: english ? 'Email' : '邮箱', dataIndex: 'email' },
    { title: english ? 'Role' : '角色', render: (_, user) => user.superAdmin ? <Tag color="red">PLATFORM_SUPER_ADMIN</Tag> : <Tag>PLATFORM_ADMIN</Tag> },
    { title: 'MFA', render: (_, user) => <Tag color={user.mfaEnabled ? 'green' : 'gold'}>{user.mfaEnabled ? (english ? 'Bound' : '已绑定') : (english ? 'Pending' : '待绑定')}</Tag> },
    { title: english ? 'Status' : '状态', render: (_, user) => <Tag color={user.enabled ? 'green' : 'default'}>{user.enabled ? (english ? 'Enabled' : '启用') : (english ? 'Disabled' : '停用')}</Tag> },
  ];
  const invitationColumns: ColumnsType<PlatformAdminInvitation> = [
    { title: english ? 'Invitee' : '受邀人', render: (_, item) => <>{item.displayName}<br /><Typography.Text type="secondary">{item.email}</Typography.Text></> },
    { title: english ? 'Role' : '角色', dataIndex: 'roleCode', render: (value: string) => <Tag color="red">{value}</Tag> },
    { title: english ? 'Expires' : '到期时间', dataIndex: 'expiresAt', render: (value: string) => new Date(value).toLocaleString() },
    { title: english ? 'Status' : '状态', dataIndex: 'status', render: (value: string) => <Tag color={value === 'ACTIVE' ? 'blue' : value === 'ACCEPTED' ? 'green' : 'default'}>{value}</Tag> },
    { title: english ? 'Action' : '操作', render: (_, item) => item.status === 'ACTIVE' ? <Button danger disabled={loading} onClick={() => void revoke(item.id)}>{english ? 'Revoke' : '撤销'}</Button> : '-' },
  ];

  return <main className="platform-home-content platform-audit-page">
    <div className="platform-section-heading">
      <div>
        <Typography.Title level={2}>{english ? 'Platform administrators' : '平台管理员管理'}</Typography.Title>
        <Typography.Text type="secondary">{english ? 'Create a second independent super-administrator through a controlled, audited invitation.' : '通过受控、可审计的邀请创建第二个独立超级管理员账号。'}</Typography.Text>
      </div>
    </div>
    <Alert showIcon type="warning" message={english
      ? 'The activation link is valid for 24 hours, is shown once, and must be sent through a secure channel. The invitee creates a password and binds their own MFA.'
      : '激活链接有效期为 24 小时、仅显示一次，必须通过安全渠道人工发送；受邀人自行设置密码并绑定自己的 MFA。'} />
    {error ? <Alert showIcon type="error" message={english ? 'Some administrator information or operation could not be completed.' : '管理员信息加载或操作未完成，请重试。'} /> : null}
    <Card loading={loading} title={english ? 'Current platform administrators' : '现有平台管理员'}>
      <Table columns={userColumns} dataSource={users} pagination={false} rowKey="id" size="small" />
    </Card>
    <Card title={english ? 'Invite another super-administrator' : '邀请第二名超级管理员'}>
      <Form form={form} layout="vertical" onFinish={(values) => void submit(values)}>
        <Space align="start" wrap style={{ width: '100%' }}>
          <Form.Item name="displayName" label={english ? 'Display name' : '姓名'} rules={[{ required: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item>
          <Form.Item name="email" label={english ? 'Independent email' : '独立邮箱'} rules={[{ required: true }, { type: 'email' }, { max: 254 }]}><Input autoComplete="off" /></Form.Item>
        </Space>
        <Form.Item name="reason" label={english ? 'Invitation reason' : '邀请原因'} rules={[{ required: true }, { max: 500 }]}><Input.TextArea maxLength={500} rows={2} showCount /></Form.Item>
        <Form.Item name="password" label={english ? 'Your current password' : '你的当前密码'} rules={[{ required: true }]}><Input.Password autoComplete="current-password" /></Form.Item>
        <Form.Item name="code" label={english ? 'Your 6-digit authenticator code' : '你的认证器 6 位验证码'} rules={[{ required: true }, { pattern: /^\d{6}$/ }]}><Input autoComplete="one-time-code" inputMode="numeric" maxLength={6} /></Form.Item>
        <Form.Item name="acknowledged" valuePropName="checked" rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error(english ? 'Confirm before continuing.' : '请先确认。')) }]}>
          <Checkbox>{english ? 'I confirmed that this belongs to another named person using an independent account and MFA device.' : '我已确认：该账号属于另一名明确人员，并使用独立账号及独立 MFA 设备。'}</Checkbox>
        </Form.Item>
        <Button htmlType="submit" loading={loading} type="primary">{english ? 'Verify me and create invitation' : '验证本人并创建邀请'}</Button>
      </Form>
    </Card>
    <Card loading={loading} title={english ? 'Invitation history' : '邀请记录'}>
      <Table columns={invitationColumns} dataSource={invitations} pagination={false} rowKey="id" size="small" />
    </Card>
    <Modal
      closable={false}
      footer={<Button disabled={!linkSaved} onClick={() => { setActivationLink(''); setLinkSaved(false); }} type="primary">{english ? 'I saved and sent it securely' : '我已安全保存并发送'}</Button>}
      keyboard={false}
      maskClosable={false}
      open={Boolean(activationLink)}
      title={english ? 'Activation link — shown once' : '激活链接——仅显示一次'}
    >
      <Alert showIcon type="warning" message={english ? 'Do not send this link in a public group or store it in ordinary notes.' : '请勿在公开群聊中发送，也不要保存到普通便签。'} />
      <Typography.Paragraph copyable={{ text: activationLink }} code style={{ marginTop: 16, overflowWrap: 'anywhere' }}>{activationLink}</Typography.Paragraph>
      <Checkbox checked={linkSaved} onChange={(event) => setLinkSaved(event.target.checked)}>{english ? 'I copied the link and will send it securely.' : '我已复制链接，并将通过安全渠道发送。'}</Checkbox>
    </Modal>
  </main>;
}
