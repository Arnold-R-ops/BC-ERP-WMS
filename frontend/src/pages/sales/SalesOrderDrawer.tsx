import { CheckOutlined, CloseOutlined, DeleteOutlined, SendOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Drawer, Dropdown, Space, Steps, Table, type MenuProps, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { getErrorMessage } from '../../api/errors';
import { getSalesOrder, type SalesOrder, type SalesOrderItem } from '../../api/sales';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { formatDate, formatDateTime, formatMoney } from '../workflowUtils';
import { InvoicePage } from '../commercial/InvoicePage';
import { SalesShipmentSection } from './SalesShipmentSection';

export type SalesReasonAction = 'reject' | 'cancel' | 'close' | 'void';

interface SalesOrderDrawerProps {
  orderId?: number;
  onApprove: (order: SalesOrder) => void;
  onClose: () => void;
  onDeleteDraft: (order: SalesOrder) => void;
  onReasonAction: (action: SalesReasonAction, order: SalesOrder) => void;
  onSubmit: (order: SalesOrder) => void;
}

export function SalesOrderDrawer({
  orderId,
  onApprove,
  onClose,
  onDeleteDraft,
  onReasonAction,
  onSubmit,
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

  const actions = new Set(order?.allowedActions ?? []);
  const canSubmit = actions.has('SUBMIT')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:create');
  const canApprove = actions.has('APPROVE')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:approve');
  const canReject = actions.has('REJECT')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:reject');
  const canCancel = actions.has('CANCEL')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:cancel');
  const canCloseRemainder = actions.has('CLOSE')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:close');
  const canVoid = actions.has('VOID')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:void');
  const canDeleteDraft = actions.has('DELETE')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:edit');
  const canEditShipments = hasPermission(session?.currentRole, session?.permissionCodes, 'sales:edit')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:shipment:edit')
    && ['APPROVED', 'COMPLETED', 'CLOSED'].includes(order?.commercialStatus ?? '');
  const canUpdateLogistics = actions.has('UPDATE_LOGISTICS')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'sales:logistics:update');
  const moreItems: NonNullable<MenuProps['items']> = order
    ? [
      canReject
        ? { key: 'reject', icon: <CloseOutlined />, danger: true, label: t('sales.actions.reject') }
        : null,
      canCancel
        ? { key: 'cancel', icon: <StopOutlined />, label: t('sales.actions.cancel') }
        : null,
      canCloseRemainder
        ? { key: 'close', icon: <StopOutlined />, label: t('sales.actions.close') }
        : null,
      canVoid
        ? { key: 'void', icon: <DeleteOutlined />, danger: true, label: t('sales.actions.void') }
        : null,
      canDeleteDraft
        ? { key: 'deleteDraft', icon: <DeleteOutlined />, danger: true, label: t('sales.actions.deleteDraft') }
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
          {canSubmit && (
            <Button icon={<SendOutlined />} onClick={() => onSubmit(order)} type="primary">
              {t('sales.actions.submit')}
            </Button>
          )}
          {moreItems.length > 0 && (
            <Dropdown
              menu={{
                items: moreItems,
                onClick: ({ key }) => {
                  if (key === 'deleteDraft') onDeleteDraft(order);
                  else onReasonAction(key as SalesReasonAction, order);
                },
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
              { key: 'commercial', label: t('sales.fields.commercialStatus'), children: <OrderStatusTag description={order.commercialStatusDescription} domain="sales" status={order.commercialStatus} /> },
              { key: 'allocation', label: t('sales.fields.allocationStatus'), children: <OrderStatusTag description={order.allocationStatusDescription} domain="sales" status={order.allocationStatus} /> },
              { key: 'warehouse', label: t('sales.fields.warehouseStatus'), children: <OrderStatusTag description={order.warehouseStatusDescription} domain="sales" status={order.warehouseStatus} /> },
              ...(order.detailedLogisticsEnabled ? [{ key: 'logistics', label: t('sales.fields.logisticsStatus'), children: <OrderStatusTag description={order.logisticsStatusDescription} domain="sales" status={order.logisticsStatus} /> }] : []),
              { key: 'customer', label: t('sales.fields.customer'), children: order.customerName ?? '-' },
              { key: 'amount', label: t('sales.fields.totalAmount'), children: formatMoney(order.totalAmount, i18n.language) },
              { key: 'approvalMode', label: t('sales.fields.approvalMode'), children: order.approvalModeSnapshot ? t(`sales.approvalModes.${order.approvalModeSnapshot}`) : '-' },
              { key: 'nextRole', label: t('sales.fields.nextResponsibleRole'), children: t(`sales.responsibleRoles.${order.nextResponsibleRole ?? 'NONE'}`) },
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
            current={order.warehouseStatus === 'SHIPPED' ? 2 : order.commercialStatus === 'APPROVED' || order.commercialStatus === 'COMPLETED' || order.commercialStatus === 'CLOSED' ? 1 : 0}
            items={[
              { title: t('sales.timeline.created'), description: formatDateTime(order.createdAt, i18n.language) },
              { title: t('sales.timeline.reviewed'), description: order.reviewedAt ? `${order.reviewedByName ?? '-'} · ${formatDateTime(order.reviewedAt, i18n.language)}` : t('common.pending') },
              { title: t('sales.timeline.shipped'), description: order.warehouseStatus === 'SHIPPED' ? formatDateTime(order.updatedAt, i18n.language) : t('common.pending') },
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
          {order.id !== undefined && <InvoicePage salesOrderId={order.id} canIssue={['APPROVED','COMPLETED','CLOSED'].includes(order.commercialStatus ?? '') && order.orderPurpose !== 'EXCHANGE_REPLACEMENT'} />}
          {order.id !== undefined && <SalesShipmentSection canEdit={canEditShipments} canUpdateLogistics={canUpdateLogistics} detailedLogisticsEnabled={order.detailedLogisticsEnabled === true} salesOrderId={order.id} />}
        </>
      )}
    </Drawer>
  );
}
