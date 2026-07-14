import { LockOutlined, LogoutOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Form, Input, Modal, Space, Typography } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../api/errors';
import type { ChangeMyPasswordRequest } from '../api/auth';
import { useAuth } from '../auth/AuthProvider';

interface PasswordChangeModalProps {
  open: boolean;
  forced: boolean;
  onClose?: () => void;
}

interface PasswordFormValues extends ChangeMyPasswordRequest {
  confirmPassword: string;
}

export function PasswordChangeModal({
  open,
  forced,
  onClose,
}: PasswordChangeModalProps): JSX.Element {
  const [form] = Form.useForm<PasswordFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const { changePassword, logout } = useAuth();
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();
  const navigate = useNavigate();

  const handleSubmit = async (values: PasswordFormValues) => {
    setSubmitting(true);
    try {
      await changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      message.success(t('auth.changePasswordSuccess'));
      form.resetFields();
      navigate('/login', { replace: true, state: { passwordChanged: true } });
    } catch (error) {
      message.error(getErrorMessage(error, t));
    } finally {
      setSubmitting(false);
    }
  };

  const handleLogout = () => {
    logout();
    form.resetFields();
    navigate('/login', { replace: true });
  };

  return (
    <Modal
      centered
      closable={!forced}
      destroyOnHidden={!forced}
      footer={null}
      keyboard={!forced}
      maskClosable={false}
      onCancel={forced ? undefined : onClose}
      open={open}
      title={forced ? t('auth.forcedPasswordTitle') : t('auth.normalPasswordTitle')}
      width={480}
    >
      {forced && (
        <Typography.Paragraph type="secondary">
          {t('auth.forcedPasswordDescription')}
        </Typography.Paragraph>
      )}

      <Form<PasswordFormValues>
        form={form}
        layout="vertical"
        onFinish={(values) => void handleSubmit(values)}
        requiredMark={false}
      >
        <Form.Item
          label={t('auth.oldPassword')}
          name="oldPassword"
          rules={[{ required: true, message: t('auth.oldPasswordRequired') }]}
        >
          <Input.Password
            autoComplete="current-password"
            prefix={<LockOutlined />}
          />
        </Form.Item>

        <Form.Item
          extra={t('auth.passwordPolicy')}
          label={t('auth.newPassword')}
          name="newPassword"
          rules={[
            { required: true, message: t('auth.newPasswordRequired') },
            {
              pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/,
              message: t('auth.passwordPolicyInvalid'),
            },
          ]}
        >
          <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
        </Form.Item>

        <Form.Item
          dependencies={['newPassword']}
          label={t('auth.confirmPassword')}
          name="confirmPassword"
          rules={[
            { required: true, message: t('auth.passwordMismatch') },
            ({ getFieldValue }) => ({
              validator(_, value: string) {
                return !value || getFieldValue('newPassword') === value
                  ? Promise.resolve()
                  : Promise.reject(new Error(t('auth.passwordMismatch')));
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
        </Form.Item>

        <Space className="password-modal-actions">
          {forced && (
            <Button icon={<LogoutOutlined />} onClick={handleLogout}>
              {t('common.logout')}
            </Button>
          )}
          {!forced && <Button onClick={onClose}>{t('common.cancel')}</Button>}
          <Button htmlType="submit" loading={submitting} type="primary">
            {t('auth.changePasswordSubmit')}
          </Button>
        </Space>
      </Form>
    </Modal>
  );
}
