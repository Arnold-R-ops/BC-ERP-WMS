import { Tabs, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { PlatformAdminDirectory } from './PlatformAdminDirectory';
import { PlatformOrdinaryAdminInvitationPanel, PlatformSuperAdminInvitationPanel } from './PlatformSuperAdminInvitationPanel';
import { usePlatformAuth } from './PlatformAuthProvider';

export function PlatformAdminManagementPage(): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const { session } = usePlatformAuth();
  const superAdmin = Boolean(session?.roles.includes('PLATFORM_SUPER_ADMIN'));

  return <main className="platform-home-content platform-audit-page platform-admin-management-page">
    <div className="platform-section-heading">
      <div>
        <Typography.Title level={2}>{english ? 'Platform administrators' : '平台管理员管理'}</Typography.Title>
        <Typography.Text type="secondary">
          {english
            ? 'Review platform identities and security state. Super administrators can invite accounts and perform protected account operations from each detail.'
            : '查看平台管理员身份与安全状态；超级管理员可邀请账号，并在管理员详情内执行受保护的账号操作。'}
        </Typography.Text>
      </div>
    </div>
    <Tabs
      className="platform-admin-tabs"
      defaultActiveKey="directory"
      items={[
        {
          key: 'directory',
          label: english ? 'Administrator directory' : '管理员目录',
          children: <PlatformAdminDirectory />,
        },
        ...(superAdmin ? [{
          key: 'ordinary-invitations',
          label: english ? 'Ordinary administrator invitations' : '普通管理员邀请',
          children: <PlatformOrdinaryAdminInvitationPanel />,
        }] : []),
        ...(superAdmin ? [{
          key: 'super-invitations',
          label: english ? 'Super-administrator invitations' : '超级管理员邀请',
          children: <PlatformSuperAdminInvitationPanel />,
        }] : []),
      ]}
    />
  </main>;
}
