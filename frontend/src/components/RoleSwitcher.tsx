import { SafetyCertificateOutlined, SwapOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Dropdown, Tag, type MenuProps, Tooltip } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthProvider';

export function RoleSwitcher(): JSX.Element | null {
  const { session, switchRole } = useAuth();
  const [loading, setLoading] = useState(false);
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();

  if (!session) {
    return null;
  }

  const roleLabel = (role: string) => t(`roles.${role}`, { defaultValue: role });
  const items: MenuProps['items'] = session.availableRoles.map((role) => ({
    key: role,
    label: roleLabel(role),
    icon: role === session.currentRole ? <SafetyCertificateOutlined /> : undefined,
    disabled: role === session.currentRole,
  }));

  const onClick: MenuProps['onClick'] = async ({ key }) => {
    setLoading(true);
    try {
      await switchRole(key);
      message.success(t('auth.switchRoleSuccess', { role: roleLabel(key) }));
    } catch (error) {
      message.error(getErrorMessage(error, t));
    } finally {
      setLoading(false);
    }
  };

  if (session.availableRoles.length <= 1) {
    return (
      <Tooltip title={t('auth.roleSwitchUnavailable')}>
        <Tag
          bordered={false}
          className="header-role-tag"
          icon={<SafetyCertificateOutlined />}
          color="cyan"
        >
          {roleLabel(session.currentRole)}
        </Tag>
      </Tooltip>
    );
  }

  return (
    <Dropdown menu={{ items, onClick }} trigger={['click']}>
      <Button className="header-action-button" icon={<SwapOutlined />} loading={loading} type="text">
        {roleLabel(session.currentRole)}
      </Button>
    </Dropdown>
  );
}
