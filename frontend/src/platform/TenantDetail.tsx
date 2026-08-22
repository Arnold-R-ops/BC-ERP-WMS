import { Tabs } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getPlatformTenant, type PlatformEffectiveAccess, type PlatformTenant } from './api';
import { TenantDatasetBrowser } from './TenantDatasetBrowser';
import { TenantOverview } from './TenantOverview';

interface TenantDetailProps {
  directoryTenant: PlatformTenant;
  access: PlatformEffectiveAccess;
}

export function TenantDetail({ directoryTenant, access }: TenantDetailProps): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const [activeTab, setActiveTab] = useState('overview');
  const [overview, setOverview] = useState<PlatformTenant | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [overviewError, setOverviewError] = useState(false);
  const [overviewAttempt, setOverviewAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setOverview(null);
    setOverviewLoading(true);
    setOverviewError(false);
    void getPlatformTenant(directoryTenant.id)
      .then((result) => { if (active) setOverview(result); })
      .catch(() => { if (active) setOverviewError(true); })
      .finally(() => { if (active) setOverviewLoading(false); });
    return () => { active = false; };
  }, [directoryTenant.id, overviewAttempt]);

  return <Tabs
    activeKey={activeTab}
    items={[
      {
        key: 'overview',
        label: english ? 'Overview' : '概览',
        children: <TenantOverview error={overviewError} loading={overviewLoading} onRetry={() => setOverviewAttempt((current) => current + 1)} tenant={overview} />,
      },
      {
        key: 'data',
        label: english ? 'Data browser' : '数据浏览',
        disabled: !overview,
        children: overview ? <TenantDatasetBrowser access={access} tenant={overview} /> : null,
      },
    ]}
    onChange={setActiveTab}
  />;
}
