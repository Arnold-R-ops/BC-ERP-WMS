import { AuditOutlined, HomeOutlined, LockOutlined, LogoutOutlined, SafetyCertificateOutlined, ShopOutlined, TeamOutlined, UserOutlined, UserSwitchOutlined } from '@ant-design/icons';
import { Alert, App as AntdApp, Button, Card, Checkbox, ConfigProvider, Descriptions, Form, Input, Layout, Modal, QRCode, Space, Spin, Tag, Typography } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { useTranslation } from 'react-i18next';
import { PlatformAuthProvider, usePlatformAuth } from './PlatformAuthProvider';
import { confirmPlatformLogoutAll, confirmPlatformMfaEnrollment, getPlatformCurrentSession, getPlatformRecoveryCodeStatus, PlatformLoginError, regeneratePlatformRecoveryCodes, startPlatformLogoutAll, startPlatformRecoveryCodeRegeneration, verifyPlatformMfa, type PlatformAuthResponse, type PlatformCurrentSession, type PlatformLoginRequest } from './api';
import { PlatformAuditLogPage } from './PlatformAuditLogPage';
import { PlatformAccessGrantPage } from './PlatformAccessGrantPage';
import { PlatformMfaManagementPage } from './PlatformMfaManagementPage';
import { PlatformAdminManagementPage } from './PlatformAdminManagementPage';
import { PlatformInvitationActivationPage } from './PlatformInvitationActivationPage';
import { PlatformTenantManagementPage } from './PlatformTenantManagementPage';
import { PlatformTenantAccessProvider, usePlatformTenantAccess } from './PlatformTenantAccessProvider';
import { platformMfaFailureMessage, shouldRestartPlatformMfa } from './mfaFeedback';

const PLATFORM_IDLE_TIMEOUT_MS = 30 * 60 * 1000;

function PlatformProtectedRoute({ view }: { view: 'home' | 'tenants' | 'audit' | 'grants' | 'security' | 'admins' }): JSX.Element {
  const { session, logout } = usePlatformAuth();
  const { access, loading: tenantAccessLoading, error: tenantAccessError, hasTenantAccess } = usePlatformTenantAccess();
  const navigate = useNavigate();
  const deadlineRef = useRef(0);

  const endSession = useCallback((reason: 'idle' | 'expired') => {
    logout();
    navigate('/login', { replace: true, state: { reason } });
  }, [logout, navigate]);

  useEffect(() => {
    if (!session) return undefined;
    const resetIdleDeadline = () => {
      deadlineRef.current = Math.min(Date.now() + PLATFORM_IDLE_TIMEOUT_MS, session.expiresAt);
    };
    const activityEvents: (keyof WindowEventMap)[] = ['click', 'keydown', 'mousemove', 'scroll', 'touchstart'];
    resetIdleDeadline();
    const onActivity = () => resetIdleDeadline();
    activityEvents.forEach((eventName) => window.addEventListener(eventName, onActivity, { passive: true }));
    const timer = window.setInterval(() => {
      if (Date.now() >= deadlineRef.current) {
        endSession(Date.now() >= session.expiresAt ? 'expired' : 'idle');
      }
    }, 1_000);
    return () => {
      activityEvents.forEach((eventName) => window.removeEventListener(eventName, onActivity));
      window.clearInterval(timer);
    };
  }, [endSession, session]);

  if (!session) return <Navigate replace to="/login" />;
  if ((view === 'audit' || view === 'grants' || view === 'security' || view === 'admins') && !session.roles.includes('PLATFORM_SUPER_ADMIN')) return <Navigate replace to="/" />;
  if (view === 'tenants' && tenantAccessLoading) return <PlatformShell><Spin fullscreen /></PlatformShell>;
  if (view === 'tenants' && (tenantAccessError || !hasTenantAccess || !access)) return <Navigate replace to="/" />;
  return <PlatformShell>{view === 'audit'
    ? <PlatformAuditLogPage />
    : view === 'grants'
      ? <PlatformAccessGrantPage />
      : view === 'security'
        ? <PlatformMfaManagementPage />
        : view === 'admins'
          ? <PlatformAdminManagementPage />
          : view === 'tenants' && access
            ? <PlatformTenantManagementPage access={access} />
          : <PlatformHome />}</PlatformShell>;
}

function PlatformLoginPage(): JSX.Element {
  const [form] = Form.useForm<PlatformLoginRequest>();
  const [mfaForm] = Form.useForm<{ code?: string; recoveryCode?: string }>();
  const [submitting, setSubmitting] = useState(false);
  const [authStep, setAuthStep] = useState<PlatformAuthResponse | null>(null);
  const [useRecoveryCode, setUseRecoveryCode] = useState(false);
  const [recoverySaved, setRecoverySaved] = useState(false);
  const { login, establishSession, sessionNotice, clearSessionNotice } = usePlatformAuth();
  const { message } = AntdApp.useApp();
  const { i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const english = i18n.language === 'en-US';
  const state = location.state as { reason?: 'idle' | 'expired' | 'revoked' } | null;

  useEffect(() => {
    if (state?.reason === 'idle') message.warning(english ? 'Signed out after 30 minutes of inactivity.' : '连续 30 分钟无操作，已自动退出。');
    if (state?.reason === 'expired') message.warning(english ? 'Your platform session has expired.' : '平台会话已过期，请重新登录。');
    if (state?.reason === 'revoked') message.success(english ? 'All platform sessions were revoked. Sign in again to continue.' : '所有平台设备会话已撤销，请重新登录。');
    if (state?.reason) navigate('/login', { replace: true, state: null });
  }, [english, message, navigate, state?.reason]);

  useEffect(() => {
    if (sessionNotice !== 'unauthorized') return;
    message.error(english
      ? 'Your platform session was rejected and has been cleared. Please sign in again.'
      : '平台会话校验失败，已安全退出，请重新登录。');
    clearSessionNotice();
  }, [clearSessionNotice, english, message, sessionNotice]);

  const submit = async (values: PlatformLoginRequest) => {
    setSubmitting(true);
    try {
      const response = await login({ ...values, email: values.email.trim() });
      form.resetFields();
      setAuthStep(response);
    } catch (error) {
      const serviceUnavailable = error instanceof PlatformLoginError && error.reason === 'service';
      message.error(serviceUnavailable
        ? (english ? 'Unable to connect to the platform service. Please try again shortly.' : '暂时无法连接平台服务，请稍后重试。')
        : (english ? 'Unable to sign in. Check your email and password, then try again.' : '无法登录。请检查平台管理员邮箱和密码后重试。'));
    } finally {
      setSubmitting(false);
    }
  };

  const submitMfa = async (values: { code?: string; recoveryCode?: string }) => {
    if (!authStep?.challengeToken) return;
    setSubmitting(true);
    try {
      const response = authStep.status === 'MFA_ENROLLMENT_REQUIRED'
        ? await confirmPlatformMfaEnrollment(authStep.challengeToken, values.code?.trim() ?? '')
        : await verifyPlatformMfa(authStep.challengeToken, values.code?.trim(), values.recoveryCode?.trim());
      if (response.recoveryCodes?.length) {
        setAuthStep(response);
      } else {
        establishSession(response);
        navigate('/', { replace: true });
      }
    } catch (error) {
      mfaForm.resetFields();
      if (shouldRestartPlatformMfa(error)) {
        setAuthStep(null);
        setUseRecoveryCode(false);
      }
      message.error(platformMfaFailureMessage(error, english));
    } finally {
      setSubmitting(false);
    }
  };

  const finishEnrollment = () => {
    if (!authStep || !recoverySaved) return;
    establishSession(authStep);
    navigate('/', { replace: true });
  };

  if (authStep?.status === 'AUTHENTICATED' && authStep.recoveryCodes?.length) {
    return <main className="platform-login-page">
      <section className="platform-login-panel platform-mfa-panel" aria-labelledby="platform-recovery-title">
        <div className="platform-brand-mark" aria-hidden="true"><SafetyCertificateOutlined /></div>
        <Typography.Title id="platform-recovery-title" level={2}>{english ? 'Save recovery codes' : '保存恢复代码'}</Typography.Title>
        <Alert message={english ? 'These codes are shown once. Store them offline; each code works only once.' : '恢复代码只显示一次，请离线安全保存；每个代码只能使用一次。'} showIcon type="warning" />
        <div className="platform-recovery-codes">{authStep.recoveryCodes.map((code) => <code key={code}>{code}</code>)}</div>
        <Checkbox checked={recoverySaved} onChange={(event) => setRecoverySaved(event.target.checked)}>{english ? 'I have stored these recovery codes safely' : '我已安全保存这些恢复代码'}</Checkbox>
        <Button block disabled={!recoverySaved} onClick={finishEnrollment} type="primary">{english ? 'Continue to platform' : '进入平台'}</Button>
      </section>
    </main>;
  }

  if (authStep?.status === 'MFA_ENROLLMENT_REQUIRED' || authStep?.status === 'MFA_REQUIRED') {
    const enrollment = authStep.status === 'MFA_ENROLLMENT_REQUIRED';
    return <main className="platform-login-page">
      <section className="platform-login-panel platform-mfa-panel" aria-labelledby="platform-mfa-title">
        <div className="platform-brand-mark" aria-hidden="true"><SafetyCertificateOutlined /></div>
        <Typography.Title id="platform-mfa-title" level={2}>{enrollment ? (english ? 'Set up authenticator' : '绑定认证器') : (english ? 'MFA verification' : 'MFA 验证')}</Typography.Title>
        {enrollment ? <>
          <Typography.Paragraph type="secondary">{english ? 'Scan this QR code in your authenticator app, or add an account with the setup key below. Then enter its 6-digit code.' : '请使用认证器应用扫描二维码，或选择“输入设置密钥”添加账号，然后输入应用生成的 6 位验证码。'}</Typography.Paragraph>
          {authStep.otpauthUri ? <div className="platform-mfa-qr" aria-label={english ? 'Authenticator setup QR code' : '认证器设置二维码'}>
            <QRCode bordered={false} errorLevel="M" size={184} type="svg" value={authStep.otpauthUri} />
            <Typography.Text type="secondary">{english ? 'Scan only with your own authenticator app. This QR code expires when this setup step expires.' : '请仅使用自己的认证器应用扫描；此二维码会随本次设置步骤到期而失效。'}</Typography.Text>
          </div> : null}
          <div className="platform-mfa-secret"><span>{english ? 'Setup key' : '设置密钥'}</span><Typography.Text code copyable>{authStep.enrollmentSecret}</Typography.Text></div>
          <Typography.Text type="secondary">{english ? `Account: ${authStep.email}` : `账号：${authStep.email}`}</Typography.Text>
        </> : <Typography.Paragraph type="secondary">{english ? 'Enter the 6-digit code from your authenticator app.' : '请输入认证器应用当前显示的 6 位验证码。'}</Typography.Paragraph>}
        <Form form={mfaForm} layout="vertical" onFinish={(values) => void submitMfa(values)} requiredMark={false} size="large">
          {useRecoveryCode && !enrollment ? <Form.Item label={english ? 'Recovery code' : '恢复代码'} name="recoveryCode" preserve={false} rules={[{ required: true }]}><Input autoComplete="one-time-code" /></Form.Item>
            : <Form.Item label={english ? '6-digit verification code' : '6 位验证码'} name="code" preserve={false} rules={[{ required: true, pattern: /^\d{6}$/ }]}><Input autoComplete="one-time-code" inputMode="numeric" maxLength={6} /></Form.Item>}
          <Button block htmlType="submit" loading={submitting} type="primary">{enrollment ? (english ? 'Bind and continue' : '绑定并继续') : (english ? 'Verify' : '验证')}</Button>
        </Form>
        {!enrollment ? <Button onClick={() => { mfaForm.resetFields(); setUseRecoveryCode((current) => !current); }} type="link">{useRecoveryCode ? (english ? 'Use authenticator code' : '使用认证器验证码') : (english ? 'Use a recovery code' : '使用恢复代码')}</Button> : null}
        <Button onClick={() => { mfaForm.resetFields(); setAuthStep(null); setUseRecoveryCode(false); }} type="link">{english ? 'Back to sign in' : '返回登录'}</Button>
      </section>
    </main>;
  }

  return (
    <main className="platform-login-page">
      <div className="platform-login-language"><LanguageSwitcher /></div>
      <section className="platform-login-panel" aria-labelledby="platform-login-title">
        <div className="platform-brand-mark" aria-hidden="true"><SafetyCertificateOutlined /></div>
        <Typography.Title id="platform-login-title" level={2}>
          {english ? 'Platform administrator' : '平台管理员'}
        </Typography.Title>
        <Typography.Paragraph type="secondary">
          {english ? 'Use your platform administrator email to access the BCWMS platform.' : '使用平台管理员邮箱登录 BCWMS 平台。'}
        </Typography.Paragraph>
        <Form<PlatformLoginRequest> autoComplete="off" form={form} layout="vertical" onFinish={(values) => void submit(values)} requiredMark={false} size="large">
          <Form.Item label={english ? 'Platform administrator email' : '平台管理员邮箱'} name="email" rules={[{ required: true, message: english ? 'Enter your platform administrator email.' : '请输入平台管理员邮箱。' }]}>
            <Input autoComplete="off" inputMode="email" name="platform-administrator-email" prefix={<UserOutlined />} />
          </Form.Item>
          <Form.Item label={english ? 'Password' : '密码'} name="password" rules={[{ required: true, message: english ? 'Enter your password.' : '请输入密码。' }]}>
            <Input.Password autoComplete="off" name="platform-administrator-password" prefix={<LockOutlined />} />
          </Form.Item>
          <Button block htmlType="submit" loading={submitting} type="primary">{english ? 'Sign in' : '登录'}</Button>
        </Form>
      </section>
    </main>
  );
}

function PlatformHome(): JSX.Element {
  const { session, logout } = usePlatformAuth();
  const { access: effectiveAccess, loading: accessLoading, error: accessError, hasTenantAccess } = usePlatformTenantAccess();
  const { i18n } = useTranslation();
  const { message } = AntdApp.useApp();
  const navigate = useNavigate();
  const english = i18n.language === 'en-US';
  const [currentSession, setCurrentSession] = useState<PlatformCurrentSession | null>(null);
  const [logoutChallenge, setLogoutChallenge] = useState<string | null>(null);
  const [logoutCode, setLogoutCode] = useState('');
  const [logoutStarting, setLogoutStarting] = useState(false);
  const [logoutConfirming, setLogoutConfirming] = useState(false);
  const [recoveryRemaining, setRecoveryRemaining] = useState<number | null>(null);
  const [recoveryChallenge, setRecoveryChallenge] = useState<string | null>(null);
  const [recoveryPassword, setRecoveryPassword] = useState('');
  const [recoveryCode, setRecoveryCode] = useState('');
  const [recoveryStarting, setRecoveryStarting] = useState(false);
  const [recoveryConfirming, setRecoveryConfirming] = useState(false);
  const [generatedRecoveryCodes, setGeneratedRecoveryCodes] = useState<string[]>([]);
  const [recoveryCodesSaved, setRecoveryCodesSaved] = useState(false);
  const superAdmin = Boolean(session?.roles.includes('PLATFORM_SUPER_ADMIN'));

  useEffect(() => {
    if (!session) return undefined;
    let active = true;
    void getPlatformCurrentSession()
      .then((result) => { if (active) setCurrentSession(result); })
      .catch(() => { if (active) setCurrentSession(null); });
    void getPlatformRecoveryCodeStatus()
      .then((result) => { if (active) setRecoveryRemaining(result.remaining); })
      .catch(() => { if (active) setRecoveryRemaining(null); });
    return () => { active = false; };
  }, [session]);

  const beginLogoutAll = async () => {
    setLogoutStarting(true);
    try {
      const challenge = await startPlatformLogoutAll();
      setLogoutCode('');
      setLogoutChallenge(challenge.challengeToken);
    } catch {
      message.error(english ? 'Unable to start MFA verification.' : '无法发起 MFA 再次验证，请稍后重试。');
    } finally {
      setLogoutStarting(false);
    }
  };

  const revokeAllSessions = async () => {
    if (!logoutChallenge || !/^\d{6}$/.test(logoutCode)) return;
    setLogoutConfirming(true);
    try {
      await confirmPlatformLogoutAll(logoutChallenge, logoutCode);
      logout();
      navigate('/login', { replace: true, state: { reason: 'revoked' } });
    } catch {
      message.error(english ? 'The authenticator code is invalid or expired.' : '认证器验证码无效或已过期。');
    } finally {
      setLogoutConfirming(false);
    }
  };

  const beginRecoveryCodeRegeneration = async () => {
    setRecoveryStarting(true);
    try {
      const challenge = await startPlatformRecoveryCodeRegeneration();
      setRecoveryPassword('');
      setRecoveryCode('');
      setRecoveryChallenge(challenge.challengeToken);
    } catch {
      message.error(english ? 'Unable to start recovery-code verification.' : '无法发起恢复码再次验证，请稍后重试。');
    } finally {
      setRecoveryStarting(false);
    }
  };

  const regenerateRecoveryCodes = async () => {
    if (!recoveryChallenge || !recoveryPassword || !/^\d{6}$/.test(recoveryCode)) return;
    setRecoveryConfirming(true);
    try {
      const result = await regeneratePlatformRecoveryCodes(recoveryChallenge, recoveryPassword, recoveryCode);
      setRecoveryChallenge(null);
      setRecoveryPassword('');
      setRecoveryCode('');
      setRecoveryRemaining(result.remaining);
      setRecoveryCodesSaved(false);
      setGeneratedRecoveryCodes(result.recoveryCodes);
    } catch {
      message.error(english ? 'The password or authenticator code is invalid or expired.' : '当前密码或认证器验证码无效，或者验证已过期。');
    } finally {
      setRecoveryConfirming(false);
    }
  };

  if (!session) return <Spin fullscreen />;

  const hasRead = superAdmin || Boolean(effectiveAccess?.scopes.some((scope) => scope.read));
  const hasExport = superAdmin || Boolean(effectiveAccess?.scopes.some((scope) => scope.export));
  return (
      <main className="platform-home-content">
        <Typography.Title level={2}>{english ? 'Platform home' : '平台首页'}</Typography.Title>
        <Typography.Paragraph type="secondary">
          {english ? 'A tenant is a customer company using BCWMS. This platform account belongs to no tenant and never appears in tenant members, roles, seats, or tenant audit logs.' : '租户是指使用 BCWMS 服务的客户公司。此平台账号不属于任何租户，不会出现在租户成员、角色、席位或租户审计中。'}
        </Typography.Paragraph>
        <Card title={english ? 'Current session' : '当前会话'}>
          <Descriptions column={1} size="small">
            <Descriptions.Item label={english ? 'Platform administrator email' : '平台管理员邮箱'}>{session.email}</Descriptions.Item>
            <Descriptions.Item label={english ? 'Platform role' : '平台角色'}>{session.roles.map((role) => <Tag color="blue" key={role}>{role}</Tag>)}</Descriptions.Item>
            <Descriptions.Item label={english ? 'MFA' : 'MFA 状态'}>{currentSession?.mfaEnabled ? (english ? 'Enabled' : '已启用') : '-'}</Descriptions.Item>
            <Descriptions.Item label={english ? 'Session expires at' : '会话最晚到期时间'}>{currentSession ? new Date(currentSession.expiresAt).toLocaleString() : '-'}</Descriptions.Item>
          </Descriptions>
          <Button danger loading={logoutStarting} onClick={() => void beginLogoutAll()}>{english ? 'Sign out all devices' : '退出所有设备'}</Button>
        </Card>
        <Card className="platform-capability-card" title={english ? 'Recovery codes' : '恢复码'}>
          <Descriptions column={1} size="small">
            <Descriptions.Item label={english ? 'Remaining unused codes' : '剩余未使用数量'}>
              {recoveryRemaining ?? '-'}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Paragraph type="secondary">
            {english
              ? 'Existing recovery codes are never displayed here. Regeneration replaces every old code and shows the 10 new codes once.'
              : '页面不会显示现有恢复码。重新生成会立即作废全部旧码，并且 10 个新恢复码只显示一次。'}
          </Typography.Paragraph>
          <Button loading={recoveryStarting} onClick={() => void beginRecoveryCodeRegeneration()}>
            {english ? 'Regenerate recovery codes' : '重新生成恢复码'}
          </Button>
        </Card>
        <Card className="platform-capability-card" title={english ? 'Authorized capability' : '已授权能力'}>
          <Typography.Paragraph>
            {hasTenantAccess
              ? (english
                ? `Effective tenant capabilities: ${[hasRead ? 'read' : '', hasExport ? 'export' : ''].filter(Boolean).join(' and ')}. Direct tenant data changes and deletions are not permitted.`
                : `当前有效租户能力：${[hasRead ? '读取' : '', hasExport ? '导出' : ''].filter(Boolean).join('、')}；不允许直接修改或删除租户数据。`)
              : (english ? 'No effective tenant data access is currently delegated.' : '当前没有有效的租户数据访问授权。')}
          </Typography.Paragraph>
          <Typography.Paragraph type="secondary">
            {english ? 'Tenant browsing and export are available on the dedicated tenant management page.' : '租户浏览与导出已迁移至独立的“租户管理”页面。'}
          </Typography.Paragraph>
          {hasTenantAccess ? <Button icon={<ShopOutlined />} onClick={() => navigate('/tenants')} type="primary">
            {english ? 'Open tenant management' : '进入租户管理'}
          </Button> : null}
        </Card>
        {accessError ? <Alert message={english ? 'Unable to check the current delegated access. Tenant data entry remains hidden.' : '无法核验当前委派授权，租户数据入口已保持隐藏。'} showIcon type="error" /> : null}
        {!superAdmin && accessLoading ? <Spin /> : null}
        <Modal
          cancelText={english ? 'Cancel' : '取消'}
          okButtonProps={{ danger: true, disabled: !/^\d{6}$/.test(logoutCode) }}
          okText={english ? 'Verify and sign out all devices' : '验证并退出所有设备'}
          onCancel={() => { if (!logoutConfirming) { setLogoutChallenge(null); setLogoutCode(''); } }}
          onOk={() => void revokeAllSessions()}
          open={Boolean(logoutChallenge)}
          confirmLoading={logoutConfirming}
          title={english ? 'MFA verification required' : '需要再次 MFA 验证'}
        >
          <Typography.Paragraph>
            {english
              ? 'Enter the current 6-digit code from your authenticator. Success immediately invalidates every platform session, including this one.'
              : '请输入认证器当前生成的 6 位验证码。验证成功后，所有设备上的平台会话（包括当前会话）都会立即失效。'}
          </Typography.Paragraph>
          <Input
            aria-label={english ? '6-digit authenticator code' : '6 位认证器验证码'}
            autoComplete="one-time-code"
            inputMode="numeric"
            maxLength={6}
            onChange={(event) => setLogoutCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
            placeholder={english ? '6-digit code' : '6 位验证码'}
            value={logoutCode}
          />
        </Modal>
        <Modal
          cancelText={english ? 'Cancel' : '取消'}
          okButtonProps={{ disabled: !recoveryPassword || !/^\d{6}$/.test(recoveryCode) }}
          okText={english ? 'Verify and regenerate' : '验证并重新生成'}
          onCancel={() => {
            if (!recoveryConfirming) {
              setRecoveryChallenge(null);
              setRecoveryPassword('');
              setRecoveryCode('');
            }
          }}
          onOk={() => void regenerateRecoveryCodes()}
          open={Boolean(recoveryChallenge)}
          confirmLoading={recoveryConfirming}
          title={english ? 'Regenerate recovery codes' : '重新生成恢复码'}
        >
          <Alert
            message={english ? 'All existing recovery codes become invalid immediately after success.' : '验证成功后，全部现有恢复码会立即作废。'}
            showIcon
            type="warning"
          />
          <Space direction="vertical" size="middle" style={{ marginTop: 16, width: '100%' }}>
            <Input.Password
              aria-label={english ? 'Current password' : '当前密码'}
              autoComplete="current-password"
              onChange={(event) => setRecoveryPassword(event.target.value)}
              placeholder={english ? 'Current password' : '当前密码'}
              value={recoveryPassword}
            />
            <Input
              aria-label={english ? '6-digit authenticator code' : '6 位认证器验证码'}
              autoComplete="one-time-code"
              inputMode="numeric"
              maxLength={6}
              onChange={(event) => setRecoveryCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder={english ? '6-digit authenticator code' : '6 位认证器验证码'}
              value={recoveryCode}
            />
          </Space>
        </Modal>
        <Modal
          closable={false}
          footer={<Button disabled={!recoveryCodesSaved} onClick={() => {
            setGeneratedRecoveryCodes([]);
            setRecoveryCodesSaved(false);
          }} type="primary">{english ? 'I saved them securely' : '我已安全保存并关闭'}</Button>}
          keyboard={false}
          maskClosable={false}
          open={generatedRecoveryCodes.length > 0}
          title={english ? 'New recovery codes — shown once' : '新恢复码——仅显示一次'}
        >
          <Alert
            message={english ? 'Old recovery codes are no longer valid. Store these codes offline.' : '旧恢复码已经失效。请将以下恢复码离线安全保存。'}
            showIcon
            type="success"
          />
          <Space direction="vertical" size="small" style={{ marginTop: 16, width: '100%' }}>
            <Typography.Text copyable={{ text: generatedRecoveryCodes.join('\n') }} strong>
              {english ? 'Copy all recovery codes' : '复制全部恢复码'}
            </Typography.Text>
            {generatedRecoveryCodes.map((code) => <Typography.Text code copyable key={code}>{code}</Typography.Text>)}
            <Checkbox checked={recoveryCodesSaved} onChange={(event) => setRecoveryCodesSaved(event.target.checked)}>
              {english ? 'I have stored these recovery codes securely.' : '我已将这些恢复码安全保存。'}
            </Checkbox>
          </Space>
        </Modal>
      </main>
  );
}

function PlatformShell({ children }: { children: React.ReactNode }): JSX.Element {
  const { session, logout } = usePlatformAuth();
  const { hasTenantAccess } = usePlatformTenantAccess();
  const { i18n } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const english = i18n.language === 'en-US';
  const signOut = () => { logout(); navigate('/login', { replace: true }); };

  return <Layout className="platform-shell">
    <header className="platform-header">
      <strong>BCWMS Platform</strong>
      <Space>
        <Button icon={<HomeOutlined />} onClick={() => navigate('/')} type={location.pathname === '/' ? 'primary' : 'default'}>{english ? 'Home' : '平台首页'}</Button>
        {hasTenantAccess ? <Button icon={<ShopOutlined />} onClick={() => navigate('/tenants')} type={location.pathname === '/tenants' ? 'primary' : 'default'}>{english ? 'Tenant management' : '租户管理'}</Button> : null}
        {session?.roles.includes('PLATFORM_SUPER_ADMIN') ? <Button icon={<AuditOutlined />} onClick={() => navigate('/audit')} type={location.pathname === '/audit' ? 'primary' : 'default'}>{english ? 'Audit log' : '审计日志'}</Button> : null}
        {session?.roles.includes('PLATFORM_SUPER_ADMIN') ? <Button icon={<SafetyCertificateOutlined />} onClick={() => navigate('/grants')} type={location.pathname === '/grants' ? 'primary' : 'default'}>{english ? 'Delegated access' : '委派授权'}</Button> : null}
        {session?.roles.includes('PLATFORM_SUPER_ADMIN') ? <Button icon={<UserSwitchOutlined />} onClick={() => navigate('/security')} type={location.pathname === '/security' ? 'primary' : 'default'}>{english ? 'MFA management' : 'MFA 管理'}</Button> : null}
        {session?.roles.includes('PLATFORM_SUPER_ADMIN') ? <Button icon={<TeamOutlined />} onClick={() => navigate('/admins')} type={location.pathname === '/admins' ? 'primary' : 'default'}>{english ? 'Administrators' : '管理员管理'}</Button> : null}
        <Button icon={<LogoutOutlined />} onClick={signOut}>{english ? 'Sign out' : '退出登录'}</Button>
      </Space>
    </header>
    {children}
  </Layout>;
}

function PlatformRoutes(): JSX.Element {
  const { session } = usePlatformAuth();
  return <Routes>
    <Route path="/login" element={session ? <Navigate replace to="/" /> : <PlatformLoginPage />} />
    <Route path="/activate" element={<PlatformInvitationActivationPage />} />
    <Route path="/" element={<PlatformProtectedRoute view="home" />} />
    <Route path="/tenants" element={<PlatformProtectedRoute view="tenants" />} />
    <Route path="/audit" element={<PlatformProtectedRoute view="audit" />} />
    <Route path="/grants" element={<PlatformProtectedRoute view="grants" />} />
    <Route path="/security" element={<PlatformProtectedRoute view="security" />} />
    <Route path="/admins" element={<PlatformProtectedRoute view="admins" />} />
    <Route path="*" element={<Navigate replace to="/" />} />
  </Routes>;
}

export function PlatformApp(): JSX.Element {
  return <ConfigProvider theme={{ token: { colorPrimary: '#173c6b', borderRadius: 8 } }}>
    <AntdApp><HashRouter><PlatformAuthProvider><PlatformTenantAccessProvider><PlatformRoutes /></PlatformTenantAccessProvider></PlatformAuthProvider></HashRouter></AntdApp>
  </ConfigProvider>;
}
