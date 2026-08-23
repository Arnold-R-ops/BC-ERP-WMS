import { Alert, App as AntdApp, Button, Checkbox, Form, Input, QRCode, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { activatePlatformInvitation, confirmPlatformInvitationActivation, getPlatformInvitationStatus, PlatformInvitationError, type PlatformAuthResponse, type PlatformInvitationStatus } from './api';
import { usePlatformAuth } from './PlatformAuthProvider';

export function PlatformInvitationActivationPage(): JSX.Element {
  const location = useLocation();
  const navigate = useNavigate();
  const { session, logout, establishSession } = usePlatformAuth();
  const { message } = AntdApp.useApp();
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const token = new URLSearchParams(location.search).get('token')?.trim() ?? '';
  const [status, setStatus] = useState<PlatformInvitationStatus | null>(null);
  const [invalid, setInvalid] = useState(false);
  const [statusUnavailable, setStatusUnavailable] = useState(false);
  const [loading, setLoading] = useState(true);
  const [authStep, setAuthStep] = useState<PlatformAuthResponse | null>(null);
  const [recoverySaved, setRecoverySaved] = useState(false);

  useEffect(() => {
    if (!token) { setInvalid(true); setLoading(false); return; }
    getPlatformInvitationStatus(token)
      .then(setStatus)
      .catch((error: unknown) => {
        if (error instanceof PlatformInvitationError && error.reason === 'invalid') setInvalid(true);
        else setStatusUnavailable(true);
      })
      .finally(() => setLoading(false));
  }, [token]);

  const activate = async (values: { password: string; confirmPassword: string }) => {
    if (values.password !== values.confirmPassword) {
      message.error(english ? 'The passwords do not match.' : '两次输入的密码不一致。');
      return;
    }
    setLoading(true);
    try {
      const response = await activatePlatformInvitation(token, values.password);
      if (response.status !== 'MFA_ENROLLMENT_REQUIRED' || !response.challengeToken) throw new Error('INVALID_ACTIVATION_STATE');
      setAuthStep(response);
    } catch (error: unknown) {
      if (error instanceof PlatformInvitationError && error.reason === 'invalid') {
        setInvalid(true);
        message.error(english ? 'This invitation is invalid, expired, revoked, or already used.' : '该邀请无效、已过期、已撤销或已经使用。');
      } else if (error instanceof PlatformInvitationError && error.reason === 'password') {
        message.error(english ? 'The password does not meet the security requirements.' : '密码不符合安全要求，请按页面规则重新设置。');
      } else {
        message.error(english ? 'Activation is temporarily unavailable. Please retry; the link remains valid.' : '暂时无法完成激活，请稍后重试；本次失败不会使链接失效。');
      }
    } finally {
      setLoading(false);
    }
  };

  const confirmMfa = async (values: { code: string }) => {
    if (!authStep?.challengeToken) return;
    setLoading(true);
    try {
      setAuthStep(await confirmPlatformInvitationActivation(authStep.challengeToken, values.code));
    } catch {
      message.error(english ? 'The verification code is invalid or expired.' : '验证码无效或已过期，请重试。');
    } finally {
      setLoading(false);
    }
  };

  const finish = () => {
    if (!authStep || !recoverySaved) return;
    establishSession(authStep);
    navigate('/', { replace: true });
  };

  if (loading && !status && !authStep) return <main className="platform-login-page"><Spin size="large" /></main>;
  if (session) return <main className="platform-login-page"><section className="platform-login-panel platform-mfa-panel">
    <Typography.Title level={2}>{english ? 'Sign out first' : '请先退出当前账号'}</Typography.Title>
    <Alert showIcon type="warning" message={english ? 'Open invitation activation in a separate private window, or sign out of the current platform account first.' : '请在独立的无痕窗口打开激活链接，或先退出当前平台账号，避免覆盖当前会话。'} />
    <Button onClick={() => logout()} type="primary">{english ? 'Sign out current account' : '退出当前账号'}</Button>
  </section></main>;
  if (invalid || !status) return <main className="platform-login-page"><section className="platform-login-panel platform-mfa-panel">
    {statusUnavailable ? <>
      <Typography.Title level={2}>{english ? 'Unable to verify invitation' : '暂时无法验证邀请'}</Typography.Title>
      <Alert showIcon type="error" message={english ? 'The platform service is temporarily unavailable. The link is not treated as used; please retry.' : '平台服务暂时不可用；链接不会因此被视为已使用，请稍后重试。'} />
      <Button onClick={() => window.location.reload()}>{english ? 'Retry' : '重新验证'}</Button>
    </> : <>
    <Typography.Title level={2}>{english ? 'Invitation unavailable' : '邀请不可用'}</Typography.Title>
    <Alert showIcon type="error" message={english ? 'The link is invalid, expired, revoked, or already used. Ask the inviter to create a new invitation.' : '链接无效、已过期、已撤销或已经使用，请联系邀请人重新创建。'} />
    <Button onClick={() => navigate('/login', { replace: true })}>{english ? 'Go to sign in' : '返回登录'}</Button>
    </>}
  </section></main>;

  if (authStep?.status === 'AUTHENTICATED' && authStep.recoveryCodes?.length) return <main className="platform-login-page"><section className="platform-login-panel platform-mfa-panel">
    <Typography.Title level={2}>{english ? 'Save recovery codes' : '保存恢复代码'}</Typography.Title>
    <Alert showIcon type="warning" message={english ? 'These 10 codes are shown once; each code can be used only once.' : '这 10 个恢复码仅显示一次，每个只能使用一次。'} />
    <div className="platform-recovery-codes">{authStep.recoveryCodes.map((code) => <Typography.Text code copyable key={code}>{code}</Typography.Text>)}</div>
    <Checkbox checked={recoverySaved} onChange={(event) => setRecoverySaved(event.target.checked)}>{english ? 'I stored all recovery codes securely offline.' : '我已将全部恢复码离线安全保存。'}</Checkbox>
    <Button block disabled={!recoverySaved} onClick={finish} type="primary">{english ? 'Enter platform' : '进入平台'}</Button>
  </section></main>;

  if (authStep?.status === 'MFA_ENROLLMENT_REQUIRED') return <main className="platform-login-page"><section className="platform-login-panel platform-mfa-panel">
    <Typography.Title level={2}>{english ? 'Bind your authenticator' : '绑定你的认证器'}</Typography.Title>
    <Typography.Paragraph type="secondary">{english ? 'Scan this QR code only with your own authenticator app, then enter the 6-digit code.' : '请仅使用你自己的认证器应用扫描二维码，然后输入生成的 6 位验证码。'}</Typography.Paragraph>
    {authStep.otpauthUri ? <div className="platform-mfa-qr"><QRCode bordered={false} errorLevel="M" size={184} type="svg" value={authStep.otpauthUri} /></div> : null}
    {authStep.enrollmentSecret ? <Typography.Paragraph>{english ? 'Setup key: ' : '设置密钥：'}<Typography.Text code copyable>{authStep.enrollmentSecret}</Typography.Text></Typography.Paragraph> : null}
    <Form layout="vertical" onFinish={(values) => void confirmMfa(values)}>
      <Form.Item name="code" label={english ? '6-digit code' : '6 位验证码'} rules={[{ required: true }, { pattern: /^\d{6}$/ }]}><Input autoComplete="one-time-code" inputMode="numeric" maxLength={6} /></Form.Item>
      <Button block htmlType="submit" loading={loading} type="primary">{english ? 'Bind and continue' : '绑定并继续'}</Button>
    </Form>
  </section></main>;

  const strongPassword = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{12,64}$/;
  return <main className="platform-login-page"><section className="platform-login-panel platform-mfa-panel">
    <Typography.Title level={2}>{english ? 'Activate platform administrator' : '激活平台管理员账号'}</Typography.Title>
    <Space direction="vertical" size="small">
      <Typography.Text>{status.displayName} · {status.email}</Typography.Text>
      <Tag color={status.invitationType === 'SUPER_ADMIN' ? 'red' : 'blue'}>{status.roleCode}</Tag>
      <Typography.Text type="secondary">{english ? 'Expires: ' : '到期时间：'}{new Date(status.expiresAt).toLocaleString()}</Typography.Text>
    </Space>
    <Alert showIcon type="info" message={english ? 'Create an independent password. MFA binding is required immediately afterward.' : '请设置独立密码；下一步必须立即绑定 MFA。'} />
    <Form layout="vertical" onFinish={(values) => void activate(values)}>
      <Form.Item name="password" label={english ? 'New password' : '新密码'} rules={[{ required: true }, { pattern: strongPassword, message: english ? '12–64 characters with upper/lowercase, number, and symbol.' : '需 12–64 位，并包含大小写字母、数字和符号。' }]}><Input.Password autoComplete="new-password" /></Form.Item>
      <Form.Item name="confirmPassword" dependencies={['password']} label={english ? 'Confirm password' : '确认密码'} rules={[{ required: true }]}><Input.Password autoComplete="new-password" /></Form.Item>
      <Button block htmlType="submit" loading={loading} type="primary">{english ? 'Set password and bind MFA' : '设置密码并绑定 MFA'}</Button>
    </Form>
  </section></main>;
}
