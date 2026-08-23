import { Alert, App as AntdApp, Button, Card, Checkbox, Form, Input, Select, Space, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { listPlatformManagedUsers, resetPlatformAdminMfa, startPlatformAdminMfaReset, type PlatformManagedUser } from './api';
import { usePlatformAuth } from './PlatformAuthProvider';

interface ResetFormValues {
  targetUserId: number;
  reason: string;
  password: string;
  code: string;
  acknowledged: boolean;
}

export function PlatformMfaManagementPage(): JSX.Element {
  const [form] = Form.useForm<ResetFormValues>();
  const [users, setUsers] = useState<PlatformManagedUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const { session } = usePlatformAuth();
  const { message } = AntdApp.useApp();
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';

  const loadUsers = async () => {
    setLoading(true);
    setError(false);
    try {
      setUsers(await listPlatformManagedUsers());
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void loadUsers(); }, []);

  const targets = useMemo(() => users.filter((user) =>
    user.enabled && user.mfaEnabled && user.email !== session?.email,
  ), [session?.email, users]);

  const submit = async (values: ResetFormValues) => {
    setLoading(true);
    setError(false);
    try {
      const challenge = await startPlatformAdminMfaReset(values.targetUserId);
      await resetPlatformAdminMfa(
        values.targetUserId,
        challenge.challengeToken,
        values.password,
        values.code,
        values.reason.trim(),
        crypto.randomUUID(),
      );
      message.success(english
        ? 'MFA was reset. The target administrator must enroll an authenticator at the next sign-in.'
        : 'MFA 已重置；目标管理员下次登录时必须重新绑定认证器。');
      form.resetFields();
      await loadUsers();
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  return <main className="platform-home-content platform-audit-page">
    <div className="platform-section-heading">
      <div>
        <Typography.Title level={2}>{english ? 'Administrator MFA management' : '平台管理员 MFA 管理'}</Typography.Title>
        <Typography.Text type="secondary">
          {english ? 'Only a super administrator can reset MFA for another platform administrator.' : '仅超级管理员可为其他平台管理员重置 MFA，不能重置自己的 MFA。'}
        </Typography.Text>
      </div>
    </div>
    <Alert
      message={english
        ? 'A successful reset invalidates the target authenticator, recovery codes, unfinished MFA challenges, and all platform sessions. Persisted business data and background export jobs are preserved.'
        : '重置成功后，目标账号的认证器、恢复码、未完成 MFA 挑战及全部平台会话立即失效；已持久化业务数据和后台导出任务不会删除。'}
      showIcon
      type="warning"
    />
    {error ? <Alert message={english ? 'The operation failed. Check the target and your verification details.' : '操作失败，请检查目标账号、当前密码和认证器验证码。'} showIcon type="error" /> : null}
    <Card title={english ? 'Controlled MFA reset' : '受控 MFA 重置'} loading={loading}>
      <Form form={form} layout="vertical" onFinish={(values) => void submit(values)}>
        <Form.Item name="targetUserId" label={english ? 'Target platform administrator' : '目标平台管理员'} rules={[{ required: true }]}>
          <Select
            placeholder={english ? 'Choose another administrator' : '选择其他平台管理员'}
            options={targets.map((user) => ({
              value: user.id,
              label: <Space>{user.displayName} · {user.email}{user.superAdmin ? <Tag color="red">PLATFORM_SUPER_ADMIN</Tag> : null}</Space>,
            }))}
          />
        </Form.Item>
        <Form.Item name="reason" label={english ? 'Reset reason' : '重置原因'} rules={[{ required: true }, { max: 500 }]}>
          <Input.TextArea maxLength={500} rows={3} showCount placeholder={english ? 'Enter the operational or security reason' : '填写操作或安全原因'} />
        </Form.Item>
        <Form.Item name="password" label={english ? 'Your current password' : '你的当前密码'} rules={[{ required: true }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item name="code" label={english ? 'Your 6-digit authenticator code' : '你的认证器 6 位验证码'} rules={[{ required: true }, { pattern: /^\d{6}$/ }]}>
          <Input autoComplete="one-time-code" inputMode="numeric" maxLength={6} />
        </Form.Item>
        <Form.Item name="acknowledged" valuePropName="checked" rules={[{
          validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error(english ? 'Confirm the impact before continuing.' : '请先确认重置影响。')),
        }]}>
          <Checkbox>
            {english
              ? 'I understand that the target will be signed out everywhere and must enroll MFA again.'
              : '我已确认：目标账号会在所有设备退出，并须在下次登录时重新绑定 MFA。'}
          </Checkbox>
        </Form.Item>
        <Button danger htmlType="submit" loading={loading} type="primary">
          {english ? 'Verify me and reset target MFA' : '验证本人并重置目标 MFA'}
        </Button>
      </Form>
      {!loading && targets.length === 0 ? <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>
        {english ? 'No other active administrator currently has an MFA binding that can be reset.' : '当前没有其他已启用且已绑定 MFA 的平台管理员可供重置。'}
      </Typography.Paragraph> : null}
    </Card>
  </main>;
}
