import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Empty,
  Input,
  Progress,
  Segmented,
  Skeleton,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getSalesOverview,
  listSalesDaily,
  REPORTS_QUERY_KEY,
  type SalesDailySummary,
} from '../../api/reports';
import { formatDate, formatDateTime, formatMoney } from '../workflowUtils';

function dateKey(daysAgo = 0): string {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() - daysAgo);
  return date.toISOString().slice(0, 10);
}

function SalesTrend({ data, label }: { data: SalesDailySummary[]; label: string }): JSX.Element {
  const chart = useMemo(() => {
    const width = 1000;
    const height = 180;
    const padding = 12;
    const max = Math.max(...data.map((item) => item.totalAmount ?? 0), 1);
    const points = data.map((item, index) => {
      const x = data.length === 1 ? width / 2 : padding + (index / (data.length - 1)) * (width - padding * 2);
      const y = height - padding - ((item.totalAmount ?? 0) / max) * (height - padding * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
    return { height, points, width };
  }, [data]);

  if (data.length === 0) {
    return <Empty description={label} image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  return (
    <div className="analytics-trend-chart">
      <svg aria-label={label} preserveAspectRatio="none" role="img" viewBox={`0 0 ${chart.width} ${chart.height}`}>
        <line x1="0" x2={chart.width} y1={chart.height - 12} y2={chart.height - 12} />
        <polyline fill="none" points={chart.points} stroke="currentColor" strokeWidth="4" vectorEffect="non-scaling-stroke" />
      </svg>
      <div className="analytics-trend-axis">
        <span>{data[0]?.summaryDate}</span>
        <span>{data[data.length - 1]?.summaryDate}</span>
      </div>
    </div>
  );
}

export function SalesAnalyticsPage(): JSX.Element {
  const { i18n, t } = useTranslation();
  const [preset, setPreset] = useState(30);
  const [startDate, setStartDate] = useState(dateKey(29));
  const [endDate, setEndDate] = useState(dateKey());

  const overviewQuery = useQuery({
    queryFn: () => getSalesOverview(startDate, endDate),
    queryKey: [...REPORTS_QUERY_KEY, 'sales-overview', startDate, endDate],
  });
  const dailyQuery = useQuery({
    queryFn: () => listSalesDaily(startDate, endDate),
    queryKey: [...REPORTS_QUERY_KEY, 'sales-daily', startDate, endDate],
  });

  const overview = overviewQuery.data;
  const statusRows = [
    { key: 'draft', label: t('analytics.status.draft'), value: overview?.draftCount ?? 0, color: '#64748b' },
    { key: 'pending', label: t('analytics.status.pendingApproval'), value: overview?.pendingApprovalCount ?? 0, color: '#b7791f' },
    { key: 'approved', label: t('analytics.status.awaitingShipment'), value: overview?.approvedAwaitingShipmentCount ?? 0, color: '#2563eb' },
    { key: 'shipped', label: t('analytics.status.shipped'), value: overview?.shippedCount ?? 0, color: '#15803d' },
    { key: 'rejected', label: t('analytics.status.rejected'), value: overview?.rejectedCount ?? 0, color: '#b42318' },
    { key: 'cancelled', label: t('analytics.status.cancelled'), value: overview?.cancelledCount ?? 0, color: '#7c3aed' },
    { key: 'voided', label: t('analytics.status.voided'), value: overview?.voidedCount ?? 0, color: '#475569' },
  ];
  const workflowTotal = statusRows.reduce((sum, item) => sum + item.value, 0);

  const applyPreset = (days: number): void => {
    setPreset(days);
    setStartDate(dateKey(days - 1));
    setEndDate(dateKey());
  };

  const columns: TableColumnsType<SalesDailySummary> = [
    { dataIndex: 'summaryDate', title: t('analytics.fields.date'), width: 140, render: (value: string) => formatDate(value, i18n.language) },
    { dataIndex: 'totalOrderCount', title: t('analytics.fields.effectiveOrders'), align: 'right', width: 130 },
    { dataIndex: 'totalAmount', title: t('analytics.fields.salesAmount'), align: 'right', width: 150, render: (value: number) => formatMoney(value, i18n.language) },
    { dataIndex: 'pendingApprovalCount', title: t('analytics.status.pendingApproval'), align: 'right', width: 130 },
    { dataIndex: 'approvedAwaitingShipmentCount', title: t('analytics.status.awaitingShipment'), align: 'right', width: 140 },
    { dataIndex: 'shippedCount', title: t('analytics.status.shipped'), align: 'right', width: 100 },
    { dataIndex: 'cancelledCount', title: t('analytics.status.cancelled'), align: 'right', width: 100 },
  ];

  const refresh = (): void => {
    void Promise.all([overviewQuery.refetch(), dailyQuery.refetch()]);
  };

  return (
    <section className="analytics-page">
      <header className="analytics-page-header">
        <div>
          <Typography.Title level={2}>{t('analytics.sales.title')}</Typography.Title>
          <Typography.Text type="secondary">{t('analytics.sales.subtitle')}</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined spin={overviewQuery.isFetching || dailyQuery.isFetching} />} onClick={refresh}>
          {t('analytics.refreshSnapshot')}
        </Button>
      </header>

      <div className="analytics-filter-band">
        <Segmented<number>
          onChange={applyPreset}
          options={[
            { label: t('analytics.rangeDays', { count: 30 }), value: 30 },
            { label: t('analytics.rangeDays', { count: 90 }), value: 90 },
            { label: t('analytics.rangeDays', { count: 180 }), value: 180 },
          ]}
          value={preset}
        />
        <div className="analytics-date-range">
          <label className="analytics-date-field">
            <span>{t('analytics.fields.startDate')}</span>
            <Input max={endDate} onChange={(event) => { setPreset(0); setStartDate(event.target.value); }} type="date" value={startDate} />
          </label>
          <label className="analytics-date-field">
            <span>{t('analytics.fields.endDate')}</span>
            <Input min={startDate} onChange={(event) => { setPreset(0); setEndDate(event.target.value); }} type="date" value={endDate} />
          </label>
        </div>
      </div>

      {(overviewQuery.isError || dailyQuery.isError) && (
        <Alert message={t('analytics.loadFailed')} showIcon type="error" />
      )}

      {overviewQuery.isLoading ? <Skeleton active /> : (
        <div className="analytics-metric-strip">
          <div><span>{t('analytics.fields.salesAmount')}</span><strong>{formatMoney(overview?.totalAmount, i18n.language)}</strong></div>
          <div><span>{t('analytics.fields.effectiveOrders')}</span><strong>{overview?.totalOrderCount ?? 0}</strong></div>
          <div><span>{t('analytics.fields.averageOrderValue')}</span><strong>{formatMoney(overview?.averageOrderValue, i18n.language)}</strong></div>
          <div><span>{t('analytics.status.shipped')}</span><strong>{overview?.shippedCount ?? 0}</strong></div>
        </div>
      )}

      <Alert className="analytics-context-alert" description={t('analytics.profitDeferred')} message={t('analytics.snapshotNotice', {
        time: formatDateTime(overview?.refreshedAt, i18n.language),
      })} showIcon type="info" />

      <div className="analytics-two-column">
        <section className="analytics-panel">
          <div className="analytics-panel-heading">
            <div><Typography.Title level={4}>{t('analytics.sales.trendTitle')}</Typography.Title><p>{t('analytics.sales.trendHint')}</p></div>
          </div>
          {dailyQuery.isLoading ? <Skeleton active /> : <SalesTrend data={dailyQuery.data ?? []} label={t('analytics.sales.emptyTrend')} />}
        </section>

        <section className="analytics-panel">
          <div className="analytics-panel-heading">
            <div><Typography.Title level={4}>{t('analytics.sales.workflowTitle')}</Typography.Title><p>{t('analytics.sales.workflowHint')}</p></div>
          </div>
          <div className="analytics-status-list">
            {statusRows.map((item) => (
              <div className="analytics-status-row" key={item.key}>
                <div><span>{item.label}</span><strong>{item.value}</strong></div>
                <Progress percent={workflowTotal === 0 ? 0 : Math.round((item.value / workflowTotal) * 100)} showInfo={false} strokeColor={item.color} />
              </div>
            ))}
          </div>
        </section>
      </div>

      <section className="analytics-table-panel">
        <div className="analytics-panel-heading">
          <div><Typography.Title level={4}>{t('analytics.sales.dailyTitle')}</Typography.Title><p>{t('analytics.sales.dailyHint')}</p></div>
        </div>
        <Table<SalesDailySummary>
          columns={columns}
          dataSource={dailyQuery.data ?? []}
          loading={dailyQuery.isLoading}
          pagination={{ pageSize: 14, showSizeChanger: false }}
          rowKey="summaryDate"
          scroll={{ x: 900 }}
          size="small"
        />
      </section>
    </section>
  );
}
