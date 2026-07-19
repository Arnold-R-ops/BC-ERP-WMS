import { CheckOutlined, RollbackOutlined, TruckOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Drawer, Space, Table, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { getPurchaseOrder, type PurchaseOrder, type PurchaseOrderItem } from '../../api/purchasing';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { formatDate, formatDateTime, formatMoney } from '../workflowUtils';

interface PurchaseOrderDrawerProps {
  orderId?: number;
  onClose: () => void;
  onConfirmAsn: (order: PurchaseOrder) => void;
  onReceive: (order: PurchaseOrder) => void;
  onRollback: (order: PurchaseOrder) => void;
}

export function PurchaseOrderDrawer({ orderId, onClose, onConfirmAsn, onReceive, onRollback }: PurchaseOrderDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const orderQuery = useQuery({
    queryKey: ['purchase-order', orderId],
    queryFn: () => getPurchaseOrder(orderId as number),
    enabled: orderId !== undefined,
  });
  const order = orderQuery.data;

  const columns: TableColumnsType<PurchaseOrderItem> = [
    { title: t('purchasing.fields.product'), dataIndex: 'productName', width: 220 },
    { title: t('products.fields.barcode'), dataIndex: 'productBarcode', width: 180 },
    { title: t('purchasing.fields.orderedQuantity'), dataIndex: 'orderedQuantity', align: 'right', width: 120 },
    { title: t('purchasing.fields.receivedQuantity'), dataIndex: 'receivedQuantity', align: 'right', width: 120 },
    { title: t('purchasing.fields.unitCost'), dataIndex: 'unitCost', align: 'right', width: 120, render: (value: number | undefined) => value === undefined ? '-' : formatMoney(value, i18n.language) },
    { title: t('purchasing.fields.expiryDate'), dataIndex: 'expiryDate', width: 145, render: (value: string | undefined) => formatDate(value, i18n.language) },
    {
      title: t('purchasing.fields.batches'),
      dataIndex: 'batches',
      width: 260,
      render: (_, item) => (item.batches ?? []).map((batch) => <div key={batch.id ?? batch.batchCode}>{batch.batchCode} · {batch.locationCode ?? t('purchasing.fields.unassigned')}</div>),
    },
  ];

  return (
    <Drawer
      destroyOnHidden
      extra={order ? (
        <Space wrap>
          {order.status === 'ORDERING' && <Button icon={<CheckOutlined />} onClick={() => onConfirmAsn(order)} type="primary">{t('purchasing.actions.confirmAsn')}</Button>}
          {(order.status === 'IN_TRANSIT' || order.status === 'PARTIALLY_RECEIVED') && <Button icon={<TruckOutlined />} onClick={() => onReceive(order)} type="primary">{t('purchasing.actions.receive')}</Button>}
          {order.status === 'IN_TRANSIT' && <Button icon={<RollbackOutlined />} onClick={() => onRollback(order)}>{t('purchasing.actions.rollback')}</Button>}
        </Space>
      ) : null}
      loading={orderQuery.isLoading}
      onClose={onClose}
      open={orderId !== undefined}
      title={t('purchasing.details.title', { poNumber: order?.poNumber ?? '-' })}
      width="min(1120px, 94vw)"
    >
      {orderQuery.error && <Alert description={getErrorMessage(orderQuery.error, t)} message={t('purchasing.messages.loadFailed')} showIcon type="error" />}
      {order && (
        <>
          <Descriptions
            className="workflow-descriptions"
            column={{ xs: 1, sm: 2, lg: 3 }}
            items={[
              { key: 'status', label: t('purchasing.fields.status'), children: <OrderStatusTag domain="purchasing" status={order.status} /> },
              { key: 'supplier', label: t('purchasing.fields.supplier'), children: order.supplier ?? '-' },
              { key: 'quantity', label: t('purchasing.fields.totalQuantity'), children: order.totalQuantity ?? 0 },
              { key: 'cost', label: t('purchasing.fields.totalCost'), children: order.totalCost === undefined ? '-' : formatMoney(order.totalCost, i18n.language) },
              { key: 'expected', label: t('purchasing.fields.expectedDate'), children: formatDate(order.expectedDate, i18n.language) },
              { key: 'entry', label: t('purchasing.fields.actualEntryDate'), children: formatDateTime(order.actualEntryDate, i18n.language) },
              { key: 'operator', label: t('purchasing.fields.operator'), children: order.operatorName ?? '-' },
              { key: 'remark', label: t('common.remark'), children: order.remark ?? '-' },
              { key: 'audit', label: t('workflow.auditLog'), children: <pre className="audit-log">{order.auditLog ?? '-'}</pre>, span: 3 },
            ]}
            size="small"
          />
          <Table columns={columns} dataSource={order.items ?? []} pagination={false} rowKey={(item) => item.id ?? item.productSkuId ?? 0} scroll={{ x: 1165 }} size="small" />
        </>
      )}
    </Drawer>
  );
}
