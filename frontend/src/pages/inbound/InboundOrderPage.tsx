import { EyeOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Tabs } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  approveInboundOrder,
  confirmInboundOrder,
  createInboundOrder,
  INBOUND_ORDERS_QUERY_KEY,
  listInboundOrders,
  receiveInboundOrder,
  rejectInboundOrder,
  type InboundConfirmationPayload,
  type InboundOrder,
  type InboundOrderPayload,
  type InboundOrderStatus,
  type InboundReceiptPayload,
} from '../../api/inbound';
import { paginateArray } from '../../api/pagination';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { ReasonModal } from '../../components/ReasonModal';
import { formatDate, formatDateTime } from '../workflowUtils';
import { InboundApprovalModal } from './InboundApprovalModal';
import { InboundConfirmModal } from './InboundConfirmModal';
import { InboundOrderDrawer } from './InboundOrderDrawer';
import { InboundOrderFormDrawer } from './InboundOrderFormDrawer';
import { InboundReceiveModal } from './InboundReceiveModal';

interface InboundTableParams { current?: number; pageSize?: number; search?: string }

export function InboundOrderPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<InboundOrderStatus>('PENDING_APPROVAL');
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [formOpen, setFormOpen] = useState(false);
  const [approvalOrder, setApprovalOrder] = useState<InboundOrder>();
  const [rejectOrder, setRejectOrder] = useState<InboundOrder>();
  const [confirmationOrder, setConfirmationOrder] = useState<InboundOrder>();
  const [receivalOrder, setReceivalOrder] = useState<InboundOrder>();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();

  useEffect(() => { actionRef.current?.reloadAndRest?.(); }, [status]);
  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: INBOUND_ORDERS_QUERY_KEY });
    actionRef.current?.reload();
    if (selectedOrderId !== undefined) await queryClient.invalidateQueries({ queryKey: ['inbound-order', selectedOrderId] });
  };

  const createMutation = useMutation({ mutationFn: createInboundOrder });
  const approveMutation = useMutation({ mutationFn: ({ id, comment }: { id: number; comment?: string }) => approveInboundOrder(id, comment) });
  const rejectMutation = useMutation({ mutationFn: ({ id, reason }: { id: number; reason: string }) => rejectInboundOrder(id, reason) });
  const confirmMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: InboundConfirmationPayload }) => confirmInboundOrder(id, payload) });
  const receiveMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: InboundReceiptPayload }) => receiveInboundOrder(id, payload) });

  const saveOrder = async (payload: InboundOrderPayload): Promise<boolean> => {
    try { await createMutation.mutateAsync(payload); message.success(t('inbound.messages.created')); setFormOpen(false); setStatus('PENDING_APPROVAL'); await refresh(); return true; }
    catch (error) { message.error(getErrorMessage(error, t)); return false; }
  };
  const submitApproval = async (comment?: string): Promise<void> => {
    if (!approvalOrder?.id) return;
    try { await approveMutation.mutateAsync({ id: approvalOrder.id, comment }); message.success(t('inbound.messages.approved')); setApprovalOrder(undefined); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };
  const submitReject = async (reason: string): Promise<void> => {
    if (!rejectOrder?.id) return;
    try { await rejectMutation.mutateAsync({ id: rejectOrder.id, reason }); message.success(t('inbound.messages.rejected')); setRejectOrder(undefined); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };
  const submitConfirmation = async (payload: InboundConfirmationPayload): Promise<void> => {
    if (!confirmationOrder?.id) return;
    try { await confirmMutation.mutateAsync({ id: confirmationOrder.id, payload }); message.success(t('inbound.messages.confirmed')); setConfirmationOrder(undefined); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };
  const submitReceive = async (payload: InboundReceiptPayload): Promise<void> => {
    if (!receivalOrder?.id) return;
    try { await receiveMutation.mutateAsync({ id: receivalOrder.id, payload }); message.success(t('inbound.messages.received')); setReceivalOrder(undefined); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const columns = useMemo<ProColumns<InboundOrder>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('inbound.searchPlaceholder') } },
    { title: t('inbound.fields.orderNo'), dataIndex: 'orderNo', width: 180, fixed: 'left', search: false, copyable: true },
    { title: t('inbound.fields.supplier'), dataIndex: 'supplierName', width: 200, search: false },
    { title: t('inbound.fields.status'), dataIndex: 'status', width: 160, search: false, render: (_, order) => <OrderStatusTag description={order.statusDescription} domain="inbound" status={order.status} /> },
    { title: t('inbound.fields.totalPlanQty'), dataIndex: 'totalPlanQty', width: 110, align: 'right', search: false },
    { title: t('inbound.fields.totalConfirmedQty'), dataIndex: 'totalConfirmedQty', width: 120, align: 'right', search: false },
    { title: t('inbound.fields.totalActualQty'), dataIndex: 'totalActualQty', width: 110, align: 'right', search: false },
    { title: t('inbound.fields.expectedDate'), dataIndex: 'expectedDate', width: 145, search: false, renderText: (value) => formatDate(value as string | undefined, i18n.language) },
    { title: t('inbound.fields.applicant'), dataIndex: 'applicantName', width: 130, search: false },
    { title: t('common.createdAt'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', width: 100, fixed: 'right', render: (_, order) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelectedOrderId(order.id)} size="small" type="link">{t('common.view')}</Button>] },
  ], [i18n.language, t]);

  const tabs: InboundOrderStatus[] = ['PENDING_APPROVAL', 'APPROVED_PLAN', 'AWAITING_RECEIVAL', 'COMPLETED', 'REJECTED'];

  return (
    <section className="data-page workflow-page">
      <Tabs activeKey={status} items={tabs.map((value) => ({ key: value, label: t(`inbound.tabs.${value}`) }))} onChange={(key) => setStatus(key as InboundOrderStatus)} />
      <ProTable<InboundOrder, InboundTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('inbound.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const orders = await listInboundOrders(status);
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword ? orders.filter((order) => [order.orderNo, order.supplierCode, order.supplierName].some((value) => value?.toLowerCase().includes(keyword))) : orders;
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey="id"
        scroll={{ x: 1515 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [<Button icon={<PlusOutlined />} key="create" onClick={() => setFormOpen(true)} type="primary">{t('inbound.create')}</Button>]}
      />

      <InboundOrderFormDrawer loading={createMutation.isPending} onClose={() => setFormOpen(false)} onSubmit={saveOrder} open={formOpen} />
      <InboundOrderDrawer orderId={selectedOrderId} onApprove={setApprovalOrder} onClose={() => setSelectedOrderId(undefined)} onConfirmOrder={setConfirmationOrder} onReceive={setReceivalOrder} onReject={setRejectOrder} />
      <InboundApprovalModal loading={approveMutation.isPending} onCancel={() => setApprovalOrder(undefined)} onConfirm={submitApproval} open={approvalOrder !== undefined} order={approvalOrder} />
      <ReasonModal danger description={t('inbound.reject.notice')} loading={rejectMutation.isPending} onCancel={() => setRejectOrder(undefined)} onConfirm={submitReject} open={rejectOrder !== undefined} title={t('inbound.actions.reject')} />
      <InboundConfirmModal loading={confirmMutation.isPending} onCancel={() => setConfirmationOrder(undefined)} onConfirm={submitConfirmation} open={confirmationOrder !== undefined} order={confirmationOrder} />
      <InboundReceiveModal loading={receiveMutation.isPending} onCancel={() => setReceivalOrder(undefined)} onConfirm={submitReceive} open={receivalOrder !== undefined} order={receivalOrder} />
    </section>
  );
}
