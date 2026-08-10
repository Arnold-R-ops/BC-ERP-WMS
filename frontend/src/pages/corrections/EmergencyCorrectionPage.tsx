import { EyeOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Tabs, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  applyEmergencyCorrection,
  approveEmergencyCorrection,
  createEmergencyCorrection,
  EMERGENCY_CORRECTIONS_QUERY_KEY,
  listEmergencyCorrections,
  rejectEmergencyCorrection,
  reviewEmergencyCorrection,
  submitEmergencyCorrection,
  type CreateEmergencyCorrectionPayload,
  type EmergencyCorrection,
  type EmergencyCorrectionActionPayload,
  type EmergencyCorrectionStatus,
} from '../../api/emergencyCorrections';
import { getErrorMessage } from '../../api/errors';
import { paginateArray } from '../../api/pagination';
import { listProductSkus, PRODUCT_SKUS_QUERY_KEY } from '../../api/productSkus';
import { useAuth } from '../../auth/AuthProvider';
import { getCorrectionCapabilities } from '../inventoryGovernance/capabilities';
import { formatDateTime, getStatusColor } from '../workflowUtils';
import { EmergencyCorrectionActionModal, type CorrectionAction } from './EmergencyCorrectionActionModal';
import { EmergencyCorrectionCreateModal } from './EmergencyCorrectionCreateModal';
import { EmergencyCorrectionDrawer } from './EmergencyCorrectionDrawer';

interface CorrectionTableParams { current?: number; pageSize?: number; search?: string }
type CorrectionTab = 'ALL' | EmergencyCorrectionStatus;

interface PendingAction {
  action: CorrectionAction;
  correction: EmergencyCorrection;
}

export function EmergencyCorrectionPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<CorrectionTab>('PENDING_REVIEW');
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedCorrection, setSelectedCorrection] = useState<EmergencyCorrection>();
  const [pendingAction, setPendingAction] = useState<PendingAction>();
  const { session } = useAuth();
  const capabilities = getCorrectionCapabilities(session?.currentRole, session?.permissionCodes);
  const { i18n, t } = useTranslation();
  const { message, modal } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const productSkusQuery = useQuery({ queryKey: PRODUCT_SKUS_QUERY_KEY, queryFn: () => listProductSkus() });

  useEffect(() => { actionRef.current?.reloadAndRest?.(); }, [status]);

  const productNames = useMemo(() => new Map(
    (productSkusQuery.data ?? []).filter((sku) => sku.id !== undefined).map((sku) => [sku.id as number, `${sku.skuCode ?? '-'} · ${sku.name ?? sku.skuName ?? '-'}`]),
  ), [productSkusQuery.data]);

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: EMERGENCY_CORRECTIONS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const createMutation = useMutation({ mutationFn: createEmergencyCorrection });
  const actionMutation = useMutation({
    mutationFn: ({ action, id, payload }: { action: CorrectionAction; id: number; payload: EmergencyCorrectionActionPayload }) => {
      if (action === 'submit') return submitEmergencyCorrection(id, payload);
      if (action === 'review') return reviewEmergencyCorrection(id, payload);
      if (action === 'approve') return approveEmergencyCorrection(id, payload);
      return rejectEmergencyCorrection(id, payload);
    },
  });
  const applyMutation = useMutation({ mutationFn: applyEmergencyCorrection });

  const submitCreate = async (payload: CreateEmergencyCorrectionPayload): Promise<void> => {
    try {
      const correction = await createMutation.mutateAsync(payload);
      message.success(t('corrections.messages.created'));
      setCreateOpen(false);
      setStatus('DRAFT');
      setSelectedCorrection(correction);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const submitAction = async (payload: EmergencyCorrectionActionPayload): Promise<void> => {
    if (!pendingAction?.correction.id) return;
    try {
      const updated = await actionMutation.mutateAsync({ action: pendingAction.action, id: pendingAction.correction.id, payload });
      message.success(t(`corrections.messages.${pendingAction.action}`));
      setPendingAction(undefined);
      setSelectedCorrection(updated);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const requestApply = (correction: EmergencyCorrection): void => {
    if (!correction.id) return;
    modal.confirm({
      title: t('corrections.apply.title', { correctionNo: correction.correctionNo ?? '-' }),
      content: t('corrections.apply.notice', { system: correction.systemQty ?? 0, counted: correction.countedQty ?? 0, adjustment: correction.adjustmentQty ?? 0 }),
      okButtonProps: { danger: true },
      okText: t('corrections.actions.apply'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          const updated = await applyMutation.mutateAsync(correction.id as number);
          message.success(t('corrections.messages.apply'));
          setSelectedCorrection(updated);
          await refresh();
        } catch (error) { message.error(getErrorMessage(error, t)); throw error; }
      },
    });
  };

  const columns = useMemo<ProColumns<EmergencyCorrection>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('corrections.searchPlaceholder') } },
    { title: t('corrections.fields.correctionNo'), dataIndex: 'correctionNo', width: 190, fixed: 'left', copyable: true, search: false },
    { title: t('corrections.fields.product'), dataIndex: 'productSkuId', width: 220, search: false, render: (_, correction) => productNames.get(correction.productSkuId ?? -1) ?? `SKU #${correction.productSkuId ?? '-'}` },
    { title: t('corrections.fields.location'), dataIndex: 'locationId', width: 120, search: false, renderText: (value) => `#${value ?? '-'}` },
    { title: t('corrections.fields.batchCode'), dataIndex: 'batchCode', width: 220, copyable: true, search: false },
    { title: t('corrections.fields.systemQty'), dataIndex: 'systemQty', width: 100, align: 'right', search: false },
    { title: t('corrections.fields.countedQty'), dataIndex: 'countedQty', width: 100, align: 'right', search: false },
    { title: t('corrections.fields.adjustmentQty'), dataIndex: 'adjustmentQty', width: 105, align: 'right', search: false, render: (_, correction) => { const value = correction.adjustmentQty ?? 0; return <Tag color={value === 0 ? 'default' : value > 0 ? 'success' : 'error'}>{value > 0 ? `+${value}` : value}</Tag>; } },
    { title: t('corrections.fields.status'), dataIndex: 'status', width: 145, search: false, render: (_, correction) => <Tag color={getStatusColor(correction.status)}>{correction.status ? t(`corrections.statuses.${correction.status}`) : '-'}</Tag> },
    { title: t('common.createdAt'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', width: 100, fixed: 'right', render: (_, correction) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelectedCorrection(correction)} size="small" type="link">{t('common.view')}</Button>] },
  ], [i18n.language, productNames, t]);

  const tabs: CorrectionTab[] = ['DRAFT', 'PENDING_REVIEW', 'PENDING_APPROVAL', 'APPROVED', 'APPLIED', 'REJECTED', 'ALL'];

  return (
    <section className="data-page workflow-page">
      <Tabs activeKey={status} items={tabs.map((value) => ({ key: value, label: t(`corrections.tabs.${value}`) }))} onChange={(key) => setStatus(key as CorrectionTab)} />
      <ProTable<EmergencyCorrection, CorrectionTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('corrections.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const corrections = await listEmergencyCorrections(status === 'ALL' ? undefined : status);
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword ? corrections.filter((correction) => [correction.correctionNo, correction.batchCode, correction.reasonCode, productNames.get(correction.productSkuId ?? -1)].some((value) => value?.toLowerCase().includes(keyword))) : corrections;
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey="id"
        scroll={{ x: 1480 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => capabilities.canAdjust ? [
          <Button icon={<PlusOutlined />} key="create" onClick={() => setCreateOpen(true)} type="primary">{t('corrections.actions.create')}</Button>,
        ] : []}
      />

      <EmergencyCorrectionCreateModal loading={createMutation.isPending} onCancel={() => setCreateOpen(false)} onConfirm={submitCreate} open={createOpen} />
      <EmergencyCorrectionDrawer
        canAdjust={capabilities.canAdjust}
        canApprove={capabilities.canApprove}
        canReject={capabilities.canReject}
        canReview={capabilities.canReview}
        correction={selectedCorrection}
        onAction={(action, correction) => setPendingAction({ action, correction })}
        onApply={requestApply}
        onClose={() => setSelectedCorrection(undefined)}
      />
      <EmergencyCorrectionActionModal
        action={pendingAction?.action}
        correction={pendingAction?.correction}
        loading={actionMutation.isPending}
        onCancel={() => setPendingAction(undefined)}
        onConfirm={submitAction}
      />
    </section>
  );
}
