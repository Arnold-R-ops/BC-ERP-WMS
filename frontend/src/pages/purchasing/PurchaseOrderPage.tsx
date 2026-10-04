import { AuditOutlined, DownloadOutlined, EyeOutlined, PlusOutlined, SettingOutlined, UploadOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Modal, Tabs } from 'antd';
import { OrderImportErrors, orderImportErrors } from '../../components/OrderImportErrors';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { getErrorMessage } from '../../api/errors';
import { saveBlob } from '../../api/files';
import { paginateArray } from '../../api/pagination';
import {
  approvePurchaseOrder,
  cancelPurchaseAsn,
  cancelPurchaseOrder,
  closePurchaseAsnRemainder,
  confirmPurchaseAsn,
  correctPurchaseAsn,
  createPurchaseAsnDraft,
  createPurchaseOrder,
  downloadPurchaseOrderTemplate,
  getPurchaseOrderAllocations,
  getPurchaseGovernancePolicy,
  importPurchaseOrder,
  listPurchaseOrders,
  PURCHASE_ORDERS_QUERY_KEY,
  quickConfirmPurchaseAsn,
  rejectPurchaseOrder,
  savePurchaseOrderAllocations,
  submitPurchaseOrder,
  updatePurchaseGovernancePolicy,
  updatePurchaseAsnDraft,
  updatePurchaseOrder,
  voidPurchaseOrder,
  withdrawPurchaseOrder,
  type CreatePurchaseAsnRequest,
  type PurchaseAsn,
  type PurchaseGovernancePolicyUpdatePayload,
  type PurchaseOrder,
  type PurchaseOrderAllocation,
  type PurchaseOrderStatus,
  type PurchaseOrderUpdatePayload,
} from '../../api/purchasing';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { ReasonModal } from '../../components/ReasonModal';
import { formatDate, formatDateTime, formatMoney } from '../workflowUtils';
import { PurchaseConfirmModal } from './PurchaseConfirmModal';
import { PurchaseGovernancePolicyModal } from './PurchaseGovernancePolicyModal';
import { PurchaseImportModal, type PurchaseImportValues } from './PurchaseImportModal';
import { PurchaseOrderDrawer, type PurchaseWorkflowAction } from './PurchaseOrderDrawer';
import { PurchaseOrderFormDrawer, type PurchaseOrderFormValues } from './PurchaseOrderFormDrawer';
import { PurchaseRemainderClosureDrawer } from './PurchaseRemainderClosureDrawer';
import { persistPurchaseDraft, PurchaseAllocationPersistenceError } from './purchaseOrderPersistence';

interface PurchaseTableParams { current?: number; pageSize?: number; search?: string }
interface PendingWorkflow { action: PurchaseWorkflowAction; order: PurchaseOrder }
interface AsnDraftEditor { asn?: PurchaseAsn; order: PurchaseOrder }
interface PendingAsnConfirmation { asn: PurchaseAsn; order: PurchaseOrder }
interface PendingAsnReason { action: 'correct' | 'cancel'; asn: PurchaseAsn; order: PurchaseOrder }

const REASON_ACTIONS: PurchaseWorkflowAction[] = ['reject', 'withdraw', 'cancel', 'void'];

export function PurchaseOrderPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<PurchaseOrderStatus>();
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [formOpen, setFormOpen] = useState(false);
  const [editOrder, setEditOrder] = useState<PurchaseOrder>();
  const [editAllocations, setEditAllocations] = useState<PurchaseOrderAllocation[]>([]);
  const [importOpen, setImportOpen] = useState(false);
  const [shipmentOrder, setShipmentOrder] = useState<PurchaseOrder>();
  const [asnDraftEditor, setAsnDraftEditor] = useState<AsnDraftEditor>();
  const [pendingAsnConfirmation, setPendingAsnConfirmation] = useState<PendingAsnConfirmation>();
  const [pendingAsnReason, setPendingAsnReason] = useState<PendingAsnReason>();
  const [closureAsn, setClosureAsn] = useState<PurchaseAsn>();
  const [closureQueueOpen, setClosureQueueOpen] = useState(false);
  const [policyOpen, setPolicyOpen] = useState(false);
  const [pendingWorkflow, setPendingWorkflow] = useState<PendingWorkflow>();
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const { message, modal } = AntdApp.useApp();
  const queryClient = useQueryClient();

  const canCreate = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:create');
  const canEdit = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:update');
  const canSubmit = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:submit');
  const canApprove = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:approve');
  const canCancel = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:cancel');
  const canVoid = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:void');
  const canConfirmShipment = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:asn:confirm');
  const canCreateAsn = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:asn:create');
  const canUpdateAsn = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:asn:update');
  const canConfirmAsn = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:asn:confirm');
  const canCancelAsn = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:asn:cancel');
  const canViewAsn = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:asn:view');
  const canViewReceiving = hasPermission(session?.currentRole, session?.permissionCodes, 'receiving:view');
  const canExecuteReceiving = hasPermission(session?.currentRole, session?.permissionCodes, 'receiving:execute');
  const canRequestClosure = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:request_close_remaining');
  const canApproveClosure = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:approve_close_remaining');
  const canDirectClose = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:asn:close');
  const canConfigureGovernance = session?.currentRole === 'TENANT_ADMIN';
  const governanceQuery = useQuery({ queryKey: ['purchase-governance-policy'], queryFn: getPurchaseGovernancePolicy });
  const closureMode = governanceQuery.data?.effectiveShortReceiptClosureMode;
  const canCloseRemainder = closureMode === 'APPROVAL_REQUIRED'
    ? canRequestClosure
    : closureMode === 'DIRECT_WITH_REASON' && canDirectClose;
  const canViewClosureQueue = closureMode === 'APPROVAL_REQUIRED' && (canRequestClosure || canApproveClosure);

  useEffect(() => { actionRef.current?.reloadAndRest?.(); }, [status]);

  const refresh = async (): Promise<void> => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: PURCHASE_ORDERS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: ['purchase-order', selectedOrderId] }),
      queryClient.invalidateQueries({ queryKey: ['purchase-order-allocations', selectedOrderId] }),
      queryClient.invalidateQueries({ queryKey: ['purchase-asns', selectedOrderId] }),
      queryClient.invalidateQueries({ queryKey: ['receiving-tasks'] }),
      queryClient.invalidateQueries({ queryKey: ['purchase-remainder-closure-requests'] }),
    ]);
    actionRef.current?.reload();
  };

  const createMutation = useMutation({ mutationFn: createPurchaseOrder });
  const updateMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: PurchaseOrderUpdatePayload }) => updatePurchaseOrder(id, payload) });
  const importMutation = useMutation({ mutationFn: importPurchaseOrder });
  const shipmentMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: CreatePurchaseAsnRequest }) => quickConfirmPurchaseAsn(id, payload) });
  const createAsnMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: CreatePurchaseAsnRequest }) => createPurchaseAsnDraft(id, payload) });
  const updateAsnMutation = useMutation({ mutationFn: ({ id, payload }: { id: number; payload: CreatePurchaseAsnRequest & { version: number } }) => updatePurchaseAsnDraft(id, payload) });
  const confirmAsnMutation = useMutation({ mutationFn: confirmPurchaseAsn });
  const correctAsnMutation = useMutation({ mutationFn: ({ id, reason }: { id: number; reason: string }) => correctPurchaseAsn(id, reason) });
  const cancelAsnMutation = useMutation({ mutationFn: ({ id, reason }: { id: number; reason: string }) => cancelPurchaseAsn(id, reason) });
  const closureMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => {
      if (!closureMode) throw new Error(t('purchasing.messages.governanceLoadFailed'));
      return closePurchaseAsnRemainder(id, reason, closureMode);
    },
  });
  const policyMutation = useMutation({ mutationFn: updatePurchaseGovernancePolicy });
  const workflowMutation = useMutation({
    mutationFn: ({ action, id, version, reason }: { action: PurchaseWorkflowAction; id: number; version: number; reason?: string }) => {
      const payload = { version, reason };
      switch (action) {
        case 'submit': return submitPurchaseOrder(id, payload);
        case 'approve': return approvePurchaseOrder(id, payload);
        case 'reject': return rejectPurchaseOrder(id, payload);
        case 'withdraw': return withdrawPurchaseOrder(id, payload);
        case 'cancel': return cancelPurchaseOrder(id, payload);
        case 'void': return voidPurchaseOrder(id, payload);
      }
    },
  });

  const saveOrder = async (values: PurchaseOrderFormValues): Promise<boolean> => {
    const wasEditing = editOrder !== undefined;
    try {
      await persistPurchaseDraft(values, editOrder, {
        create: (payload) => createMutation.mutateAsync(payload),
        update: (id, payload) => updateMutation.mutateAsync({ id, payload }),
        saveAllocations: (id, version, allocations) => savePurchaseOrderAllocations(id, version, allocations),
      });
      message.success(wasEditing ? t('purchasing.messages.updated') : t('purchasing.messages.created'));
      setEditOrder(undefined);
      setEditAllocations([]);
      setFormOpen(false);
      await refresh();
      return true;
    } catch (error) {
      if (error instanceof PurchaseAllocationPersistenceError) {
        setEditOrder(error.order);
        setEditAllocations(error.allocations);
        message.error(t('purchasing.messages.draftSavedAllocationPending'));
        await refresh();
        return false;
      }
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const editInitialValues = useMemo<PurchaseOrderFormValues | undefined>(() => {
    if (!editOrder?.supplierId) return undefined;
    return {
      supplierId: editOrder.supplierId,
      expectedDate: editOrder.expectedDate,
      remark: editOrder.remark,
      items: (editOrder.items ?? []).flatMap((item) => {
        if (item.productSkuId === undefined || item.orderedQuantity === undefined) return [];
        const allocations = editAllocations.filter((candidate) => candidate.purchaseOrderItemId === item.id);
        return [{
          id: item.id,
          productSkuId: item.productSkuId,
          orderedQuantity: item.orderedQuantity,
          unitCost: item.unitCost,
          expiryDate: item.expiryDate,
          productionDate: item.productionDate,
          externalBatchCode: item.externalBatchCode,
          remark: item.remark,
          allocations: allocations.length > 0
            ? allocations.map((allocation) => ({
              warehouseId: allocation.warehouseId,
              plannedQuantity: allocation.plannedQuantity,
            }))
            : [{ plannedQuantity: item.orderedQuantity }],
        }];
      }),
    };
  }, [editAllocations, editOrder]);

  const openEdit = async (order: PurchaseOrder): Promise<void> => {
    if (!order.id) return;
    try {
      const allocations = await queryClient.fetchQuery({
        queryKey: ['purchase-order-allocations', order.id],
        queryFn: () => getPurchaseOrderAllocations(order.id as number),
      });
      setEditAllocations(allocations);
      setEditOrder(order);
      setFormOpen(true);
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const importOrder = async (values: PurchaseImportValues): Promise<void> => {
    try {
      await importMutation.mutateAsync(values);
      message.success(t('purchasing.messages.importedDraft'));
      setImportOpen(false);
      await refresh();
    } catch (error) {
      if (orderImportErrors(error).length) modal.error({ title: '订单导入检查', width: 720, content: <OrderImportErrors error={error} /> });
      else message.error(getErrorMessage(error, t));
    }
  };

  const submitShipment = async (payload: CreatePurchaseAsnRequest): Promise<void> => {
    if (!shipmentOrder?.id) return;
    try {
      await shipmentMutation.mutateAsync({ id: shipmentOrder.id, payload });
      message.success(t('purchasing.messages.shipmentConfirmed'));
      setShipmentOrder(undefined);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const saveAsnDraft = async (payload: CreatePurchaseAsnRequest): Promise<void> => {
    if (!asnDraftEditor?.order.id) return;
    try {
      if (asnDraftEditor.asn) {
        await updateAsnMutation.mutateAsync({
          id: asnDraftEditor.asn.id,
          payload: { ...payload, version: asnDraftEditor.asn.version },
        });
        message.success(t('purchasing.asn.messages.draftUpdated'));
      } else {
        await createAsnMutation.mutateAsync({ id: asnDraftEditor.order.id, payload });
        message.success(t('purchasing.asn.messages.draftCreated'));
      }
      setAsnDraftEditor(undefined);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const submitAsnConfirmation = async (): Promise<void> => {
    if (!pendingAsnConfirmation) return;
    try {
      await confirmAsnMutation.mutateAsync(pendingAsnConfirmation.asn.id);
      message.success(t('purchasing.asn.messages.confirmed'));
      setPendingAsnConfirmation(undefined);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const submitAsnReason = async (reason: string): Promise<void> => {
    if (!pendingAsnReason) return;
    try {
      if (pendingAsnReason.action === 'correct') {
        const draft = await correctAsnMutation.mutateAsync({ id: pendingAsnReason.asn.id, reason });
        message.success(t('purchasing.asn.messages.corrected', { asnNumber: draft.asnNumber }));
        const order = pendingAsnReason.order;
        setPendingAsnReason(undefined);
        await refresh();
        if (canUpdateAsn) setAsnDraftEditor({ order, asn: draft });
      } else {
        await cancelAsnMutation.mutateAsync({ id: pendingAsnReason.asn.id, reason });
        message.success(t('purchasing.asn.messages.cancelled'));
        setPendingAsnReason(undefined);
        await refresh();
      }
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const requestAsnAction = (order: PurchaseOrder, asn: PurchaseAsn, action: 'confirm' | 'correct' | 'cancel'): void => {
    if (action === 'confirm') setPendingAsnConfirmation({ order, asn });
    else setPendingAsnReason({ action, asn, order });
  };

  const executeWorkflow = async (order: PurchaseOrder, action: PurchaseWorkflowAction, reason?: string): Promise<void> => {
    if (!order.id || order.version === undefined) return;
    try {
      await workflowMutation.mutateAsync({ action, id: order.id, version: order.version, reason });
      message.success(t(`purchasing.messages.${action}`));
      setPendingWorkflow(undefined);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const submitClosure = async (reason: string): Promise<void> => {
    if (!closureAsn) return;
    try {
      await closureMutation.mutateAsync({ id: closureAsn.id, reason });
      message.success(closureMode === 'APPROVAL_REQUIRED' ? t('purchasing.messages.closureRequested') : t('purchasing.messages.remainderClosed'));
      setClosureAsn(undefined);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const saveGovernancePolicy = async (payload: PurchaseGovernancePolicyUpdatePayload): Promise<void> => {
    try {
      await policyMutation.mutateAsync(payload);
      message.success(t('purchasing.governance.saved'));
      setPolicyOpen(false);
      await queryClient.invalidateQueries({ queryKey: ['purchase-governance-policy'] });
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const requestWorkflow = (order: PurchaseOrder, action: PurchaseWorkflowAction): void => {
    if (REASON_ACTIONS.includes(action)) setPendingWorkflow({ order, action });
    else void executeWorkflow(order, action);
  };

  const columns = useMemo<ProColumns<PurchaseOrder>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('purchasing.searchPlaceholder') } },
    { title: t('purchasing.fields.poNumber'), dataIndex: 'poNumber', width: 180, fixed: 'left', search: false, copyable: true },
    { title: t('purchasing.fields.supplier'), dataIndex: 'supplier', width: 200, search: false },
    { title: t('purchasing.fields.status'), dataIndex: 'status', width: 170, search: false, render: (_, order) => <OrderStatusTag domain="purchasing" status={order.status} /> },
    { title: t('purchasing.fields.totalQuantity'), dataIndex: 'totalQuantity', width: 120, align: 'right', search: false },
    { title: t('purchasing.fields.totalCost'), dataIndex: 'totalCost', width: 140, align: 'right', search: false, render: (value) => value === undefined ? '-' : formatMoney(value as number, i18n.language) },
    { title: t('purchasing.fields.expectedDate'), dataIndex: 'expectedDate', width: 145, search: false, renderText: (value) => formatDate(value as string | undefined, i18n.language) },
    { title: t('common.createdAt'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', width: 100, fixed: 'right', render: (_, order) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelectedOrderId(order.id)} size="small" type="link">{t('common.view')}</Button>] },
  ], [i18n.language, t]);

  const tabs = [undefined, 'DRAFT', 'PENDING_APPROVAL', 'AWAITING_SHIPMENT', 'PARTIALLY_SHIPPED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED', 'COMPLETED'] as const;

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
        scroll={{ x: 1350 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          ...(canViewClosureQueue ? [
            <Button icon={<AuditOutlined />} key="closure-queue" onClick={() => setClosureQueueOpen(true)}>{t('purchasing.actions.closureQueue')}</Button>,
          ] : []),
          ...(canConfigureGovernance ? [
            <Button icon={<SettingOutlined />} key="governance" onClick={() => setPolicyOpen(true)}>{t('purchasing.actions.configureGovernance')}</Button>,
          ] : []),
          ...(canCreate ? [
            <Button icon={<DownloadOutlined />} key="template" onClick={() => void downloadPurchaseOrderTemplate().then((blob) => saveBlob(blob, 'purchase_order_import_template.xlsx')).catch((error) => message.error(getErrorMessage(error, t)))}>{t('purchasing.actions.template')}</Button>,
            <Button icon={<UploadOutlined />} key="import" onClick={() => setImportOpen(true)}>{t('purchasing.actions.import')}</Button>,
            <Button icon={<PlusOutlined />} key="create" onClick={() => { setEditOrder(undefined); setEditAllocations([]); setFormOpen(true); }} type="primary">{t('purchasing.create')}</Button>,
          ] : []),
        ]}
      />
      <PurchaseOrderFormDrawer initialValues={editInitialValues} loading={createMutation.isPending || updateMutation.isPending} mode={editOrder ? 'edit' : 'create'} onClose={() => { setFormOpen(false); setEditOrder(undefined); setEditAllocations([]); }} onSubmit={saveOrder} open={formOpen} />
      <PurchaseImportModal loading={importMutation.isPending} onCancel={() => setImportOpen(false)} onConfirm={importOrder} open={importOpen} />
      <PurchaseGovernancePolicyModal
        loading={policyMutation.isPending}
        onCancel={() => setPolicyOpen(false)}
        onConfirm={saveGovernancePolicy}
        open={policyOpen}
        policy={governanceQuery.data}
      />
      <PurchaseRemainderClosureDrawer
        canApprove={canApproveClosure}
        canRequest={canRequestClosure}
        currentUsername={session?.username}
        onChanged={refresh}
        onClose={() => setClosureQueueOpen(false)}
        open={closureQueueOpen}
      />
      <PurchaseOrderDrawer
        canApprove={canApprove}
        canCancel={canCancel}
        canCancelAsn={canCancelAsn}
        canCloseRemainder={canCloseRemainder}
        canConfirmAsn={canConfirmAsn}
        canConfirmShipment={canConfirmShipment}
        canCreateAsn={canCreateAsn}
        canEdit={canEdit}
        canExecuteReceiving={canExecuteReceiving}
        canSubmit={canSubmit}
        canUpdateAsn={canUpdateAsn}
        canViewAsn={canViewAsn}
        canViewReceiving={canViewReceiving}
        canVoid={canVoid}
        orderId={selectedOrderId}
        onAsnAction={requestAsnAction}
        onClose={() => setSelectedOrderId(undefined)}
        onCloseRemainder={setClosureAsn}
        onConfirmShipment={setShipmentOrder}
        onCreateAsn={(order) => setAsnDraftEditor({ order })}
        onEdit={(order) => void openEdit(order)}
        onEditAsn={(order, asn) => setAsnDraftEditor({ order, asn })}
        onWorkflow={requestWorkflow}
      />
      <PurchaseConfirmModal loading={shipmentMutation.isPending} onCancel={() => setShipmentOrder(undefined)} onConfirm={submitShipment} open={shipmentOrder !== undefined} order={shipmentOrder} />
      <PurchaseConfirmModal
        asn={asnDraftEditor?.asn}
        intent="draft"
        loading={createAsnMutation.isPending || updateAsnMutation.isPending}
        onCancel={() => setAsnDraftEditor(undefined)}
        onConfirm={saveAsnDraft}
        open={asnDraftEditor !== undefined}
        order={asnDraftEditor?.order}
      />
      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={confirmAsnMutation.isPending}
        destroyOnHidden
        okText={t('purchasing.asn.actions.confirm')}
        onCancel={() => setPendingAsnConfirmation(undefined)}
        onOk={() => void submitAsnConfirmation()}
        open={pendingAsnConfirmation !== undefined}
        title={t('purchasing.asn.confirmTitle', { asnNumber: pendingAsnConfirmation?.asn.asnNumber ?? '-' })}
      >
        {t('purchasing.asn.confirmNotice')}
      </Modal>
      <ReasonModal
        danger
        description={pendingAsnReason ? t(`purchasing.asn.${pendingAsnReason.action}Notice`) : ''}
        loading={correctAsnMutation.isPending || cancelAsnMutation.isPending}
        onCancel={() => setPendingAsnReason(undefined)}
        onConfirm={submitAsnReason}
        open={pendingAsnReason !== undefined}
        title={pendingAsnReason ? t(`purchasing.asn.${pendingAsnReason.action}Title`, { asnNumber: pendingAsnReason.asn.asnNumber }) : ''}
      />
      <ReasonModal
        danger
        description={closureMode === 'APPROVAL_REQUIRED' ? t('purchasing.closure.approvalNotice') : t('purchasing.closure.directNotice')}
        loading={closureMutation.isPending}
        onCancel={() => setClosureAsn(undefined)}
        onConfirm={submitClosure}
        open={closureAsn !== undefined}
        title={t('purchasing.actions.closeRemainder')}
      />
      <ReasonModal
        danger={pendingWorkflow?.action === 'cancel' || pendingWorkflow?.action === 'void' || pendingWorkflow?.action === 'reject'}
        description={pendingWorkflow ? t(`purchasing.workflow.${pendingWorkflow.action}Notice`) : ''}
        loading={workflowMutation.isPending}
        onCancel={() => setPendingWorkflow(undefined)}
        onConfirm={(reason) => pendingWorkflow ? executeWorkflow(pendingWorkflow.order, pendingWorkflow.action, reason) : undefined}
        open={pendingWorkflow !== undefined}
        title={pendingWorkflow ? t(`purchasing.actions.${pendingWorkflow.action}`) : ''}
      />
    </section>
  );
}
