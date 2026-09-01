import { EditOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Descriptions, Drawer, Space, Table, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Transfer, TransferAction, TransferActionPayloadMap } from '../../api/transfers';
import { ReasonModal } from '../../components/ReasonModal';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { formatDateTime } from '../workflowUtils';
import { getTransferAvailableActions } from './transferActions';

interface TransferDetailDrawerProps {
  canApprove: boolean;
  canClose: boolean;
  canCreate: boolean;
  currentUsername?: string;
  loading: boolean;
  onAction: <TAction extends TransferAction>(action: TAction, payload: TransferActionPayloadMap[TAction]) => Promise<void>;
  onClose: () => void;
  onEdit: (transfer: Transfer) => void;
  open: boolean;
  transfer?: Transfer;
}

type ReasonAction = 'reject' | 'cancel' | 'reallocate' | 'close-remainder' | 'close-shortage' | 'void';

export function TransferDetailDrawer({
  canApprove, canClose, canCreate, currentUsername, loading, onAction, onClose, onEdit, open, transfer,
}: TransferDetailDrawerProps): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const [reasonAction, setReasonAction] = useState<ReasonAction>();
  const actions = useMemo(() => transfer ? getTransferAvailableActions(transfer) : undefined, [transfer]);
  const selfReview = Boolean(transfer && currentUsername && transfer.requestedByName === currentUsername);

  const submitReason = async (reason: string): Promise<void> => {
    if (!transfer || !reasonAction) return;
    try {
      if (reasonAction === 'reallocate' || reasonAction === 'reject') {
        await onAction(reasonAction, { version: transfer.version, comment: reason });
      } else if (reasonAction === 'close-shortage') {
        await onAction(reasonAction, { version: transfer.version, reason, confirmed: true });
      } else {
        await onAction(reasonAction, { version: transfer.version, reason });
      }
      setReasonAction(undefined);
    } catch {
      // The page reports the API error and keeps the modal open for a stable-key retry.
    }
  };

  return (
    <>
      <Drawer onClose={onClose} open={open} title={transfer?.transferNumber} width="min(100%, 1120px)">
        {transfer && actions ? (
          <>
            <Descriptions bordered column={3} size="small" items={[
              { key: 'source', label: english ? 'Source warehouse' : '调出仓', children: `${transfer.sourceWarehouseCode} · ${transfer.sourceWarehouseName}` },
              { key: 'target', label: english ? 'Destination warehouse' : '调入仓', children: `${transfer.destinationWarehouseCode} · ${transfer.destinationWarehouseName}` },
              { key: 'date', label: english ? 'Estimated arrival' : '预计到达', children: formatDateTime(transfer.estimatedArrivalAt, i18n.language) },
              { key: 'business', label: english ? 'Business status' : '业务状态', children: <OrderStatusTag domain="transfers" status={transfer.businessStatus} /> },
              { key: 'dispatch', label: english ? 'Dispatch progress' : '调出进度', children: <OrderStatusTag domain="transfers" status={transfer.outboundStatus} /> },
              { key: 'receipt', label: english ? 'Receipt progress' : '调入进度', children: <OrderStatusTag domain="transfers" status={transfer.inboundStatus} /> },
              { key: 'reason', label: english ? 'Reason' : '调拨原因', children: transfer.reason, span: 3 },
            ]} />
            <Space style={{ margin: '16px 0' }} wrap>
              {canCreate && actions.edit ? <Button icon={<EditOutlined />} onClick={() => onEdit(transfer)}>{english ? 'Edit' : '编辑'}</Button> : null}
              {canCreate && actions.submit ? <Button type="primary" onClick={() => { void onAction('submit', { version: transfer.version, comment: english ? 'Submitted for processing' : '提交调拨单' }).catch(() => undefined); }}>{english ? 'Submit' : '提交'}</Button> : null}
              {canApprove && !selfReview && actions.approve ? <Button type="primary" onClick={() => { void onAction('approve', { version: transfer.version, comment: english ? 'Approved' : '审批通过' }).catch(() => undefined); }}>{english ? 'Approve' : '批准'}</Button> : null}
              {canApprove && !selfReview && actions.reject ? <Button danger onClick={() => setReasonAction('reject')}>{english ? 'Reject' : '驳回'}</Button> : null}
              {canCreate && actions.reallocate ? <Button onClick={() => setReasonAction('reallocate')}>{english ? 'Reallocate batches' : '重新分配批次'}</Button> : null}
              {canCreate && actions.cancel ? <Button danger onClick={() => setReasonAction('cancel')}>{english ? 'Cancel' : '取消'}</Button> : null}
              {canClose && actions.closeRemainder ? <Button onClick={() => setReasonAction('close-remainder')}>{english ? 'Close undispatched remainder' : '关闭未调出余量'}</Button> : null}
              {canClose && actions.closeShortage ? <Button danger onClick={() => setReasonAction('close-shortage')}>{english ? 'Close transit shortage' : '关闭在途短少'}</Button> : null}
              {canCreate && actions.void ? <Button danger onClick={() => setReasonAction('void')}>{english ? 'Void' : '作废'}</Button> : null}
            </Space>
            {canApprove && selfReview && transfer.businessStatus === 'PENDING_APPROVAL' ? <Alert message={english ? 'The requester cannot approve or reject their own transfer.' : '调拨申请人不能审批或驳回自己提交的调拨单。'} showIcon style={{ marginBottom: 12 }} type="info" /> : null}
            {actions.closeShortage ? <Alert message={english ? 'Closing a shortage removes only the remaining in-transit balance and records an audited loss event. It does not deduct the source warehouse again.' : '关闭短少只核销剩余在途量并记录损失事件，不会再次扣减调出仓库存。'} showIcon style={{ marginBottom: 12 }} type="warning" /> : null}
            <Typography.Title level={4}>{english ? 'Quantity and batch trace' : '数量与批次追溯'}</Typography.Title>
            <Table pagination={false} rowKey="id" scroll={{ x: 1450 }} dataSource={transfer.lines} columns={[
              { title: 'SKU', dataIndex: 'skuCode', fixed: 'left', width: 135 },
              { title: english ? 'Product' : '商品', dataIndex: 'productName', width: 170 },
              { title: english ? 'Requested' : '计划', dataIndex: 'requestedQuantity' },
              { title: english ? 'Allocated' : '已分配', dataIndex: 'allocatedQuantity' },
              { title: english ? 'Dispatched' : '已调出', dataIndex: 'dispatchedQuantity' },
              { title: english ? 'In transit' : '在途', dataIndex: 'inTransitQuantity' },
              { title: english ? 'Received' : '累计调入', dataIndex: 'receivedQuantity' },
              { title: english ? 'Damaged' : '破损', dataIndex: 'damagedQuantity' },
              { title: english ? 'Quarantined' : '隔离', dataIndex: 'quarantinedQuantity' },
              { title: english ? 'Closed remainder' : '关闭未调出', dataIndex: 'closedUnshippedQuantity' },
              { title: english ? 'Transit loss' : '在途损失', dataIndex: 'shortageClosedQuantity' },
              { title: english ? 'Allocated batches' : '分配批次', width: 300, render: (_, line) => line.allocations.length ? line.allocations.map((allocation) => <Tag key={allocation.id}>{allocation.batchCode} · {allocation.locationCode} · {allocation.remainingQuantity}</Tag>) : '-' },
            ]} />
            <Typography.Title level={4}>{english ? 'Dispatch records' : '多次调出记录'}</Typography.Title>
            <Table pagination={false} rowKey="id" dataSource={transfer.dispatches} columns={[
              { title: english ? 'Dispatch' : '调出记录号', dataIndex: 'dispatchNumber' },
              { title: english ? 'Quantity' : '调出数量', render: (_, dispatch) => dispatch.lines.reduce((sum, line) => sum + line.quantity, 0) },
              { title: english ? 'Handover reference' : '交接参考号', dataIndex: 'handoverReference', render: (value?: string) => value || '-' },
              { title: english ? 'Operator' : '操作人', dataIndex: 'dispatchedByName' },
              { title: english ? 'Dispatched at' : '调出时间', dataIndex: 'dispatchedAt', render: (value: string) => formatDateTime(value, i18n.language) },
            ]} />
            <Typography.Title level={4}>{english ? 'Receipt records' : '多次调入记录'}</Typography.Title>
            <Table pagination={false} rowKey="id" dataSource={transfer.receipts} columns={[
              { title: english ? 'Receipt' : '调入记录号', dataIndex: 'receiptNumber' },
              { title: english ? 'Quantity' : '调入数量', render: (_, receipt) => receipt.lines.reduce((sum, line) => sum + line.quantity, 0) },
              { title: english ? 'Condition / location' : '实物状态 / 库位', render: (_, receipt) => receipt.lines.map((line) => <Tag key={line.id}>{line.disposition} · {line.locationCode}</Tag>) },
              { title: english ? 'Operator' : '操作人', dataIndex: 'receivedByName' },
              { title: english ? 'Received at' : '调入时间', dataIndex: 'receivedAt', render: (value: string) => formatDateTime(value, i18n.language) },
            ]} />
            <Typography.Title level={4}>{english ? 'Audit timeline' : '审计时间线'}</Typography.Title>
            {transfer.events.map((event) => (
              <Card extra={formatDateTime(event.occurredAt, i18n.language)} key={event.id} size="small" style={{ marginBottom: 8 }} title={<Space><Tag>{event.eventType}</Tag><span>{event.actorName}</span></Space>}>
                {event.payloadJson ? <Typography.Text code>{event.payloadJson}</Typography.Text> : null}
              </Card>
            ))}
          </>
        ) : null}
      </Drawer>

      <ReasonModal
        danger={reasonAction === 'reject' || reasonAction === 'cancel' || reasonAction === 'close-shortage' || reasonAction === 'void'}
        loading={loading}
        onCancel={() => setReasonAction(undefined)}
        onConfirm={submitReason}
        open={reasonAction !== undefined}
        title={english ? `Confirm ${reasonAction ?? ''}` : ({
          reject: '确认驳回', cancel: '确认取消', reallocate: '重新分配批次', 'close-remainder': '关闭未调出余量',
          'close-shortage': '关闭在途短少', void: '确认作废',
        } as Record<ReasonAction, string>)[reasonAction as ReasonAction] ?? ''}
      />
    </>
  );
}
