import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getCustomerFact,
  listCustomerFacts,
  REPORTS_QUERY_KEY,
  type CustomerFact,
  type CustomerFactSource,
  type CustomerFactType,
  type CustomerProductFact,
} from '../../api/reports';
import { formatDate, formatDateTime, formatMoney } from '../workflowUtils';

export function CustomerAnalyticsPage(): JSX.Element {
  const { i18n, t } = useTranslation();
  const [keyword, setKeyword] = useState('');
  const [customerType, setCustomerType] = useState<CustomerFactType>();
  const [source, setSource] = useState<CustomerFactSource>();
  const [page, setPage] = useState(0);
  const [selectedCustomerId, setSelectedCustomerId] = useState<number>();

  const customerQuery = useQuery({
    placeholderData: (previous) => previous,
    queryFn: () => listCustomerFacts({
      keyword: keyword.trim() || undefined,
      customerType,
      source,
      sortBy: 'totalAmount',
      sortDirection: 'desc',
      page,
      size: 20,
    }),
    queryKey: [...REPORTS_QUERY_KEY, 'customers', keyword, customerType, source, page],
  });
  const detailQuery = useQuery({
    enabled: selectedCustomerId !== undefined,
    queryFn: () => getCustomerFact(selectedCustomerId as number, 8),
    queryKey: [...REPORTS_QUERY_KEY, 'customer', selectedCustomerId],
  });

  const customerColumns: TableColumnsType<CustomerFact> = [
    {
      dataIndex: 'customerName',
      fixed: 'left',
      title: t('analytics.customer.customer'),
      width: 230,
      render: (_, customer) => (
        <Space direction="vertical" size={0}>
          <strong>{customer.customerName ?? '-'}</strong>
          <span className="table-secondary">{customer.customerCode ?? '-'}</span>
        </Space>
      ),
    },
    {
      dataIndex: 'customerType',
      title: t('analytics.customer.type'),
      width: 120,
      render: (value: CustomerFactType | undefined) => <Tag color={value === 'CLIENT' ? 'blue' : 'cyan'}>{value ? t(`analytics.customer.types.${value}`) : '-'}</Tag>,
    },
    {
      dataIndex: 'source',
      title: t('analytics.customer.source'),
      width: 120,
      render: (value: CustomerFactSource | undefined) => value ? t(`analytics.customer.sources.${value}`) : '-',
    },
    { dataIndex: 'totalOrderCount', title: t('analytics.fields.effectiveOrders'), align: 'right', width: 130 },
    { dataIndex: 'totalAmount', title: t('analytics.customer.lifetimeSales'), align: 'right', width: 150, render: (value: number) => formatMoney(value, i18n.language) },
    { dataIndex: 'averageOrderValue', title: t('analytics.fields.averageOrderValue'), align: 'right', width: 150, render: (value: number) => formatMoney(value, i18n.language) },
    { dataIndex: 'lastOrderDate', title: t('analytics.customer.lastOrder'), width: 145, render: (value: string | undefined) => formatDate(value, i18n.language) },
    { dataIndex: 'averageIntervalDays', title: t('analytics.customer.repeatInterval'), align: 'right', width: 150, render: (value: number) => t('analytics.days', { count: value ?? 0 }) },
    {
      key: 'action',
      fixed: 'right',
      title: t('common.actions'),
      width: 82,
      render: (_, customer) => <Button aria-label={t('common.view')} icon={<EyeOutlined />} onClick={() => setSelectedCustomerId(customer.customerId)} type="text" />,
    },
  ];

  const productColumns: TableColumnsType<CustomerProductFact> = [
    {
      dataIndex: 'skuName',
      title: t('analytics.customer.productSku'),
      width: 220,
      render: (_, product) => (
        <Space direction="vertical" size={0}>
          <strong>{product.productName ?? product.skuName ?? '-'}</strong>
          <span className="table-secondary">{product.skuName ?? product.skuCode ?? '-'}</span>
        </Space>
      ),
    },
    { dataIndex: 'skuCode', title: t('analytics.customer.skuCode'), width: 140 },
    { dataIndex: 'totalOrderCount', title: t('analytics.fields.effectiveOrders'), align: 'right', width: 100 },
    { dataIndex: 'totalQuantity', title: t('analytics.customer.quantity'), align: 'right', width: 100 },
    { dataIndex: 'totalAmount', title: t('analytics.customer.salesContribution'), align: 'right', width: 140, render: (value: number) => formatMoney(value, i18n.language) },
    { dataIndex: 'averageIntervalDays', title: t('analytics.customer.repeatInterval'), align: 'right', width: 130, render: (value: number) => t('analytics.days', { count: value ?? 0 }) },
  ];

  const detail = detailQuery.data;

  return (
    <section className="analytics-page">
      <header className="analytics-page-header">
        <div>
          <Typography.Title level={2}>{t('analytics.customer.title')}</Typography.Title>
          <Typography.Text type="secondary">{t('analytics.customer.subtitle')}</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined spin={customerQuery.isFetching} />} onClick={() => void customerQuery.refetch()}>
          {t('analytics.refreshSnapshot')}
        </Button>
      </header>

      <Alert className="analytics-context-alert" description={t('analytics.customer.scopeHint')} message={t('analytics.customer.snapshotOnly')} showIcon type="info" />

      <div className="analytics-customer-toolbar">
        <Input
          allowClear
          onChange={(event) => { setKeyword(event.target.value); setPage(0); }}
          placeholder={t('analytics.customer.searchPlaceholder')}
          prefix={<SearchOutlined />}
          value={keyword}
        />
        <Select
          allowClear
          onChange={(value: CustomerFactType | undefined) => { setCustomerType(value); setPage(0); }}
          options={[
            { label: t('analytics.customer.types.CLIENT'), value: 'CLIENT' },
            { label: t('analytics.customer.types.CONSUMER'), value: 'CONSUMER' },
          ]}
          placeholder={t('analytics.customer.type')}
          value={customerType}
        />
        <Select
          allowClear
          onChange={(value: CustomerFactSource | undefined) => { setSource(value); setPage(0); }}
          options={[
            { label: t('analytics.customer.sources.MANUAL'), value: 'MANUAL' },
            { label: t('analytics.customer.sources.CHANNEL'), value: 'CHANNEL' },
          ]}
          placeholder={t('analytics.customer.source')}
          value={source}
        />
      </div>

      {customerQuery.isError && <Alert message={t('analytics.loadFailed')} showIcon type="error" />}

      <section className="analytics-table-panel analytics-customer-table">
        <div className="analytics-panel-heading">
          <div><Typography.Title level={4}>{t('analytics.customer.rankingTitle')}</Typography.Title><p>{t('analytics.customer.rankingHint')}</p></div>
          <Typography.Text type="secondary">{t('analytics.customer.totalCustomers', { count: customerQuery.data?.totalElements ?? 0 })}</Typography.Text>
        </div>
        <Table<CustomerFact>
          columns={customerColumns}
          dataSource={customerQuery.data?.content ?? []}
          loading={customerQuery.isLoading}
          locale={{ emptyText: <Empty description={t('analytics.customer.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
          onRow={(customer) => ({ className: 'clickable-table-row', onDoubleClick: () => setSelectedCustomerId(customer.customerId) })}
          pagination={{
            current: page + 1,
            onChange: (nextPage) => setPage(nextPage - 1),
            pageSize: 20,
            showSizeChanger: false,
            total: customerQuery.data?.totalElements ?? 0,
          }}
          rowKey="customerId"
          scroll={{ x: 1250 }}
          size="small"
        />
      </section>

      <Drawer
        loading={detailQuery.isLoading}
        onClose={() => setSelectedCustomerId(undefined)}
        open={selectedCustomerId !== undefined}
        title={detail?.customerName ?? t('analytics.customer.detailTitle')}
        width={760}
      >
        {detailQuery.isError ? <Alert message={t('analytics.loadFailed')} showIcon type="error" /> : detail && (
          <div className="analytics-customer-detail">
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label={t('analytics.customer.customerCode')}>{detail.customerCode ?? '-'}</Descriptions.Item>
              <Descriptions.Item label={t('analytics.customer.type')}>{detail.customerType ? t(`analytics.customer.types.${detail.customerType}`) : '-'}</Descriptions.Item>
              <Descriptions.Item label={t('analytics.customer.source')}>{detail.source ? t(`analytics.customer.sources.${detail.source}`) : '-'}</Descriptions.Item>
              <Descriptions.Item label={t('analytics.customer.lastOrder')}>{formatDate(detail.lastOrderDate, i18n.language)}</Descriptions.Item>
            </Descriptions>
            <div className="analytics-detail-metrics">
              <div><span>{t('analytics.fields.effectiveOrders')}</span><strong>{detail.totalOrderCount}</strong></div>
              <div><span>{t('analytics.customer.lifetimeSales')}</span><strong>{formatMoney(detail.totalAmount, i18n.language)}</strong></div>
              <div><span>{t('analytics.fields.averageOrderValue')}</span><strong>{formatMoney(detail.averageOrderValue, i18n.language)}</strong></div>
              <div><span>{t('analytics.customer.repeatInterval')}</span><strong>{t('analytics.days', { count: detail.averageIntervalDays })}</strong></div>
            </div>
            <div className="analytics-panel-heading analytics-detail-heading">
              <div><Typography.Title level={4}>{t('analytics.customer.topProducts')}</Typography.Title><p>{t('analytics.customer.topProductsHint')}</p></div>
              <Typography.Text type="secondary">{t('analytics.refreshedAt', { time: formatDateTime(detail.refreshedAt, i18n.language) })}</Typography.Text>
            </div>
            <Table<CustomerProductFact>
              columns={productColumns}
              dataSource={detail.topProducts ?? []}
              pagination={false}
              rowKey="productSkuId"
              scroll={{ x: 790 }}
              size="small"
            />
          </div>
        )}
      </Drawer>
    </section>
  );
}
