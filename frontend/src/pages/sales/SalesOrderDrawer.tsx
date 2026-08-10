import { CheckOutlined, CloseOutlined, DeleteOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Drawer, Dropdown, Space, Steps, Table, type MenuProps, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { getErrorMessage } from '../../api/errors';
import { getSalesOrder, type SalesOrder, type SalesOrderItem } from '../../api/sales';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { formatDate, formatDateTime, formatMoney } from '../workflowUtils';
import { SalesShipmentSection } from './SalesShipmentSection';

export type SalesReasonAction = 'reject' | 'cancel' | 'void';

interface SalesOrderDrawerProps {
  orderId?: number;
  onApprove: (order: SalesOrder) => void;
  onClose: () => void;
  onReasonAction: (action: SalesReasonAction, order: SalesOrder) => void;
}

export function SalesOrderDrawer({
  orderId,
  onApprove,
  onClose,
  onReasonAction,
}: SalesOrderDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const { session } = useAuth();
  const orderQuery = useQuery({
    queryKey: ['sales-order', orderId],
    queryFn: () => getSalesOrder(orderId as number),
    enabled: orderId !== undefined,
  });
  const order = orderQuery.data;

  const columns: TableColumnsType<SalesOrderItem> = [
    { title: t('sales.fields.product'), dataIndex: 'productName', width: 220 },
    { title: t('products.fields.barcode'), dataIndex: 'productBarcode', width: 180 },
    { title: t('sales.fields.quantity'), dataIndex: 'quantity', align: 'right', width: 90 },
    { title: t('sales.fields.allocatedQty'), dataIndex: 'allocatedQty', align: 'right', width: 100 },
    { title: t('sales.fields.backorderQty'), dataIndex: 'backorderQty', align: 'right', width: 110 },
    {
      title: t('sales.fields.unitPrice'),
      dataIndex: 'unitPrice',
      align: 'right',
      width: 120,
      render: (value: number | undefined) => formatMoney(value, i18n.language),
    },
    {
      title: t('sales.fields.subtotal'),
      dataIndex: 'subtotal',
      align: 'right',
      width: 130,
      render: (value: number | undefined) => formatMoney(value, i18n.language),
    },
    { title: t('common.remark'), dataIndex: 'remark', width: 220 },
  ];

  const terminalStatuses = new Set(['SHIPPED', 'REJECTED', 'CANCELLED', 'VOIDED']);
  const canApprove = order?.status === 'PENDING_APPROVAL'
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:approve');
  const canReject = order?.status === 'PENDING_APPROVAL'
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:reject');
  const canCancel = Boolean(order?.status && !terminalStatuses.has(order.status))
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:cancel');
  const canVoid = order?.status !== 'VOIDED'
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:void');
  const canEditShipments = hasPermission(session?.currentRole, session?.permissionCodes, 'sales:edit')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:shipment:edit');
  const moreItems: NonNullable<MenuProps['items']> = order
    ? [
      canReject
        ? { key: 'reject', icon: <CloseOutlined />, danger: true, label: t('sales.actions.reject') }
        : null,
      canCancel
        ? { key: 'cancel', icon: <StopOutlined />, label: t('sales.actions.cancel') }
        : null,
      canVoid
        ? { key: 'void', icon: <DeleteOutlined />, danger: true, label: t('sales.actions.void') }
        : null,
    ].filter(Boolean) as NonNullable<MenuProps['items']>
    : [];

  return (
    <Drawer
      destroyOnHidden
      extra={order ? (
        <Space>
          {canApprove && (
            <Button icon={<CheckOutlined />} onClick={() => onApprove(order)} type="primary">
              {t('sales.actions.approve')}
            </Button>
          )}
          {moreItems.length > 0 && (
            <Dropdown
              menu={{
                items: moreItems,
                onClick: ({ key }) => onReasonAction(key as SalesReasonAction, order),
              }}
            >
              <Button>{t('common.more')}</Button>
            </Dropdown>
          )}
        </Space>
      ) : null}
      loading={orderQuery.isLoading}
      onClose={onClose}
      open={orderId !== undefined}
      title={t('sales.details.title', { orderNo: order?.orderNo ?? '-' })}
      width="min(1120px, 94vw)"
    >
      {orderQuery.error && (
        <Alert description={getErrorMessage(orderQuery.error, t)} message={t('sales.messages.loadFailed')} showIcon type="error" />
      )}
      {order && (
        <>
          <Descriptions
            className="workflow-descriptions"
            column={{ xs: 1, sm: 2, lg: 3 }}
            items={[
              { key: 'status', label: t('sales.fields.status'), children: <OrderStatusTag description={order.statusDescription} domain="sales" status={order.status} /> },
              { key: 'customer', label: t('sales.fields.customer'), children: order.customerName ?? '-' },
              { key: 'amount', label: t('sales.fields.totalAmount'), children: formatMoney(order.totalAmount, i18n.language) },
              { key: 'commercial', label: t('sales.fields.commercialStatus'), children: <OrderStatusTag description={order.commercialStatusDescription} domain="sales" status={order.commercialStatus} /> },
              { key: 'fulfillment', label: t('sales.fields.fulfillmentStatus'), children: <OrderStatusTag description={order.fulfillmentStatusDescription} domain="sales" status={order.fulfillmentStatus} /> },
              { key: 'policy', label: t('sales.fields.allocationPolicy'), children: order.allocationPolicy ? t(`sales.policies.${order.allocationPolicy}`, { defaultValue: order.allocationPolicy }) : '-' },
              { key: 'requested', label: t('sales.fields.requestedShipDate'), children: formatDate(order.requestedShipDate, i18n.language) },
              { key: 'promised', label: t('sales.fields.promisedShipDate'), children: formatDate(order.promisedShipDate, i18n.language) },
              { key: 'applicant', label: t('sales.fields.applicant'), children: order.applicantName ?? '-' },
              { key: 'consignee', label: t('sales.fields.consigneeName'), children: order.consigneeName ?? '-' },
              { key: 'shipCity', label: t('sales.fields.shipCity'), children: [order.shipCity, order.shipProvince, order.shipCountryCode].filter(Boolean).join(', ') || '-' },
            ]}
            size="small"
          />

          <Steps
            className="workflow-timeline"
            current={order.status === 'SHIPPED' ? 2 : order.reviewedAt ? 1 : 0}
            items={[
              { title: t('sales.timeline.created'), description: formatDateTime(order.createdAt, i18n.language) },
              { title: t('sales.timeline.reviewed'), description: order.reviewedAt ? `${order.reviewedByName ?? '-'} · ${formatDateTime(order.reviewedAt, i18n.language)}` : t('common.pending') },
              { title: t('sales.timeline.shipped'), description: order.status === 'SHIPPED' ? formatDateTime(order.updatedAt, i18n.language) : t('common.pending') },
            ]}
            responsive
            size="small"
          />

          <Table<SalesOrderItem>
            columns={columns}
            dataSource={order.items ?? []}
            pagination={false}
            rowKey={(item) => item.id ?? `${item.productSkuId}-${item.quantity}`}
            scroll={{ x: 1170 }}
            size="small"
          />
          {order.id !== undefined && <SalesShipmentSection canEdit={canEditShipments} salesOrderId={order.id} />}
        </>
      )}
    </Drawer>
  );
}
