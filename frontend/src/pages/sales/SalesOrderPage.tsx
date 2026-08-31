import { DownloadOutlined, EyeOutlined, PlusOutlined, SettingOutlined, UploadOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Tabs, Tag, Upload } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { hasPermission } from '../../access';
import { getErrorMessage } from '../../api/errors';
import { saveBlob } from '../../api/files';
import { useAuth } from '../../auth/AuthProvider';
import { listCustomers } from '../../api/masterData';
import { paginateArray } from '../../api/pagination';
import {
  approveSalesOrder,
  cancelSalesOrder,
  closeSalesOrder,
  createSalesOrder,
  deleteDraftSalesOrder,
  downloadSalesOrderTemplate,
  importSalesOrderItems,
  listSalesOrders,
  rejectSalesOrder,
  SALES_ORDERS_QUERY_KEY,
  type SalesApprovalPayload,
  type SalesOrder,
  type SalesOrderItemPayload,
  type SalesOrderPayload,
  submitSalesOrder,
  type SalesAllocationStatus,
  type SalesCommercialStatus,
  type SalesWarehouseStatus,
  voidSalesOrder,
} from '../../api/sales';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { ReasonModal } from '../../components/ReasonModal';
import { formatDateTime, formatMoney } from '../workflowUtils';
import { SalesApprovalModal } from './SalesApprovalModal';
import { SalesOrderDrawer, type SalesReasonAction } from './SalesOrderDrawer';
import { SalesOrderFormDrawer } from './SalesOrderFormDrawer';
import { SalesPolicyModal } from './SalesPolicyModal';

interface SalesTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  customerId?: number;
  allocationStatus?: SalesAllocationStatus;
  warehouseStatus?: SalesWarehouseStatus;
}

interface ReasonActionState {
  action: SalesReasonAction;
  order: SalesOrder;
}

const salesStatuses = new Set<SalesCommercialStatus>([
  'DRAFT',
  'PENDING_APPROVAL',
  'APPROVED',
  'COMPLETED',
  'CLOSED',
  'REJECTED',
  'CANCELLED',
  'VOIDED',
]);

function readSalesStatus(value: string | null): SalesCommercialStatus | undefined {
  return value && salesStatuses.has(value as SalesCommercialStatus)
    ? value as SalesCommercialStatus
    : undefined;
}

function readOrderId(value: string | null): number | undefined {
  const parsed = value ? Number(value) : Number.NaN;
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

export function SalesOrderPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [status, setStatus] = useState<SalesCommercialStatus | undefined>(() => readSalesStatus(searchParams.get('commercialStatus')));
  const [selectedOrderId, setSelectedOrderId] = useState<number | undefined>(() => readOrderId(searchParams.get('orderId')));
  const [formOpen, setFormOpen] = useState(false);
  const [initialItems, setInitialItems] = useState<SalesOrderItemPayload[]>();
  const [approvalOrder, setApprovalOrder] = useState<SalesOrder>();
  const [reasonAction, setReasonAction] = useState<ReasonActionState>();
  const [policyOpen, setPolicyOpen] = useState(false);
  const { i18n, t } = useTranslation();
  const { session } = useAuth();
  const { message, modal } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const customersQuery = useQuery({ queryKey: ['customers'], queryFn: () => listCustomers(false) });
  const canCreate = hasPermission(session?.currentRole, session?.permissionCodes, 'sales:create');
  const canManagePolicy = hasPermission(session?.currentRole, session?.permissionCodes, 'sales:policy:manage');

  useEffect(() => {
    actionRef.current?.reloadAndRest?.();
  }, [status]);

  useEffect(() => {
    setStatus(readSalesStatus(searchParams.get('commercialStatus')));
    setSelectedOrderId(readOrderId(searchParams.get('orderId')));
  }, [searchParams]);

  const changeStatus = (nextStatus?: SalesCommercialStatus): void => {
    const next = new URLSearchParams(searchParams);
    if (nextStatus) next.set('commercialStatus', nextStatus);
    else next.delete('commercialStatus');
    next.delete('orderId');
    setSearchParams(next, { replace: true });
  };

  const openOrder = (id?: number): void => {
    if (id === undefined) return;
    const next = new URLSearchParams(searchParams);
    next.set('orderId', String(id));
    setSearchParams(next, { replace: true });
  };

  const closeOrder = (): void => {
    const next = new URLSearchParams(searchParams);
    next.delete('orderId');
    setSearchParams(next, { replace: true });
  };

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: SALES_ORDERS_QUERY_KEY });
    actionRef.current?.reload();
    if (selectedOrderId !== undefined) {
      await queryClient.invalidateQueries({ queryKey: ['sales-order', selectedOrderId] });
    }
  };

  const createMutation = useMutation({ mutationFn: (payload: SalesOrderPayload) => createSalesOrder(payload) });
  const approveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: SalesApprovalPayload }) => approveSalesOrder(id, payload),
  });
  const reasonMutation = useMutation({
    mutationFn: ({ action, id, reason }: { action: SalesReasonAction; id: number; reason: string }) => {
      if (action === 'reject') return rejectSalesOrder(id, reason);
      if (action === 'cancel') return cancelSalesOrder(id, reason);
      if (action === 'close') return closeSalesOrder(id, reason);
      return voidSalesOrder(id, reason);
    },
  });
  const submitMutation = useMutation({ mutationFn: (id: number) => submitSalesOrder(id) });
  const deleteMutation = useMutation({ mutationFn: (id: number) => deleteDraftSalesOrder(id) });

  const saveOrder = async (payload: SalesOrderPayload): Promise<boolean> => {
    try {
      const order = await createMutation.mutateAsync(payload);
      message.success(t(payload.saveAsDraft ? 'sales.messages.draftSaved' : 'sales.messages.created', { orderNo: order.orderNo ?? '-' }));
      setFormOpen(false);
      setInitialItems(undefined);
      await refresh();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const submitDraft = async (order: SalesOrder): Promise<void> => {
    if (order.id === undefined) return;
    try {
      await submitMutation.mutateAsync(order.id);
      message.success(t('sales.messages.submitted'));
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const deleteDraft = (order: SalesOrder): void => {
    if (order.id === undefined) return;
    modal.confirm({
      title: t('sales.actions.deleteDraft'),
      content: t('sales.confirmations.deleteDraft'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteMutation.mutateAsync(order.id as number);
          message.success(t('sales.messages.draftDeleted'));
          closeOrder();
          await refresh();
        } catch (error) { message.error(getErrorMessage(error, t)); throw error; }
      },
    });
  };

  const confirmApproval = async (payload: SalesApprovalPayload): Promise<void> => {
    if (!approvalOrder?.id) return;
    try {
      await approveMutation.mutateAsync({ id: approvalOrder.id, payload });
      message.success(t('sales.messages.approved'));
      setApprovalOrder(undefined);
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const confirmReasonAction = async (reason: string): Promise<void> => {
    if (!reasonAction?.order.id) return;
    try {
      await reasonMutation.mutateAsync({ action: reasonAction.action, id: reasonAction.order.id, reason });
      const messageKey: Record<SalesReasonAction, string> = {
        reject: 'rejected', cancel: 'cancelled', close: 'closed', void: 'voided',
      };
      message.success(t(`sales.messages.${messageKey[reasonAction.action]}`));
      setReasonAction(undefined);
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const customerOptions = (customersQuery.data ?? []).reduce<Record<number, { text: string }>>((result, customer) => {
    if (customer.id !== undefined) result[customer.id] = { text: customer.name ?? `#${customer.id}` };
    return result;
  }, {});

  const columns = useMemo<ProColumns<SalesOrder>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('sales.searchPlaceholder') } },
    { title: t('sales.fields.customer'), dataIndex: 'customerId', hideInTable: true, valueType: 'select', valueEnum: customerOptions },
    { title: t('sales.fields.orderNo'), dataIndex: 'orderNo', width: 180, fixed: 'left', search: false, copyable: true },
    { title: t('sales.fields.customer'), dataIndex: 'customerName', width: 200, search: false },
    { title: t('sales.fields.channel'), dataIndex: 'channel', width: 105, search: false, render: () => <Tag>-</Tag> },
    { title: t('sales.fields.totalAmount'), dataIndex: 'totalAmount', align: 'right', width: 135, search: false, renderText: (value) => formatMoney(value as number | undefined, i18n.language) },
    { title: t('sales.fields.commercialStatus'), dataIndex: 'commercialStatus', width: 150, search: false, render: (_, order) => <OrderStatusTag description={order.commercialStatusDescription} domain="sales" status={order.commercialStatus} /> },
    { title: t('sales.fields.allocationStatus'), dataIndex: 'allocationStatus', width: 150, valueType: 'select', valueEnum: { UNALLOCATED: { text: t('sales.statuses.UNALLOCATED') }, FULLY_ALLOCATED: { text: t('sales.statuses.FULLY_ALLOCATED') }, PARTIALLY_ALLOCATED: { text: t('sales.statuses.PARTIALLY_ALLOCATED') }, BACKORDERED: { text: t('sales.statuses.BACKORDERED') }, WAITING_INBOUND: { text: t('sales.statuses.WAITING_INBOUND') } }, render: (_, order) => <OrderStatusTag description={order.allocationStatusDescription} domain="sales" status={order.allocationStatus} /> },
    { title: t('sales.fields.warehouseStatus'), dataIndex: 'warehouseStatus', width: 155, valueType: 'select', valueEnum: { NOT_RELEASED: { text: t('sales.statuses.NOT_RELEASED') }, PENDING_PICK: { text: t('sales.statuses.PENDING_PICK') }, PICKING: { text: t('sales.statuses.PICKING') }, PARTIALLY_SHIPPED: { text: t('sales.statuses.PARTIALLY_SHIPPED') }, SHIPPED: { text: t('sales.statuses.SHIPPED') }, EXCEPTION: { text: t('sales.statuses.EXCEPTION') } }, render: (_, order) => <OrderStatusTag description={order.warehouseStatusDescription} domain="sales" status={order.warehouseStatus} /> },
    { title: t('sales.fields.applicant'), dataIndex: 'applicantName', width: 130, search: false },
    { title: t('common.createdAt'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', width: 100, fixed: 'right', render: (_, order) => [<Button icon={<EyeOutlined />} key="view" onClick={() => openOrder(order.id)} size="small" type="link">{t('common.view')}</Button>] },
  ], [customerOptions, i18n.language, searchParams, t]);

  const tabs = [undefined, 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'COMPLETED', 'CLOSED', 'CANCELLED', 'REJECTED'] as const;

  return (
    <section className="data-page workflow-page">
      <Tabs
        activeKey={status ?? 'ALL'}
        items={tabs.map((tabStatus) => ({ key: tabStatus ?? 'ALL', label: t(`sales.tabs.${tabStatus ?? 'ALL'}`) }))}
        onChange={(key) => changeStatus(key === 'ALL' ? undefined : key as SalesCommercialStatus)}
      />
      <ProTable<SalesOrder, SalesTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('sales.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const orders = await listSalesOrders({ commercialStatus: status, allocationStatus: params.allocationStatus, warehouseStatus: params.warehouseStatus, customerId: params.customerId });
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword
              ? orders.filter((order) => [order.orderNo, order.customerName].some((value) => value?.toLowerCase().includes(keyword)))
              : orders;
            return paginateArray(filtered, params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1490 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          ...(canManagePolicy ? [<Button icon={<SettingOutlined />} key="policy" onClick={() => setPolicyOpen(true)}>{t('sales.actions.policy')}</Button>] : []),
          ...(canCreate ? [
          <Button
            icon={<DownloadOutlined />}
            key="template"
            onClick={() => void downloadSalesOrderTemplate().then((blob) => saveBlob(blob, 'sales_order_import_template.xlsx')).catch((error) => message.error(getErrorMessage(error, t)))}
          >
            {t('sales.actions.template')}
          </Button>,
          <Upload
            accept=".xlsx"
            beforeUpload={(file) => {
              void importSalesOrderItems(file as File)
                .then((items) => { setInitialItems(items); setFormOpen(true); })
                .catch((error) => message.error(getErrorMessage(error, t)));
              return false;
            }}
            key="import"
            maxCount={1}
            showUploadList={false}
          >
            <Button icon={<UploadOutlined />}>{t('sales.actions.import')}</Button>
          </Upload>,
          <Button icon={<PlusOutlined />} key="create" onClick={() => { setInitialItems(undefined); setFormOpen(true); }} type="primary">
            {t('sales.create')}
          </Button>,
          ] : []),
        ]}
      />

      <SalesOrderFormDrawer initialItems={initialItems} loading={createMutation.isPending} onClose={() => { setFormOpen(false); setInitialItems(undefined); }} onSubmit={saveOrder} open={formOpen} />
      <SalesOrderDrawer orderId={selectedOrderId} onApprove={setApprovalOrder} onClose={closeOrder} onDeleteDraft={deleteDraft} onReasonAction={(action, order) => setReasonAction({ action, order })} onSubmit={submitDraft} />
      <SalesApprovalModal loading={approveMutation.isPending} onCancel={() => setApprovalOrder(undefined)} onConfirm={confirmApproval} open={approvalOrder !== undefined} order={approvalOrder} />
      <ReasonModal
        danger
        description={reasonAction ? t(`sales.confirmations.${reasonAction.action}`) : undefined}
        loading={reasonMutation.isPending}
        onCancel={() => setReasonAction(undefined)}
        onConfirm={confirmReasonAction}
        open={reasonAction !== undefined}
        title={reasonAction ? t(`sales.actions.${reasonAction.action}`) : ''}
      />
      <SalesPolicyModal onClose={() => setPolicyOpen(false)} onSaved={refresh} open={policyOpen} />
    </section>
  );
}
