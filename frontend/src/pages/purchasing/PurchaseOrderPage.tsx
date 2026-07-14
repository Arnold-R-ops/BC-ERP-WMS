import { EyeOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Tabs } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../auth/AuthProvider';
import { getErrorMessage } from '../../api/errors';
import { resolveCurrentUser } from '../../api/masterData';
import { paginateArray } from '../../api/pagination';
import {
  confirmPurchaseOrder,
  createPurchaseOrder,
  importPurchaseOrder,
  listPurchaseOrders,
  PURCHASE_ORDERS_QUERY_KEY,
  receivePurchaseOrder,
  rollbackPurchaseOrder,
  type PurchaseConfirmPayload,
  type PurchaseOrder,
  type PurchaseOrderStatus,
  type PurchaseReceiptPayload,
} from '../../api/purchasing';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { ReasonModal } from '../../components/ReasonModal';
import { formatDate, formatDateTime, formatMoney } from '../workflowUtils';
import { PurchaseConfirmModal } from './PurchaseConfirmModal';
import { PurchaseImportModal, type PurchaseImportValues } from './PurchaseImportModal';
import { PurchaseOrderDrawer } from './PurchaseOrderDrawer';
import { PurchaseOrderFormDrawer, type PurchaseOrderFormValues } from './PurchaseOrderFormDrawer';
import { PurchaseReceiveModal } from './PurchaseReceiveModal';

interface PurchaseTableParams { current?: number; pageSize?: number; search?: string }

export function PurchaseOrderPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<PurchaseOrderStatus>();
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [formOpen, setFormOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [confirmOrder, setConfirmOrder] = useState<PurchaseOrder>();
  const [receiveOrder, setReceiveOrder] = useState<PurchaseOrder>();
  const [rollbackOrder, setRollbackOrder] = useState<PurchaseOrder>();
  const [operatorId, setOperatorId] = useState<number>();
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();

  useEffect(() => { actionRef.current?.reloadAndRest?.(); }, [status]);

  const resolveOperator = async (): Promise<{ id: number; name: string }> => {
    if (!session) throw new Error('Authentication session is unavailable');
    const user = await queryClient.fetchQuery({ queryKey: ['current-user', session.username], queryFn: () => resolveCurrentUser(session.username) });
    setOperatorId(user.id);
    return { id: user.id as number, name: session.username };
  };

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: PURCHASE_ORDERS_QUERY_KEY });
    actionRef.current?.reload();
    if (selectedOrderId !== undefined) await queryClient.invalidateQueries({ queryKey: ['purchase-order', selectedOrderId] });
  };

  const createMutation = useMutation({ mutationFn: createPurchaseOrder });
  const importMutation = useMutation({ mutationFn: importPurchaseOrder });
  const confirmMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: PurchaseConfirmPayload }) => confirmPurchaseOrder(id, payload) });
  const receiveMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: PurchaseReceiptPayload }) => receivePurchaseOrder(id, payload) });
  const rollbackMutation = useMutation({ mutationFn: ({ id, reason }: { id: number; reason: string }) => rollbackPurchaseOrder(id, reason) });

  const saveOrder = async (values: PurchaseOrderFormValues): Promise<boolean> => {
    try {
      const operator = await resolveOperator();
      await createMutation.mutateAsync({ ...values, operatorId: operator.id, operatorName: operator.name });
      message.success(t('purchasing.messages.created'));
      setFormOpen(false);
      await refresh();
      return true;
    } catch (error) { message.error(getErrorMessage(error, t)); return false; }
  };

  const importOrder = async (values: PurchaseImportValues): Promise<void> => {
    try {
      const operator = await resolveOperator();
      await importMutation.mutateAsync({ ...values, operatorId: operator.id, operatorName: operator.name });
      message.success(t('purchasing.messages.imported'));
      setImportOpen(false);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const submitConfirm = async (payload: PurchaseConfirmPayload): Promise<void> => {
    if (!confirmOrder?.id) return;
    try { await confirmMutation.mutateAsync({ id: confirmOrder.id, payload }); message.success(t('purchasing.messages.confirmed')); setConfirmOrder(undefined); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const openReceive = async (order: PurchaseOrder): Promise<void> => {
    try { const operator = await resolveOperator(); setOperatorId(operator.id); setReceiveOrder(order); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const submitReceive = async (payload: PurchaseReceiptPayload): Promise<void> => {
    if (!receiveOrder?.id) return;
    try { await receiveMutation.mutateAsync({ id: receiveOrder.id, payload }); message.success(t('purchasing.messages.received')); setReceiveOrder(undefined); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const submitRollback = async (reason: string): Promise<void> => {
    if (!rollbackOrder?.id) return;
    try { await rollbackMutation.mutateAsync({ id: rollbackOrder.id, reason }); message.success(t('purchasing.messages.rolledBack')); setRollbackOrder(undefined); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const columns = useMemo<ProColumns<PurchaseOrder>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('purchasing.searchPlaceholder') } },
    { title: t('purchasing.fields.poNumber'), dataIndex: 'poNumber', width: 180, fixed: 'left', search: false, copyable: true },
    { title: t('purchasing.fields.supplier'), dataIndex: 'supplier', width: 200, search: false },
    { title: t('purchasing.fields.status'), dataIndex: 'status', width: 160, search: false, render: (_, order) => <OrderStatusTag domain="purchasing" status={order.status} /> },
    { title: t('purchasing.fields.totalQuantity'), dataIndex: 'totalQuantity', width: 120, align: 'right', search: false },
    { title: t('purchasing.fields.totalCost'), dataIndex: 'totalCost', width: 140, align: 'right', search: false, render: (value) => value === undefined ? '-' : formatMoney(value as number, i18n.language) },
    { title: t('purchasing.fields.expectedDate'), dataIndex: 'expectedDate', width: 145, search: false, renderText: (value) => formatDate(value as string | undefined, i18n.language) },
    { title: t('purchasing.fields.operator'), dataIndex: 'operatorName', width: 130, search: false },
    { title: t('common.createdAt'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', width: 100, fixed: 'right', render: (_, order) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelectedOrderId(order.id)} size="small" type="link">{t('common.view')}</Button>] },
  ], [i18n.language, t]);

  const tabs = [undefined, 'ORDERING', 'IN_TRANSIT', 'PARTIALLY_RECEIVED', 'COMPLETED'] as const;

  return (
    <section className="data-page workflow-page">
      <Tabs activeKey={status ?? 'ALL'} items={tabs.map((value) => ({ key: value ?? 'ALL', label: t(`purchasing.tabs.${value ?? 'ALL'}`) }))} onChange={(key) => setStatus(key === 'ALL' ? undefined : key as PurchaseOrderStatus)} />
      <ProTable<PurchaseOrder, PurchaseTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('purchasing.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const orders = await listPurchaseOrders(status);
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword ? orders.filter((order) => [order.poNumber, order.supplier].some((value) => value?.toLowerCase().includes(keyword))) : orders;
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey="id"
        scroll={{ x: 1455 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button icon={<UploadOutlined />} key="import" onClick={() => setImportOpen(true)}>{t('purchasing.actions.import')}</Button>,
          <Button icon={<PlusOutlined />} key="create" onClick={() => setFormOpen(true)} type="primary">{t('purchasing.create')}</Button>,
        ]}
      />

      <PurchaseOrderFormDrawer loading={createMutation.isPending} onClose={() => setFormOpen(false)} onSubmit={saveOrder} open={formOpen} />
      <PurchaseImportModal loading={importMutation.isPending} onCancel={() => setImportOpen(false)} onConfirm={importOrder} open={importOpen} />
      <PurchaseOrderDrawer orderId={selectedOrderId} onClose={() => setSelectedOrderId(undefined)} onConfirmAsn={setConfirmOrder} onReceive={(order) => void openReceive(order)} onRollback={setRollbackOrder} />
      <PurchaseConfirmModal loading={confirmMutation.isPending} onCancel={() => setConfirmOrder(undefined)} onConfirm={submitConfirm} open={confirmOrder !== undefined} order={confirmOrder} />
      <PurchaseReceiveModal loading={receiveMutation.isPending} onCancel={() => setReceiveOrder(undefined)} onConfirm={submitReceive} open={receiveOrder !== undefined} operatorId={operatorId} operatorName={session?.username ?? ''} order={receiveOrder} />
      <ReasonModal description={t('purchasing.rollback.notice')} loading={rollbackMutation.isPending} onCancel={() => setRollbackOrder(undefined)} onConfirm={submitRollback} open={rollbackOrder !== undefined} title={t('purchasing.actions.rollback')} />
    </section>
  );
}
