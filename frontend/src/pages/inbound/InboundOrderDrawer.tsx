import { CheckOutlined, CloseOutlined, InboxOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Drawer, Space, Table, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { getInboundOrder, type InboundOrder, type InboundOrderItem } from '../../api/inbound';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { difference, formatDate, formatDateTime, formatMoney } from '../workflowUtils';

interface InboundOrderDrawerProps {
  orderId?: number;
  onApprove: (order: InboundOrder) => void;
  onClose: () => void;
  onConfirmOrder: (order: InboundOrder) => void;
  onReceive: (order: InboundOrder) => void;
  onReject: (order: InboundOrder) => void;
}

export function InboundOrderDrawer({ orderId, onApprove, onClose, onConfirmOrder, onReceive, onReject }: InboundOrderDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const orderQuery = useQuery({ queryKey: ['inbound-order', orderId], queryFn: () => getInboundOrder(orderId as number), enabled: orderId !== undefined });
  const order = orderQuery.data;

  const columns: TableColumnsType<InboundOrderItem> = [
    { title: t('inbound.fields.product'), dataIndex: 'productName', width: 220 },
    { title: t('products.fields.barcode'), dataIndex: 'productBarcode', width: 180 },
    { title: t('inbound.fields.planQty'), dataIndex: 'planQty', align: 'right', width: 95 },
    { title: t('inbound.fields.confirmedQty'), dataIndex: 'confirmedQty', align: 'right', width: 110 },
    { title: t('inbound.fields.actualQty'), dataIndex: 'actualQty', align: 'right', width: 95 },
    {
      title: t('inbound.fields.difference'),
      key: 'difference',
      align: 'right',
      width: 100,
      render: (_, item) => {
        if (order?.status !== 'COMPLETED') return '-';
        const value = difference(item.actualQty, item.planQty);
        return <span className={value === 0 ? '' : 'quantity-difference'}>{value > 0 ? `+${value}` : value}</span>;
      },
    },
    { title: t('inbound.fields.batchCode'), dataIndex: 'batchCode', width: 240 },
    { title: t('inbound.fields.targetLocation'), dataIndex: 'targetLocationCode', width: 250 },
    { title: t('inbound.fields.expiryDate'), dataIndex: 'expiryDate', width: 145, render: (value: string | undefined) => formatDate(value, i18n.language) },
    { title: t('inbound.fields.unitCost'), dataIndex: 'unitCost', align: 'right', width: 120, render: (value: number | undefined) => value === undefined ? '-' : formatMoney(value, i18n.language) },
  ];

  return (
    <Drawer
      destroyOnHidden
      extra={order ? (
        <Space wrap>
          {order.status === 'PENDING_APPROVAL' && <Button icon={<CheckOutlined />} onClick={() => onApprove(order)} type="primary">{t('inbound.actions.approve')}</Button>}
          {order.status === 'PENDING_APPROVAL' && <Button danger icon={<CloseOutlined />} onClick={() => onReject(order)}>{t('inbound.actions.reject')}</Button>}
          {order.status === 'APPROVED_PLAN' && <Button icon={<CheckOutlined />} onClick={() => onConfirmOrder(order)} type="primary">{t('inbound.actions.confirmOrder')}</Button>}
          {order.status === 'AWAITING_RECEIVAL' && <Button icon={<InboxOutlined />} onClick={() => onReceive(order)} type="primary">{t('inbound.actions.receive')}</Button>}
        </Space>
      ) : null}
      loading={orderQuery.isLoading}
      onClose={onClose}
      open={orderId !== undefined}
      title={t('inbound.details.title', { orderNo: order?.orderNo ?? '-' })}
      width="min(1180px, 95vw)"
    >
      {orderQuery.error && <Alert description={getErrorMessage(orderQuery.error, t)} message={t('inbound.messages.loadFailed')} showIcon type="error" />}
      {order && (
        <>
          <Descriptions
            className="workflow-descriptions"
            column={{ xs: 1, sm: 2, lg: 3 }}
            items={[
              { key: 'status', label: t('inbound.fields.status'), children: <OrderStatusTag description={order.statusDescription} domain="inbound" status={order.status} /> },
              { key: 'supplier', label: t('inbound.fields.supplier'), children: `${order.supplierName ?? '-'} (${order.supplierCode ?? `#${order.supplierId ?? '-'}`})` },
              { key: 'expected', label: t('inbound.fields.expectedDate'), children: formatDate(order.expectedDate, i18n.language) },
              { key: 'plan', label: t('inbound.fields.totalPlanQty'), children: order.totalPlanQty ?? 0 },
              { key: 'confirmed', label: t('inbound.fields.totalConfirmedQty'), children: order.totalConfirmedQty ?? 0 },
              { key: 'actual', label: t('inbound.fields.totalActualQty'), children: order.totalActualQty ?? 0 },
              { key: 'applicant', label: t('inbound.fields.applicant'), children: order.applicantName ?? '-' },
              { key: 'created', label: t('common.createdAt'), children: formatDateTime(order.createdAt, i18n.language) },
              { key: 'remark', label: t('common.remark'), children: order.remark ?? '-' },
              { key: 'audit', label: t('workflow.auditLog'), children: <pre className="audit-log">{order.auditLog ?? '-'}</pre>, span: 3 },
            ]}
            size="small"
          />
          <Table columns={columns} dataSource={order.items ?? []} pagination={false} rowKey={(item) => item.id ?? item.productSkuId ?? 0} scroll={{ x: 1555 }} size="small" />
        </>
      )}
    </Drawer>
  );
}
