import { EyeOutlined, PlusOutlined, SettingOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Tabs, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  applyEmergencyCorrection,
  approveEmergencyCorrection,
  createEmergencyCorrection,
  EMERGENCY_CORRECTION_POLICY_QUERY_KEY,
  EMERGENCY_CORRECTIONS_QUERY_KEY,
  getEmergencyCorrectionImpact,
  getEmergencyCorrectionPolicy,
  listEmergencyCorrections,
  rejectEmergencyCorrection,
  reviewEmergencyCorrection,
  submitEmergencyCorrection,
  updateEmergencyCorrectionPolicy,
  type CreateEmergencyCorrectionPayload,
  type EmergencyCorrection,
  type EmergencyCorrectionActionPayload,
  type EmergencyCorrectionImpact,
  type EmergencyCorrectionStatus,
  type UpdateEmergencyCorrectionPolicyPayload,
} from '../../api/emergencyCorrections';
import { getErrorMessage } from '../../api/errors';
import { paginateArray } from '../../api/pagination';
import { useAuth } from '../../auth/AuthProvider';
import { getCorrectionCapabilities } from '../inventoryGovernance/capabilities';
import { formatDateTime, getStatusColor } from '../workflowUtils';
import { EmergencyCorrectionActionModal, type CorrectionAction } from './EmergencyCorrectionActionModal';
import { EmergencyCorrectionApplyModal } from './EmergencyCorrectionApplyModal';
import { EmergencyCorrectionCreateModal } from './EmergencyCorrectionCreateModal';
import { EmergencyCorrectionDrawer } from './EmergencyCorrectionDrawer';
import { EmergencyCorrectionPolicyModal } from './EmergencyCorrectionPolicyModal';
import {
  clearCorrectionIdempotencyKey,
  getStableCorrectionIdempotencyKey,
  hasCorrectionIdempotencyKey,
} from './correctionIdempotency';
import { getCorrectionPreviewState } from './correctionPreview';

interface CorrectionTableParams { current?: number; pageSize?: number; search?: string }
type CorrectionTab = 'ALL' | EmergencyCorrectionStatus;

interface PendingAction {
  action: CorrectionAction;
  correction: EmergencyCorrection;
}

interface PendingApply {
  correction: EmergencyCorrection;
  hasPriorAttempt: boolean;
  impact: EmergencyCorrectionImpact;
}

export function EmergencyCorrectionPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<CorrectionTab>('DRAFT');
  const [createOpen, setCreateOpen] = useState(false);
  const [policyOpen, setPolicyOpen] = useState(false);
  const [selectedCorrection, setSelectedCorrection] = useState<EmergencyCorrection>();
  const [pendingAction, setPendingAction] = useState<PendingAction>();
  const [pendingApply, setPendingApply] = useState<PendingApply>();
  const { session } = useAuth();
  const capabilities = getCorrectionCapabilities(session?.currentRole, session?.permissionCodes);
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const policyQuery = useQuery({
    queryKey: EMERGENCY_CORRECTION_POLICY_QUERY_KEY,
    queryFn: getEmergencyCorrectionPolicy,
  });
  const canConfigurePolicy = session?.currentRole === 'TENANT_ADMIN'
    && policyQuery.data?.approvalEntitled === true
    && policyQuery.data.configurable;

  useEffect(() => { actionRef.current?.reloadAndRest?.(); }, [status]);

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
  const impactMutation = useMutation({ mutationFn: getEmergencyCorrectionImpact });
  const applyMutation = useMutation({
    mutationFn: ({ id, idempotencyKey, previewToken }: { id: number; idempotencyKey: string; previewToken: string }) => (
      applyEmergencyCorrection(id, idempotencyKey, previewToken)
    ),
  });
  const policyMutation = useMutation({ mutationFn: updateEmergencyCorrectionPolicy });

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

  const requestApply = async (correction: EmergencyCorrection): Promise<void> => {
    if (!correction.id) return;
    try {
      const impact = await impactMutation.mutateAsync(correction.id);
      const hasPriorAttempt = hasCorrectionIdempotencyKey(correction.id);
      setPendingApply({
        correction,
        hasPriorAttempt,
        impact,
      });
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const startNewApplyAttempt = async (): Promise<void> => {
    const correction = pendingApply?.correction;
    const id = correction?.id;
    if (!id || !correction) return;
    clearCorrectionIdempotencyKey(id);
    setPendingApply(undefined);
    try {
      const impact = await impactMutation.mutateAsync(id);
      setPendingApply({ correction, hasPriorAttempt: false, impact });
      message.success(t('corrections.messages.newApplyAttempt'));
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const confirmApply = async (): Promise<void> => {
    const id = pendingApply?.correction.id;
    if (!id || !pendingApply.impact.canApply) return;
    const previewState = getCorrectionPreviewState(pendingApply.impact);
    if (previewState !== 'READY') {
      setPendingApply(undefined);
      message.error(t(`corrections.apply.previewStateMessages.${previewState}`));
      return;
    }
    const previewToken = pendingApply.impact.previewToken.trim();
    const idempotencyKey = getStableCorrectionIdempotencyKey(id);
    try {
      const updated = await applyMutation.mutateAsync({ id, idempotencyKey, previewToken });
      clearCorrectionIdempotencyKey(id);
      message.success(t('corrections.messages.apply'));
      setPendingApply(undefined);
      setSelectedCorrection(updated);
      await refresh();
    } catch (error) {
      setPendingApply((current) => current ? { ...current, hasPriorAttempt: true } : current);
      message.error(getErrorMessage(error, t));
    }
  };

  const submitPolicy = async (payload: UpdateEmergencyCorrectionPolicyPayload): Promise<void> => {
    try {
      const updated = await policyMutation.mutateAsync(payload);
      queryClient.setQueryData(EMERGENCY_CORRECTION_POLICY_QUERY_KEY, updated);
      message.success(t('corrections.messages.policyUpdated'));
      setPolicyOpen(false);
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const columns = useMemo<ProColumns<EmergencyCorrection>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('corrections.searchPlaceholder') } },
    { title: t('corrections.fields.correctionNo'), dataIndex: 'correctionNo', width: 190, fixed: 'left', copyable: true, search: false },
    { title: t('corrections.fields.product'), dataIndex: 'productSkuId', width: 220, search: false, render: (_, correction) => correction.productSkuCode ? [correction.productSkuCode, correction.productSkuName].filter(Boolean).join(' · ') : `SKU #${correction.productSkuId ?? '-'}` },
    { title: t('corrections.fields.location'), dataIndex: 'locationId', width: 160, search: false, render: (_, correction) => correction.locationCode ?? `#${correction.locationId ?? '-'}` },
    { title: t('corrections.fields.batchCode'), dataIndex: 'batchCode', width: 220, copyable: true, search: false },
    { title: t('corrections.fields.systemQty'), dataIndex: 'systemQty', width: 100, align: 'right', search: false },
    { title: t('corrections.fields.countedQty'), dataIndex: 'countedQty', width: 100, align: 'right', search: false },
    { title: t('corrections.fields.adjustmentQty'), dataIndex: 'adjustmentQty', width: 105, align: 'right', search: false, render: (_, correction) => { const value = correction.adjustmentQty ?? 0; return <Tag color={value === 0 ? 'default' : value > 0 ? 'success' : 'error'}>{value > 0 ? `+${value}` : value}</Tag>; } },
    { title: t('corrections.fields.status'), dataIndex: 'status', width: 145, search: false, render: (_, correction) => <Tag color={getStatusColor(correction.status)}>{correction.status ? t(`corrections.statuses.${correction.status}`) : '-'}</Tag> },
    { title: t('common.createdAt'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', width: 100, fixed: 'right', render: (_, correction) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelectedCorrection(correction)} size="small" type="link">{t('common.view')}</Button>] },
  ], [i18n.language, t]);

  const tabs: CorrectionTab[] = ['DRAFT', 'PENDING_REVIEW', 'PENDING_APPROVAL', 'APPROVED', 'APPLIED', 'REJECTED', 'ALL'];

  return (
    <section className="data-page workflow-page">
      {policyQuery.isError ? <Alert message={t('corrections.policy.loadFailed')} showIcon type="error" /> : null}
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
            const filtered = keyword ? corrections.filter((correction) => [correction.correctionNo, correction.batchCode, correction.reasonCode, correction.productSkuCode, correction.productSkuName, correction.locationCode, correction.warehouseCode, correction.warehouseName].some((value) => value?.toLowerCase().includes(keyword))) : corrections;
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey="id"
        scroll={{ x: 1480 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          ...(canConfigurePolicy ? [
            <Button icon={<SettingOutlined />} key="policy" onClick={() => setPolicyOpen(true)}>{t('corrections.actions.configurePolicy')}</Button>,
          ] : []),
          ...(capabilities.canAdjust ? [
            <Button icon={<PlusOutlined />} key="create" onClick={() => setCreateOpen(true)} type="primary">{t('corrections.actions.create')}</Button>,
          ] : []),
        ]}
      />

      <EmergencyCorrectionCreateModal loading={createMutation.isPending} onCancel={() => setCreateOpen(false)} onConfirm={submitCreate} open={createOpen} />
      <EmergencyCorrectionDrawer
        canAdjust={capabilities.canAdjust}
        canApprove={capabilities.canApprove}
        canReject={capabilities.canReject}
        canReview={capabilities.canReview}
        correction={selectedCorrection}
        effectiveApprovalMode={policyQuery.data?.effectiveMode}
        onAction={(action, correction) => setPendingAction({ action, correction })}
        onApply={requestApply}
        onClose={() => setSelectedCorrection(undefined)}
        policyLoading={policyQuery.isLoading}
        previewLoading={impactMutation.isPending}
      />
      <EmergencyCorrectionActionModal
        action={pendingAction?.action}
        correction={pendingAction?.correction}
        loading={actionMutation.isPending}
        onCancel={() => setPendingAction(undefined)}
        onConfirm={submitAction}
      />
      <EmergencyCorrectionApplyModal
        correction={pendingApply?.correction}
        hasPriorAttempt={pendingApply?.hasPriorAttempt ?? false}
        impact={pendingApply?.impact}
        loading={applyMutation.isPending}
        onCancel={() => setPendingApply(undefined)}
        onConfirm={confirmApply}
        onStartNewAttempt={startNewApplyAttempt}
      />
      <EmergencyCorrectionPolicyModal
        loading={policyMutation.isPending}
        onCancel={() => setPolicyOpen(false)}
        onConfirm={submitPolicy}
        open={policyOpen}
        policy={policyQuery.data}
      />
    </section>
  );
}
