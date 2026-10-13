import { MailOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError, apiRequest } from '../api/http';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { ledgerLoginDestination } from '../auth/ledgerEntry';

export function AccountFinderPage({ baseDomain }: { baseDomain: string }): JSX.Element {
  const { t } = useTranslation();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  async function findAccount(values: { email: string }) {
    setSubmitting(true);
    setError('');
    try {
      const result = await apiRequest<{ loginUrl: string }>('/public/v1/account-finder', {
        method: 'POST', body: { email: values.email.trim() }, omitAuth: true, suppressAuthEvents: true,
      });
      window.location.assign(ledgerLoginDestination(result.loginUrl, baseDomain));
    } catch (failure) {
      setError(t(failure instanceof ApiError && failure.status === 429
        ? 'ledgerEntry.rateLimited' : 'ledgerEntry.unavailable'));
    } finally {
      setSubmitting(false);
    }
  }
  return <main className="login-page">
    <div className="login-language"><LanguageSwitcher /></div>
    <section className="login-panel" aria-labelledby="finder-title">
      <img src="/fongvo-mark.svg" alt="" width={48} height={48} />
      <Typography.Title id="finder-title" level={2}>FONGVO Ledger</Typography.Title>
      <Typography.Paragraph type="secondary">{t('ledgerEntry.findAccount')}</Typography.Paragraph>
      {error && <Alert type="error" showIcon message={error} />}
      <Form layout="vertical" requiredMark={false} size="large" onFinish={(values: { email: string }) => void findAccount(values)}>
        <Form.Item label={t('ledgerEntry.email')} name="email" rules={[
          { required: true, message: t('ledgerEntry.emailRequired') },
          { type: 'email', message: t('ledgerEntry.emailInvalid') },
        ]}>
          <Input autoComplete="email" prefix={<MailOutlined />} maxLength={254} />
        </Form.Item>
        <Button block type="primary" htmlType="submit" loading={submitting}>{t('ledgerEntry.continue')}</Button>
      </Form>
    </section>
  </main>;
}
