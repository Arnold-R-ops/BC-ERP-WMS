import {
  ApartmentOutlined,
  AuditOutlined,
  CheckOutlined,
  DatabaseOutlined,
  EyeOutlined,
  ReloadOutlined,
  RightOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Skeleton,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { canAccessModule } from '../access';
import {
  getUrgentReorderSuggestions,
  URGENT_REORDER_QUERY_KEY,
  type ReorderSuggestion,
} from '../api/dashboard';
import { getErrorMessage } from '../api/errors';
import {
  listPendingSkuMappings,
  listRawEvents,
  PENDING_SKU_QUERY_KEY,
  RAW_EVENTS_QUERY_KEY,
} from '../api/integrations';
import {
  approveSalesOrder,
  listSalesOrders,
  SALES_ORDERS_QUERY_KEY,
  type SalesApprovalPayload,
  type SalesOrder,
} from '../api/sales';
import { useAuth } from '../auth/AuthProvider';
import { OrderStatusTag } from '../components/OrderStatusTag';
import { SalesApprovalModal } from './sales/SalesApprovalModal';
import { getRecentOrders, getUrgentReorderCount } from './dashboardUtils';
import { formatDateTime, formatMoney } from './workflowUtils';

interface DashboardMetric {
  error: boolean;
  icon: ReactNode;
  key: string;
  loading: boolean;
  onClick: () => void;
  title: string;
  tone: 'sales' | 'mapping' | 'review' | 'reorder';
  value: number;
}

const urgencyColors: Record<string, string> = {
  CRITICAL: 'error',
  HIGH: 'warning',
  MEDIUM: 'processing',
  LOW: 'default',
};

function MetricCard({ metric, unavailableLabel }: { metric: DashboardMetric; unavailableLabel: string }): JSX.Element {
  return (
    <button
      aria-label={metric.title}
      className="dashboard-metric-button"
      onClick={metric.onClick}
      type="button"
    >
      <Card className={`dashboard-metric-card dashboard-metric-${metric.tone}`} size="small">
        <div className="dashboard-metric-heading">
          <span className="dashboard-metric-icon">{metric.icon}</span>
          <RightOutlined className="dashboard-metric-arrow" />
        </div>
        <span className="dashboard-metric-title">{metric.title}</span>
        {metric.loading ? (
          <Skeleton.Input active className="dashboard-metric-skeleton" size="small" />
        ) : (
          <strong className={metric.error ? 'dashboard-metric-value dashboard-metric-error' : 'dashboard-metric-value'}>
            {metric.error ? '—' : metric.value}
          </strong>
        )}
        {metric.error && <span className="dashboard-metric-error-label">{unavailableLabel}</span>}
      </Card>
    </button>
  );
}

export function DashboardPage(): JSX.Element {
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [approvalOrder, setApprovalOrder] = useState<SalesOrder>();

  const role = session?.currentRole ?? '';
  const canViewSales = Boolean(session && canAccessModule(role, 'sales'));
  const canViewIntegrations = Boolean(session && canAccessModule(role, 'integrations'));
  const canViewInventory = Boolean(session && canAccessModule(role, 'inventory'));
  const canApproveSales = role === 'SUPER_ADMIN' || role === 'GENERAL_MANAGER';

  const pendingSalesQuery = useQuery({
    enabled: canViewSales,
    queryFn: () => listSalesOrders({ status: 'PENDING_APPROVAL' }),
    queryKey: [...SALES_ORDERS_QUERY_KEY, 'dashboard', 'PENDING_APPROVAL'],
  });
  const pendingSkuQuery = useQuery({
    enabled: canViewIntegrations,
    queryFn: () => listPendingSkuMappings({ status: 'PENDING' }),
    queryKey: [...PENDING_SKU_QUERY_KEY, 'dashboard', 'PENDING'],
  });
  const manualReviewQuery = useQuery({
    enabled: canViewIntegrations,
    queryFn: () => listRawEvents({ channel: 'SHOPIFY', status: 'MANUAL_REVIEW', page: 0, size: 5 }),
    queryKey: [...RAW_EVENTS_QUERY_KEY, 'dashboard', 'MANUAL_REVIEW'],
  });
  const urgentReorderQuery = useQuery({
    enabled: canViewInventory,
    queryFn: () => getUrgentReorderSuggestions(30),
    queryKey: [...URGENT_REORDER_QUERY_KEY, 30],
  });

  const approveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: SalesApprovalPayload }) => approveSalesOrder(id, payload),
  });

  const recentOrders = useMemo(
    () => getRecentOrders(pendingSalesQuery.data ?? []),
    [pendingSalesQuery.data],
  );
  const urgentSuggestions = urgentReorderQuery.data?.suggestions ?? [];

  const isRefreshing = pendingSalesQuery.isFetching
    || pendingSkuQuery.isFetching
    || manualReviewQuery.isFetching
    || urgentReorderQuery.isFetching;
  const hasPartialError = pendingSalesQuery.isError
    || pendingSkuQuery.isError
    || manualReviewQuery.isError
    || urgentReorderQuery.isError;
  const lastUpdatedAt = Math.max(
    pendingSalesQuery.dataUpdatedAt,
    pendingSkuQuery.dataUpdatedAt,
    manualReviewQuery.dataUpdatedAt,
    urgentReorderQuery.dataUpdatedAt,
  );
  const lastUpdated = lastUpdatedAt > 0
    ? new Intl.DateTimeFormat(i18n.language, { timeStyle: 'short' }).format(new Date(lastUpdatedAt))
    : '—';

  const metrics: DashboardMetric[] = [];
  if (canViewSales) {
    metrics.push({
      error: pendingSalesQuery.isError,
      icon: <ShoppingCartOutlined />,
      key: 'sales',
      loading: pendingSalesQuery.isLoading,
      onClick: () => navigate('/sales?status=PENDING_APPROVAL'),
      title: t('dashboard.metrics.pendingSales'),
      tone: 'sales',
      value: pendingSalesQuery.data?.length ?? 0,
    });
  }
  if (canViewIntegrations) {
    metrics.push({
      error: pendingSkuQuery.isError,
      icon: <ApartmentOutlined />,
      key: 'mapping',
      loading: pendingSkuQuery.isLoading,
      onClick: () => navigate('/integrations/pending?status=PENDING'),
      title: t('dashboard.metrics.pendingSku'),
      tone: 'mapping',
      value: pendingSkuQuery.data?.length ?? 0,
    });
    metrics.push({
      error: manualReviewQuery.isError,
      icon: <AuditOutlined />,
      key: 'review',
      loading: manualReviewQuery.isLoading,
      onClick: () => navigate('/integrations/reviews?status=MANUAL_REVIEW'),
      title: t('dashboard.metrics.manualReview'),
      tone: 'review',
      value: manualReviewQuery.data?.totalElements ?? manualReviewQuery.data?.content?.length ?? 0,
    });
  }
  if (canViewInventory) {
    metrics.push({
      error: urgentReorderQuery.isError,
      icon: <DatabaseOutlined />,
      key: 'reorder',
      loading: urgentReorderQuery.isLoading,
      onClick: () => document.getElementById('dashboard-urgent-reorders')?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      title: t('dashboard.metrics.urgentReorder'),
      tone: 'reorder',
      value: getUrgentReorderCount(urgentReorderQuery.data),
    });
  }

  const confirmApproval = async (payload: SalesApprovalPayload): Promise<void> => {
    if (approvalOrder?.id === undefined) return;
    try {
      await approveMutation.mutateAsync({ id: approvalOrder.id, payload });
      message.success(t('sales.messages.approved'));
      setApprovalOrder(undefined);
      await queryClient.invalidateQueries({ queryKey: SALES_ORDERS_QUERY_KEY });
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const refreshDashboard = async (): Promise<void> => {
    const refreshes: Promise<unknown>[] = [];
    if (canViewSales) refreshes.push(pendingSalesQuery.refetch());
    if (canViewIntegrations) refreshes.push(pendingSkuQuery.refetch(), manualReviewQuery.refetch());
    if (canViewInventory) refreshes.push(urgentReorderQuery.refetch());
    await Promise.all(refreshes);
  };

  const orderColumns: TableColumnsType<SalesOrder> = [
    {
      dataIndex: 'orderNo',
      title: t('sales.fields.orderNo'),
      width: 170,
      render: (_, order) => (
        <Space direction="vertical" size={0}>
          <strong>{order.orderNo ?? '-'}</strong>
          <span className="table-secondary">{order.customerName ?? '-'}</span>
        </Space>
      ),
    },
    {
      dataIndex: 'totalAmount',
      title: t('sales.fields.totalAmount'),
      width: 125,
      align: 'right',
      render: (value: number | undefined) => formatMoney(value, i18n.language),
    },
    {
      dataIndex: 'status',
      title: t('sales.fields.status'),
      width: 125,
      render: (_, order) => <OrderStatusTag description={order.statusDescription} domain="sales" status={order.status} />,
    },
    {
      dataIndex: 'createdAt',
      title: t('common.createdAt'),
      width: 175,
      render: (value: string | undefined) => formatDateTime(value, i18n.language),
    },
    {
      key: 'actions',
      title: t('common.actions'),
      width: 118,
      fixed: 'right',
      render: (_, order) => (
        <Space size={2}>
          <Tooltip title={t('dashboard.actions.viewOrder')}>
            <Button
              aria-label={t('dashboard.actions.viewOrder')}
              disabled={order.id === undefined}
              icon={<EyeOutlined />}
              onClick={() => navigate(`/sales?status=PENDING_APPROVAL&orderId=${order.id}`)}
              size="small"
              type="text"
            />
          </Tooltip>
          {canApproveSales && (
            <Button
              disabled={order.id === undefined}
              icon={<CheckOutlined />}
              onClick={() => setApprovalOrder(order)}
              size="small"
              type="link"
            >
              {t('sales.actions.approve')}
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const reorderColumns: TableColumnsType<ReorderSuggestion> = [
    {
      dataIndex: 'productName',
      title: t('dashboard.reorder.product'),
      width: 220,
      render: (_, suggestion) => (
        <Space direction="vertical" size={0}>
          <strong>{suggestion.productName ?? '-'}</strong>
          <span className="table-secondary">{suggestion.barcode ?? '-'}</span>
        </Space>
      ),
    },
    {
      key: 'stock',
      title: t('dashboard.reorder.stock'),
      width: 140,
      align: 'right',
      render: (_, suggestion) => t('dashboard.reorder.stockValue', {
        current: suggestion.currentStock ?? 0,
        minimum: suggestion.minStock ?? 0,
      }),
    },
    {
      dataIndex: 'urgencyLevel',
      title: t('dashboard.reorder.urgency'),
      width: 105,
      render: (value: string | undefined) => (
        <Tag color={urgencyColors[value ?? 'LOW']}>
          {t(`dashboard.urgency.${value ?? 'LOW'}`)}
        </Tag>
      ),
    },
    {
      dataIndex: 'suggestedReorderQuantity',
      title: t('dashboard.reorder.suggestedQty'),
      width: 120,
      align: 'right',
      render: (value: number | undefined) => value ?? 0,
    },
    {
      dataIndex: 'estimatedDaysUntilStockout',
      title: t('dashboard.reorder.stockoutDays'),
      width: 120,
      align: 'right',
      render: (value: number | undefined) => value === undefined
        ? '-'
        : t('common.daysValue', { count: Number(value.toFixed(1)) }),
    },
    {
      key: 'actions',
      title: t('common.actions'),
      width: 70,
      fixed: 'right',
      render: (_, suggestion) => (
        <Tooltip title={t('dashboard.actions.viewInventory')}>
          <Button
            aria-label={t('dashboard.actions.viewInventory')}
            icon={<EyeOutlined />}
            onClick={() => navigate(`/inventory?search=${encodeURIComponent(suggestion.barcode ?? suggestion.productName ?? '')}`)}
            size="small"
            type="text"
          />
        </Tooltip>
      ),
    },
  ];

  if (!session) return <></>;

  return (
    <section className="dashboard-page">
      <div className="dashboard-toolbar">
        <div>
          <Typography.Title level={3}>{t('dashboard.title')}</Typography.Title>
          <Typography.Text type="secondary">
            {t('dashboard.lastUpdated', { time: lastUpdated })}
          </Typography.Text>
        </div>
        <Tooltip title={t('dashboard.refresh')}>
          <Button
            aria-label={t('dashboard.refresh')}
            icon={<ReloadOutlined spin={isRefreshing} />}
            onClick={() => void refreshDashboard()}
            type="text"
          />
        </Tooltip>
      </div>

      {hasPartialError && (
        <Alert
          className="dashboard-alert"
          description={t('dashboard.partialLoadFailure')}
          message={t('dashboard.partialLoadTitle')}
          showIcon
          type="warning"
        />
      )}

      <div className="dashboard-metric-grid">
        {metrics.map((metric) => (
          <MetricCard key={metric.key} metric={metric} unavailableLabel={t('dashboard.unavailable')} />
        ))}
      </div>

      <div className="dashboard-content-grid">
        {canViewSales && (
          <section className="dashboard-panel">
            <div className="dashboard-panel-heading">
              <Typography.Title level={5}>{t('dashboard.recentPendingOrders')}</Typography.Title>
              <Button onClick={() => navigate('/sales?status=PENDING_APPROVAL')} type="link">
                {t('dashboard.actions.viewAll')}
              </Button>
            </div>
            <Table<SalesOrder>
              columns={orderColumns}
              dataSource={recentOrders}
              loading={pendingSalesQuery.isLoading}
              locale={{ emptyText: <Empty description={t('dashboard.emptyPendingOrders')} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
              pagination={false}
              rowKey={(order) => String(order.id ?? order.orderNo)}
              scroll={{ x: 820 }}
              size="small"
            />
          </section>
        )}

        {canViewInventory && (
          <section className="dashboard-panel" id="dashboard-urgent-reorders">
            <div className="dashboard-panel-heading">
              <Typography.Title level={5}>{t('dashboard.urgentReorderList')}</Typography.Title>
              <Button onClick={() => navigate('/inventory')} type="link">
                {t('dashboard.actions.viewInventory')}
              </Button>
            </div>
            <Table<ReorderSuggestion>
              columns={reorderColumns}
              dataSource={urgentSuggestions}
              loading={urgentReorderQuery.isLoading}
              locale={{ emptyText: <Empty description={t('dashboard.emptyUrgentReorders')} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
              pagination={false}
              rowKey={(suggestion) => String(suggestion.productId ?? suggestion.barcode)}
              scroll={{ x: 780 }}
              size="small"
            />
          </section>
        )}
      </div>

      <SalesApprovalModal
        loading={approveMutation.isPending}
        onCancel={() => setApprovalOrder(undefined)}
        onConfirm={confirmApproval}
        open={approvalOrder !== undefined}
        order={approvalOrder}
      />
    </section>
  );
}
