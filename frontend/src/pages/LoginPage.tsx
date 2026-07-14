import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Form, Input, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import type { LoginRequest } from '../api/auth';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthProvider';
import { LanguageSwitcher } from '../components/LanguageSwitcher';

interface LoginLocationState {
  passwordChanged?: boolean;
}

export function LoginPage(): JSX.Element {
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const state = location.state as LoginLocationState | null;
    if (state?.passwordChanged) {
      message.success(t('auth.passwordChanged'));
      navigate('/login', { replace: true, state: null });
    }
  }, [location.state, message, navigate, t]);

  const handleSubmit = async (values: LoginRequest) => {
    setSubmitting(true);
    try {
      await login(values);
      navigate('/', { replace: true });
    } catch (error) {
      const errorMessage =
        error instanceof Error && error.message === 'INVALID_AUTH_RESPONSE'
          ? t('errors.invalidAuthResponse')
          : getErrorMessage(error, t);
      message.error(errorMessage);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="login-page">
      <div className="login-language">
        <LanguageSwitcher />
      </div>

      <section className="login-panel" aria-labelledby="login-title">
        <div className="brand-mark" aria-hidden="true">2G</div>
        <Typography.Title id="login-title" level={2}>
          {t('auth.loginTitle')}
        </Typography.Title>
        <Typography.Paragraph type="secondary">
          {t('auth.loginSubtitle')}
        </Typography.Paragraph>

        <Form<LoginRequest>
          layout="vertical"
          onFinish={(values) => void handleSubmit(values)}
          requiredMark={false}
          size="large"
        >
          <Form.Item
            label={t('auth.username')}
            name="username"
            rules={[{ required: true, message: t('auth.usernameRequired') }]}
          >
            <Input autoComplete="username" prefix={<UserOutlined />} />
          </Form.Item>
          <Form.Item
            label={t('auth.password')}
            name="password"
            rules={[{ required: true, message: t('auth.passwordRequired') }]}
          >
            <Input.Password autoComplete="current-password" prefix={<LockOutlined />} />
          </Form.Item>
          <Button block htmlType="submit" loading={submitting} type="primary">
            {t('auth.submit')}
          </Button>
        </Form>
      </section>
    </main>
  );
}
