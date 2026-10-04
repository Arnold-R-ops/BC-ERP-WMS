import { CheckOutlined, EditOutlined, ExportOutlined, PlusOutlined, SendOutlined, StopOutlined, UndoOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Divider, Drawer, Collapse, Space, Table, Tag, type TableColumnsType } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { MobileReceivingPage } from '../mobile/MobileReceivingPage';
import { getErrorMessage } from '../../api/errors';
import {
  getPurchaseOrder,
  getPurchaseOrderAllocations,
  listPurchaseAsns,
  type PurchaseAsn,
  type PurchaseOrder,
  type PurchaseOrderAllocation,
  type PurchaseOrderItem,
} from '../../api/purchasing';
import { listReceivingTasks, type ReceivingTask } from '../../api/receiving';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { formatDate, formatDateTime, formatMoney } from '../workflowUtils';

export type PurchaseWorkflowAction = 'submit' | 'approve' | 'reject' | 'withdraw' | 'cancel' | 'void';
export type PurchaseAsnManagementAction = 'edit' | 'confirm' | 'correct' | 'cancel' | 'closeRemainder';

interface PurchaseAsnCapabilities {
  canCancel: boolean;
  canCloseRemainder: boolean;
  canConfirm: boolean;
  canCreate: boolean;
  canUpdate: boolean;
}

export function getPurchaseAsnAvailableActions(
  asn: PurchaseAsn,
  capabilities: PurchaseAsnCapabilities,
): PurchaseAsnManagementAction[] {
  const actions: PurchaseAsnManagementAction[] = [];
  const hasReceipts = asn.items.some((item) => (item.receivedQuantity ?? 0) > 0);
  if (asn.status === 'DRAFT') {
    if (capabilities.canUpdate) actions.push('edit');
    if (capabilities.canConfirm) actions.push('confirm');
    if (capabilities.canCancel) actions.push('cancel');
  } else if (asn.status === 'CONFIRMED' && !hasReceipts) {
    if (capabilities.canCreate) actions.push('correct');
    if (capabilities.canCancel) actions.push('cancel');
  }
  if (capabilities.canCloseRemainder && asn.status === 'PARTIALLY_RECEIVED'
      && asn.items.some((item) => (item.remainingQuantity ?? 0) > 0)) {
    actions.push('closeRemainder');
  }
  return actions;
}

interface PurchaseOrderDrawerProps {
  canApprove: boolean;
  canCancel: boolean;
  canCancelAsn: boolean;
  canConfirmShipment: boolean;
  canConfirmAsn: boolean;
  canCloseRemainder: boolean;
  canCreateAsn: boolean;
  canEdit: boolean;
  canExecuteReceiving: boolean;
  canUpdateAsn: boolean;
  canSubmit: boolean;
  canViewAsn: boolean;
  canViewReceiving: boolean;
  canVoid: boolean;
  orderId?: number;
  onClose: () => void;
  onConfirmShipment: (order: PurchaseOrder) => void;
  onCloseRemainder: (asn: PurchaseAsn) => void;
  onCreateAsn: (order: PurchaseOrder) => void;
  onEdit: (order: PurchaseOrder) => void;
  onEditAsn: (order: PurchaseOrder, asn: PurchaseAsn) => void;
  onAsnAction: (order: PurchaseOrder, asn: PurchaseAsn, action: 'confirm' | 'correct' | 'cancel') => void;
  onWorkflow: (order: PurchaseOrder, action: PurchaseWorkflowAction) => void;
}

export function PurchaseOrderDrawer(props: PurchaseOrderDrawerProps): JSX.Element {
  const {
    canApprove, canCancel, canCancelAsn, canCloseRemainder, canConfirmAsn, canConfirmShipment,
    canCreateAsn, canEdit, canExecuteReceiving, canSubmit, canUpdateAsn, canViewAsn, canViewReceiving, canVoid,
    orderId, onAsnAction, onClose, onCloseRemainder, onConfirmShipment, onCreateAsn, onEdit,
    onEditAsn, onWorkflow,
  } = props;
  const { i18n, t } = useTranslation();
  const [executingTaskId, setExecutingTaskId] = useState<number>();
  const [selectedReceivingTask, setSelectedReceivingTask] = useState<ReceivingTask>();
  const orderQuery = useQuery({
    queryKey: ['purchase-order', orderId],
    queryFn: () => getPurchaseOrder(orderId as number),
    enabled: orderId !== undefined,
  });
  const allocationsQuery = useQuery({
    queryKey: ['purchase-order-allocations', orderId],
    queryFn: () => getPurchaseOrderAllocations(orderId as number),
    enabled: orderId !== undefined,
  });
  const asnsQuery = useQuery({
    queryKey: ['purchase-asns', orderId],
    queryFn: () => listPurchaseAsns(orderId as number),
    enabled: orderId !== undefined && canViewAsn,
  });
  const tasksQuery = useQuery({
    queryKey: ['receiving-tasks', 'purchase-order', orderId],
    queryFn: async () => (await listReceivingTasks()).filter((task) => task.purchaseOrderId === orderId),
    enabled: orderId !== undefined && canViewReceiving,
  });
  const order = orderQuery.data;

  const itemColumns: TableColumnsType<PurchaseOrderItem> = [
    { title: t('purchasing.fields.product'), dataIndex: 'productName', width: 220 },
    { title: t('products.fields.barcode'), dataIndex: 'productBarcode', width: 180 },
    { title: t('purchasing.fields.orderedQuantity'), dataIndex: 'orderedQuantity', align: 'right', width: 120 },
    { title: t('purchasing.fields.receivedQuantity'), dataIndex: 'receivedQuantity', align: 'right', width: 120 },
    { title: t('purchasing.fields.unitCost'), dataIndex: 'unitCost', align: 'right', width: 120, render: (value: number | undefined) => value === undefined ? '-' : formatMoney(value, i18n.language) },
  ];
  const allocationColumns: TableColumnsType<PurchaseOrderAllocation> = [
    { title: t('purchasing.fields.product'), dataIndex: 'skuCode' },
    { title: t('purchasing.fields.targetWarehouse'), dataIndex: 'warehouseCode' },
    { title: t('purchasing.fields.plannedQuantity'), dataIndex: 'plannedQuantity', align: 'right' },
    { title: t('purchasing.fields.shippedQuantity'), dataIndex: 'shippedQuantity', align: 'right' },
    { title: t('purchasing.fields.receivedQuantity'), dataIndex: 'receivedQuantity', align: 'right' },
  ];
  const asnColumns: TableColumnsType<PurchaseAsn> = [
    { title: t('purchasing.fields.asnNumber'), dataIndex: 'asnNumber' },
    { title: t('purchasing.fields.status'), dataIndex: 'status', render: (status) => <Tag>{String(status)}</Tag> },
    { title: t('purchasing.fields.targetWarehouse'), dataIndex: 'warehouseCode' },
    { title: t('purchasing.fields.carrier'), dataIndex: 'carrier' },
    { title: t('purchasing.fields.trackingNo'), dataIndex: 'trackingNo' },
    { title: t('purchasing.fields.expectedArrivalDate'), dataIndex: 'expectedArrivalDate', render: (value) => formatDate(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), key: 'actions',
      render: (_, asn) => {
        if (!order) return null;
        const actions = getPurchaseAsnAvailableActions(asn, {
          canCancel: canCancelAsn,
          canCloseRemainder,
          canConfirm: canConfirmAsn,
          canCreate: canCreateAsn,
          canUpdate: canUpdateAsn,
        });
        return (
          <Space size={0} wrap>
            {actions.includes('edit') && <Button onClick={() => onEditAsn(order, asn)} size="small" type="link">{t('purchasing.asn.actions.edit')}</Button>}
            {actions.includes('confirm') && <Button onClick={() => onAsnAction(order, asn, 'confirm')} size="small" type="link">{t('purchasing.asn.actions.confirm')}</Button>}
            {actions.includes('correct') && <Button onClick={() => onAsnAction(order, asn, 'correct')} size="small" type="link">{t('purchasing.asn.actions.correct')}</Button>}
            {actions.includes('cancel') && <Button danger onClick={() => onAsnAction(order, asn, 'cancel')} size="small" type="link">{t('purchasing.asn.actions.cancel')}</Button>}
            {actions.includes('closeRemainder') && <Button danger onClick={() => onCloseRemainder(asn)} size="small" type="link">{t('purchasing.actions.closeRemainder')}</Button>}
          </Space>
        );
      },
    },
  ];
  const taskColumns: TableColumnsType<ReceivingTask> = [
    { title: t('purchasing.fields.receivingTask'), dataIndex: 'taskNumber' },
    { title: t('purchasing.fields.targetWarehouse'), dataIndex: 'warehouseName', render: (_, task) => task.warehouseName ?? task.warehouseCode ?? '-' },
    { title: t('purchasing.fields.status'), dataIndex: 'status', render: (status) => <Tag>{String(status)}</Tag> },
    {
      title: t('common.actions'), key: 'actions', render: (_, task) => (
        <Space size={0} wrap>
          <Button onClick={() => setSelectedReceivingTask(task)} size="small" type="link">
            {t('purchasing.actions.viewReceivingTask')}
          </Button>
          {canExecuteReceiving && ['PENDING', 'PARTIALLY_RECEIVED'].includes(task.status)
            ? <Button onClick={() => setExecutingTaskId(task.id)} size="small" type="link">{t('purchasing.actions.enterReceivingTask')}</Button>
            : null}
        </Space>
      ),
    },
  ];

  const receivingTaskItemColumns: TableColumnsType<ReceivingTask['items'][number]> = [
    { title: t('purchasing.fields.product'), dataIndex: 'productName', render: (_, item) => item.productName ?? item.skuCode ?? '-' },
    { title: t('purchasing.fields.expectedQuantity'), dataIndex: 'expectedQuantity', align: 'right' },
    { title: t('purchasing.fields.receivedQuantity'), dataIndex: 'receivedQuantity', align: 'right' },
    { title: t('purchasing.fields.closedQuantity'), dataIndex: 'closedQuantity', align: 'right' },
    { title: t('purchasing.fields.remainingQuantity'), dataIndex: 'remainingQuantity', align: 'right' },
  ];

  const finalStatus = order?.status && ['COMPLETED', 'CLOSED', 'CANCELLED', 'VOIDED'].includes(order.status);

  return (
    <Drawer
      destroyOnHidden
      extra={order ? (
        <Space wrap>
          {canEdit && order.status === 'DRAFT' && <Button icon={<EditOutlined />} onClick={() => onEdit(order)}>{t('purchasing.actions.edit')}</Button>}
          {canSubmit && (order.status === 'DRAFT' || order.status === 'REJECTED') && <Button icon={<SendOutlined />} onClick={() => onWorkflow(order, 'submit')} type="primary">{t('purchasing.actions.submit')}</Button>}
          {canApprove && order.status === 'PENDING_APPROVAL' && <Button icon={<CheckOutlined />} onClick={() => onWorkflow(order, 'approve')} type="primary">{t('purchasing.actions.approve')}</Button>}
          {canApprove && order.status === 'PENDING_APPROVAL' && <Button danger onClick={() => onWorkflow(order, 'reject')}>{t('purchasing.actions.reject')}</Button>}
          {canSubmit && ['PENDING_APPROVAL', 'REJECTED', 'AWAITING_SHIPMENT'].includes(order.status ?? '') && <Button icon={<UndoOutlined />} onClick={() => onWorkflow(order, 'withdraw')}>{t('purchasing.actions.withdraw')}</Button>}
          {canConfirmShipment && ['AWAITING_SHIPMENT', 'PARTIALLY_SHIPPED'].includes(order.status ?? '') && <Button icon={<ExportOutlined />} onClick={() => onConfirmShipment(order)} type="primary">{t('purchasing.actions.confirmShipment')}</Button>}
          {canCancel && !finalStatus && order.status !== 'DRAFT' && <Button danger onClick={() => onWorkflow(order, 'cancel')}>{t('purchasing.actions.cancel')}</Button>}
          {canVoid && ['DRAFT', 'REJECTED'].includes(order.status ?? '') && <Button danger icon={<StopOutlined />} onClick={() => onWorkflow(order, 'void')}>{t('purchasing.actions.void')}</Button>}
        </Space>
      ) : null}
      loading={orderQuery.isLoading}
      onClose={onClose}
      open={orderId !== undefined}
      title={t('purchasing.details.title', { poNumber: order?.poNumber ?? '-' })}
      width="min(1180px, 96vw)"
    >
      <Drawer destroyOnHidden open={executingTaskId !== undefined} onClose={() => setExecutingTaskId(undefined)}
        width="min(1180px, 96vw)" title={t('purchasing.actions.enterReceivingTask')}>
        {executingTaskId !== undefined && <MobileReceivingPage key={executingTaskId} embeddedTaskId={executingTaskId}
          onReceiptSaved={async () => { await Promise.all([orderQuery.refetch(), allocationsQuery.refetch(), tasksQuery.refetch(), ...(canViewAsn ? [asnsQuery.refetch()] : [])]); }} />}
      </Drawer>
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
              { key: 'shipped', label: t('purchasing.fields.shippedQuantity'), children: allocationsQuery.data?.reduce((total, allocation) => total + (allocation.shippedQuantity ?? 0), 0) ?? '-' },
              { key: 'received', label: t('purchasing.fields.receivedQuantity'), children: (order.items ?? []).reduce((total, item) => total + (item.receivedQuantity ?? 0), 0) },
              { key: 'cost', label: t('purchasing.fields.totalCost'), children: order.totalCost === undefined ? '-' : formatMoney(order.totalCost, i18n.language) },
              { key: 'expected', label: t('purchasing.fields.expectedDate'), children: formatDate(order.expectedDate, i18n.language) },
              { key: 'entry', label: t('purchasing.fields.actualEntryDate'), children: formatDateTime(order.actualEntryDate, i18n.language) },
              { key: 'remark', label: t('common.remark'), children: order.remark ?? '-', span: 3 },
            ]}
            size="small"
          />
          <Divider orientation="left">{t('purchasing.details.lines')}</Divider>
          <Table columns={itemColumns} dataSource={order.items ?? []} pagination={false} rowKey={(item) => item.id ?? item.productSkuId ?? 0} scroll={{ x: 760 }} size="small" />
          <Collapse style={{ marginTop: 16 }} items={[{ key: 'allocations', label: t('purchasing.details.allocations'),
            children: <Table columns={allocationColumns} dataSource={allocationsQuery.data ?? []} pagination={false} rowKey={(item) => item.id ?? `${item.purchaseOrderItemId}-${item.warehouseId}`} size="small" />,
          }]} />
          {canViewAsn && <Collapse style={{ marginTop: 16 }} items={[{ key: 'shipments', label: t('purchasing.details.shipments'), children: <>
            <Divider />
            <div className="purchase-asn-section-heading">
              <strong>{t('purchasing.details.shipments')}</strong>
              {canCreateAsn && ['AWAITING_SHIPMENT', 'PARTIALLY_SHIPPED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED'].includes(order.status ?? '')
                ? <Button icon={<PlusOutlined />} onClick={() => onCreateAsn(order)} size="small">{t('purchasing.asn.actions.create')}</Button>
                : null}
            </div>
            <Table columns={asnColumns} dataSource={asnsQuery.data ?? []} pagination={false} rowKey="id" scroll={{ x: 1080 }} size="small" />
          </> }]} />}
          {canViewReceiving && <><Divider orientation="left">{t('purchasing.details.receivingTasks')}</Divider><Table columns={taskColumns} dataSource={tasksQuery.data ?? []} pagination={false} rowKey="id" size="small" /></>}
          <Drawer
            destroyOnHidden
            onClose={() => setSelectedReceivingTask(undefined)}
            open={selectedReceivingTask !== undefined}
            title={t('purchasing.details.receivingTaskTitle', { taskNumber: selectedReceivingTask?.taskNumber ?? '-' })}
            width="min(760px, 94vw)"
          >
            {selectedReceivingTask ? <>
              <Descriptions
                column={{ xs: 1, sm: 2 }}
                items={[
                  { key: 'status', label: t('purchasing.fields.status'), children: <Tag>{selectedReceivingTask.status}</Tag> },
                  { key: 'warehouse', label: t('purchasing.fields.targetWarehouse'), children: selectedReceivingTask.warehouseName ?? selectedReceivingTask.warehouseCode ?? '-' },
                ]}
                size="small"
              />
              <Divider orientation="left">{t('purchasing.details.receivingTaskItems')}</Divider>
              <Table columns={receivingTaskItemColumns} dataSource={selectedReceivingTask.items} pagination={false} rowKey="id" size="small" />
            </> : null}
          </Drawer>
        </>
      )}
    </Drawer>
  );
}
