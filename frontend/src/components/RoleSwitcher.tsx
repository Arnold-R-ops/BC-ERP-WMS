import { SafetyCertificateOutlined, SwapOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Dropdown, Tag, type MenuProps, Tooltip } from 'antd';
import { type ReactNode, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthProvider';

interface RoleSwitcherProps {
  label?: ReactNode;
  iconOnly?: boolean;
  tooltip?: ReactNode;
}

export function RoleSwitcher({ iconOnly = false, label, tooltip }: RoleSwitcherProps = {}): JSX.Element | null {
  const { session, switchRole } = useAuth();
  const [loading, setLoading] = useState(false);
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();

  if (!session) {
    return null;
  }

  const roleLabel = (role: string) => t(`roles.${role}`, { defaultValue: role });
  const accessibleLabel = typeof tooltip === 'string'
    ? tooltip
    : typeof label === 'string'
      ? label
      : undefined;
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
    if (label) {
      return (
        <Tooltip title={t('auth.roleSwitchUnavailable')}>
          <Button aria-label={typeof label === 'string' ? label : undefined} className="header-action-button" icon={<SwapOutlined />} type="text">
            {label}
          </Button>
        </Tooltip>
      );
    }
    if (iconOnly) {
      return (
        <Tooltip title={tooltip ?? t('auth.roleSwitchUnavailable')}>
          <Button
            aria-label={accessibleLabel}
            className="header-action-button"
            icon={<SwapOutlined />}
            type="text"
          />
        </Tooltip>
      );
    }
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

  const trigger = (
    <Button
      aria-label={accessibleLabel}
      className="header-action-button"
      icon={<SwapOutlined />}
      loading={loading}
      type="text"
    >
      {iconOnly ? null : label ?? roleLabel(session.currentRole)}
    </Button>
  );

  return (
    <Dropdown menu={{ items, onClick }} trigger={['click']}>
      {tooltip ? <Tooltip title={tooltip}>{trigger}</Tooltip> : trigger}
    </Dropdown>
  );
}
