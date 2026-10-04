import { CalendarOutlined, CheckCircleFilled, EditOutlined, InboxOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntdApp, Button, Checkbox, Empty, Form, Input, InputNumber, Modal, Progress, Select, Skeleton, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { hasPermission } from '../../access';
import { getErrorMessage } from '../../api/errors';
import {
  createReceiptIdempotencyKey,
  getReceivingTask,
  listReceivingPlans,
  listReceivingTaskLocations,
  listReceivingTasks,
  RECEIVING_PLANS_QUERY_KEY,
  RECEIVING_TASKS_QUERY_KEY,
  submitReceivingReceipt,
  updateReceivingPlanOperations,
  type ReceivingPlan,
  type ReceivingPlanOperations,
  type ReceivingTaskItem,
  type SubmitReceivingReceiptRequest,
} from '../../api/receiving';
import { useAuth } from '../../auth/AuthProvider';
import {
  inspectionIdempotencyKey, recordArrivalException,
  type ArrivalDisposition, type ArrivalExceptionType,
} from '../../api/receivingInspection';
import { ReceivingPlanOperationsModal } from './ReceivingPlanOperationsModal';
import { ScanInput } from './ScanInput';
import { WarehouseOperationSteps } from './WarehouseOperationSteps';
import { useMobileOperation } from './WarehouseMobileLayout';

interface ReceiptDraft {
  selected: boolean;
  quantity: number;
  locationId?: number;
  productionDate?: string;
  expiryDate?: string;
  externalBatchCode?: string;
  productVerified: boolean;
}

interface IdempotencyAttempt { fingerprint: string; key: string }
type ReceivingView = 'plans' | 'tasks';

function formatPlanDateTime(value: string | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

interface ReceivingPlanCardProps {
  canEdit: boolean;
  onEdit: (plan: ReceivingPlan) => void;
  plan: ReceivingPlan;
}

function ReceivingPlanCard({ canEdit, onEdit, plan }: ReceivingPlanCardProps): JSX.Element {
  const { t } = useTranslation();
  const items = plan.items ?? [];
  const totalQuantity = items.reduce((sum, item) => sum + item.plannedQuantity, 0);
  const warehouse = plan.warehouseName ?? plan.warehouseCode ?? `#${plan.warehouseId}`;

  return (
    <article className="receiving-plan-card">
      <header className="receiving-plan-card-heading">
        <div>
          <strong>{plan.planNumber}</strong>
          <span>{t('mobile.receiving.plan.purchaseOrder')}: {plan.purchaseOrderNumber ?? `#${plan.purchaseOrderId}`}</span>
        </div>
        <Tag color={plan.status === 'WAITING_SHIPMENT' ? 'gold' : plan.status === 'IN_TRANSIT' ? 'blue' : 'cyan'}>
          {t(`mobile.receiving.plan.status.${plan.status}`, { defaultValue: plan.status })}
        </Tag>
      </header>

      <dl className="receiving-plan-metadata">
        <div><dt>{t('mobile.receiving.plan.warehouse')}</dt><dd>{warehouse}</dd></div>
        <div><dt>{t('mobile.receiving.plan.priority')}</dt><dd>{plan.priority ?? '-'}</dd></div>
        <div><dt>{t('mobile.receiving.plan.window')}</dt><dd><CalendarOutlined /> {formatPlanDateTime(plan.expectedWindowStart)} — {formatPlanDateTime(plan.expectedWindowEnd)}</dd></div>
        <div><dt>{t('mobile.receiving.plan.receiver')}</dt><dd>{plan.assignedReceiverName ?? '-'}</dd></div>
        <div><dt>{t('mobile.receiving.plan.team')}</dt><dd>{plan.assignedTeam ?? '-'}</dd></div>
        <div><dt>{t('mobile.receiving.plan.dockAndArea')}</dt><dd>{[plan.dockArea, plan.workArea].filter(Boolean).join(' / ') || '-'}</dd></div>
      </dl>

      <section className="receiving-plan-lines">
        <div className="receiving-plan-lines-heading">
          <strong>{t('mobile.receiving.plan.lines')}</strong>
          <span>{items.length === 0 ? t('mobile.receiving.plan.linesPending') : t('mobile.receiving.plan.totalQuantity', { count: totalQuantity })}</span>
        </div>
        {items.length === 0 ? (
          <div className="receiving-plan-lines-empty">{t('mobile.receiving.plan.linesUnavailable')}</div>
        ) : items.map((item, index) => (
          <div className="receiving-plan-line" key={`${item.productSkuId ?? item.skuCode ?? 'line'}-${index}`}>
            <div><strong>{item.productName ?? '-'}</strong><span>{item.skuCode ?? `#${item.productSkuId ?? '-'}`}</span></div>
            <b>{t('mobile.receiving.plan.plannedQuantity', { count: item.plannedQuantity })}</b>
          </div>
        ))}
      </section>

      <div className="receiving-plan-notes">
        <span>{t('mobile.receiving.plan.notes')}</span>
        <p>{plan.warehouseNotes || '-'}</p>
      </div>
      {canEdit ? (
        <Button aria-label={t('mobile.receiving.plan.edit')} block icon={<EditOutlined />} onClick={() => onEdit(plan)}>
          {t('mobile.receiving.plan.edit')}
        </Button>
      ) : null}
    </article>
  );
}

export function MobileReceivingPage({ embeddedTaskId, onReceiptSaved }: {
  embeddedTaskId?: number; onReceiptSaved?: () => Promise<void>;
} = {}): JSX.Element {
  const [searchParams] = useSearchParams();
  const queryTaskId = embeddedTaskId ?? (Number(searchParams.get('taskId')) || undefined);
  const [view, setView] = useState<ReceivingView>(queryTaskId ? 'tasks' : 'plans');
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>(queryTaskId);
  const [editingPlan, setEditingPlan] = useState<ReceivingPlan>();
  const [activeItemId, setActiveItemId] = useState<number>();
  const [drafts, setDrafts] = useState<Record<number, ReceiptDraft>>({});
  const [exceptionOpen, setExceptionOpen] = useState(false);
  const [exceptionForm] = Form.useForm<{
    productSkuId?: number;
    exceptionType: ArrivalExceptionType;
    disposition: ArrivalDisposition;
    observedQuantity: number;
    reasonDetail?: string;
  }>();
  const idempotencyAttempt = useRef<IdempotencyAttempt>();
  const { session } = useAuth();
  const operation = useMobileOperation();
  const [browserOnline, setBrowserOnline] = useState(() => navigator.onLine);
  useEffect(() => {
    const update = (): void => setBrowserOnline(navigator.onLine);
    window.addEventListener('online', update); window.addEventListener('offline', update);
    return () => { window.removeEventListener('online', update); window.removeEventListener('offline', update); };
  }, []);
  const online = operation.online && browserOnline;
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();
  const canEditPlans = hasPermission(session?.currentRole, session?.permissionCodes, 'receiving:plan:edit');

  const plansQuery = useQuery({
    queryKey: RECEIVING_PLANS_QUERY_KEY,
    queryFn: () => listReceivingPlans(['WAITING_SHIPMENT', 'IN_TRANSIT', 'PARTIALLY_RECEIVED']),
    enabled: view === 'plans',
  });
  const plans = (plansQuery.data ?? []).filter((plan) => (
    plan.status === 'WAITING_SHIPMENT' || plan.status === 'IN_TRANSIT' || plan.status === 'PARTIALLY_RECEIVED'
  ));
  const tasksQuery = useQuery({
    queryKey: RECEIVING_TASKS_QUERY_KEY,
    queryFn: () => listReceivingTasks(),
    enabled: view === 'tasks' && embeddedTaskId === undefined,
  });
  const taskQuery = useQuery({
    queryKey: ['receiving-task', selectedTaskId],
    queryFn: () => getReceivingTask(selectedTaskId as number),
    enabled: view === 'tasks' && selectedTaskId !== undefined,
  });
  const selectedTask = taskQuery.data;
  const tasks = (embeddedTaskId !== undefined ? selectedTask ? [selectedTask] : [] : tasksQuery.data ?? [])
    .filter((task) => task.status === 'PENDING' || task.status === 'PARTIALLY_RECEIVED');
  const locationsQuery = useQuery({
    queryKey: ['receiving-task-locations', selectedTaskId],
    queryFn: () => listReceivingTaskLocations(selectedTaskId as number),
    enabled: view === 'tasks' && selectedTaskId !== undefined,
  });
  const taskFingerprint = selectedTask?.items.map((item) => `${item.id}:${item.remainingQuantity}`).join('|');

  useEffect(() => {
    if (embeddedTaskId === undefined && selectedTaskId !== undefined && !tasks.some((task) => task.id === selectedTaskId)
        && !tasksQuery.isLoading && !taskQuery.isLoading) {
      setSelectedTaskId(undefined);
    }
  }, [embeddedTaskId, selectedTaskId, taskQuery.isLoading, tasks, tasksQuery.isLoading]);

  useEffect(() => {
    if (!selectedTask) {
      setDrafts({});
      setActiveItemId(undefined);
      return;
    }
    setDrafts(Object.fromEntries(selectedTask.items
      .filter((item) => item.remainingQuantity > 0)
      .map((item) => [item.id, {
        selected: false,
        quantity: item.remainingQuantity,
        productionDate: item.productionDate,
        expiryDate: item.expiryDate,
        externalBatchCode: item.externalBatchCode,
        productVerified: false,
      }])));
    setActiveItemId(undefined);
    idempotencyAttempt.current = undefined;
  }, [selectedTask?.id, taskFingerprint]);

  const receiptMutation = useMutation({
    mutationFn: ({ id, key, payload }: { id: number; key: string; payload: SubmitReceivingReceiptRequest }) => submitReceivingReceipt(id, payload, key),
  });
  const planMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ReceivingPlanOperations }) => updateReceivingPlanOperations(id, payload),
  });
  const exceptionMutation = useMutation({
    mutationFn: (payload: {
      productSkuId?: number;
      exceptionType: ArrivalExceptionType;
      disposition: ArrivalDisposition;
      observedQuantity: number;
      reasonDetail?: string;
    }) => recordArrivalException(
      selectedTaskId as number, payload, inspectionIdempotencyKey('arrival-exception')),
  });
  const selectedLines = (selectedTask?.items ?? []).filter((item) => drafts[item.id]?.selected);
  const selectedCount = selectedLines.length;
  const totalCount = selectedTask?.items.filter((item) => item.remainingQuantity > 0).length ?? 0;
  const readyCount = selectedLines.filter((item) => {
    const draft = drafts[item.id];
    const expiryManaged = item.expiryManagementEnabled;
    return draft.locationId && draft.quantity > 0 && draft.quantity <= item.remainingQuantity
      && (!expiryManaged || (draft.productionDate && draft.expiryDate));
  }).length;
  const readyPercent = selectedCount === 0 ? 0 : Math.round((readyCount / selectedCount) * 100);

  const taskOptions = tasks.map((task) => ({
    label: `${task.taskNumber} · ${task.warehouseName ?? task.warehouseCode ?? '-'}`,
    value: task.id,
  }));
  const locationOptions = (locationsQuery.data ?? [])
    .filter((location): location is typeof location & { id: number } => location.id !== undefined)
    .map((location) => ({ label: location.name ? `${location.code} · ${location.name}` : location.code, value: location.id }));

  const patchDraft = (itemId: number, patch: Partial<ReceiptDraft>): void => {
    setDrafts((current) => ({ ...current, [itemId]: { ...current[itemId], ...patch } }));
  };

  const scanProduct = (code: string): void => {
    const normalized = code.trim().toLowerCase();
    const item = (selectedTask?.items ?? []).find((candidate) => candidate.remainingQuantity > 0 && [candidate.barcode, candidate.skuCode, candidate.externalBatchCode]
      .some((value) => value?.toLowerCase() === normalized));
    if (!item) {
      message.error(t('mobile.receiving.scanNotFound'));
      return;
    }
    setActiveItemId(item.id);
    patchDraft(item.id, { productVerified: true, selected: true });
    message.success(t('mobile.receiving.productVerified', { name: item.productName ?? '-' }));
  };

  const scanLocation = (code: string): void => {
    const location = (locationsQuery.data ?? []).find((candidate) => candidate.code?.toLowerCase() === code.trim().toLowerCase());
    if (!activeItemId || !location?.id) {
      message.error(t('mobile.receiving.locationNotFound'));
      return;
    }
    patchDraft(activeItemId, { locationId: location.id, selected: true });
    message.success(t('mobile.receiving.locationVerified'));
  };

  const buildPayload = (): SubmitReceivingReceiptRequest | undefined => {
    if (selectedLines.length === 0 || readyCount !== selectedCount) return undefined;
    return {
      lines: selectedLines.map((item) => {
        const draft = drafts[item.id];
        return {
          receivingTaskItemId: item.id,
          locationId: draft.locationId as number,
          quantity: draft.quantity,
          productionDate: draft.productionDate || undefined,
          expiryDate: draft.expiryDate || undefined,
          externalBatchCode: draft.externalBatchCode || undefined,
        };
      }),
    };
  };

  const submit = async (): Promise<void> => {
    if (!selectedTask?.id || !online) return;
    const payload = buildPayload();
    if (!payload) {
      message.warning(selectedCount === 0 ? t('mobile.receiving.selectAtLeastOne') : t('mobile.receiving.completeSelected'));
      return;
    }
    const fingerprint = JSON.stringify(payload);
    if (idempotencyAttempt.current?.fingerprint !== fingerprint) {
      idempotencyAttempt.current = { fingerprint, key: createReceiptIdempotencyKey() };
    }
    try {
      await receiptMutation.mutateAsync({ id: selectedTask.id, key: idempotencyAttempt.current.key, payload });
      idempotencyAttempt.current = undefined;
      message.success(t('mobile.receiving.submitted', { count: payload.lines.length }));
      await Promise.all([taskQuery.refetch(), ...(embeddedTaskId === undefined ? [tasksQuery.refetch()] : [])]);
      await onReceiptSaved?.();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const savePlanOperations = async (id: number, payload: ReceivingPlanOperations): Promise<void> => {
    if (!online || !canEditPlans) return;
    try {
      await planMutation.mutateAsync({ id, payload });
      message.success(t('mobile.receiving.planEditor.saved'));
      setEditingPlan(undefined);
      await plansQuery.refetch();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const refresh = async (): Promise<void> => {
    if (view === 'plans') {
      await plansQuery.refetch();
      return;
    }
    const requests: Array<Promise<unknown>> = embeddedTaskId === undefined ? [tasksQuery.refetch()] : [];
    if (selectedTaskId !== undefined) requests.push(taskQuery.refetch(), locationsQuery.refetch());
    await Promise.all(requests);
  };

  return (
    <section className="mobile-operation-page">
      <header className="mobile-operation-heading">
        <div><Typography.Title level={2}>{t('mobile.receiving.title')}</Typography.Title><p>{t('mobile.receiving.subtitle')}</p></div>
        <Button
          aria-label={t('common.refresh')}
          disabled={!online}
          icon={<ReloadOutlined spin={view === 'plans' ? plansQuery.isFetching : tasksQuery.isFetching || taskQuery.isFetching} />}
          onClick={() => void refresh()}
        />
      </header>
      {embeddedTaskId === undefined && <div aria-label={t('mobile.receiving.viewSwitcher')} className="mobile-receiving-switch" role="group">
        <Button aria-pressed={view === 'plans'} onClick={() => setView('plans')} type={view === 'plans' ? 'primary' : 'default'}>
          {t('mobile.receiving.planTab')}
        </Button>
        <Button aria-pressed={view === 'tasks'} onClick={() => setView('tasks')} type={view === 'tasks' ? 'primary' : 'default'}>
          {t('mobile.receiving.taskTab')}
        </Button>
      </div>}

      {view === 'plans' ? (
        plansQuery.isLoading ? <Skeleton active /> : plans.length === 0 ? (
          <Empty description={t('mobile.receiving.plan.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <div className="receiving-plan-list">
            {plans.map((plan) => <ReceivingPlanCard canEdit={canEditPlans} key={plan.id} onEdit={setEditingPlan} plan={plan} />)}
          </div>
        )
      ) : (embeddedTaskId !== undefined ? taskQuery.isLoading : tasksQuery.isLoading) ? <Skeleton active /> : tasks.length === 0 ? (
        <Empty description={t('mobile.receiving.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <>
          <WarehouseOperationSteps current={!selectedTask ? 0 : selectedCount > 0 && readyCount === selectedCount ? 2 : 1} />
          <div className="mobile-operation-workspace">
            <section className="mobile-operation-panel">
              <label className="mobile-field-label">
                <span>{t('mobile.receiving.task')}</span>
                <Select disabled={embeddedTaskId !== undefined} allowClear={embeddedTaskId === undefined} onChange={setSelectedTaskId} optionFilterProp="label" options={taskOptions} placeholder={t('mobile.receiving.selectTask')} showSearch value={selectedTaskId} />
              </label>
              {!selectedTask ? <div className="mobile-selection-hint">{t('mobile.receiving.selectTaskHint')}</div> : (
                <>
                  <div className="mobile-task-summary"><strong>{selectedTask.taskNumber}</strong><span>{selectedTask.warehouseName ?? selectedTask.warehouseCode ?? '-'}</span></div>
                  <div className="mobile-progress-band"><span>{t('mobile.receiving.selectionProgress', { ready: readyCount, selected: selectedCount, total: totalCount })}</span><Progress percent={readyPercent} showInfo={false} /></div>
                  <ScanInput disabled={!online} label={t('mobile.receiving.scanProduct')} onScan={scanProduct} placeholder={t('mobile.receiving.scanProductPlaceholder')} />
                  <ScanInput disabled={!activeItemId || !online} label={t('mobile.receiving.scanLocation')} onScan={scanLocation} placeholder={t('mobile.receiving.scanLocationPlaceholder')} />
                  <Button block danger icon={<WarningOutlined />} disabled={!online}
                    onClick={() => {
                      exceptionForm.setFieldsValue({
                        exceptionType: 'DAMAGED', disposition: 'REJECTED_AT_DOCK',
                        observedQuantity: 1,
                      });
                      setExceptionOpen(true);
                    }}>{t('mobile.receiving.arrivalException')}</Button>
                  <Button block disabled={!online || selectedCount === 0 || readyCount !== selectedCount} loading={receiptMutation.isPending} onClick={() => void submit()} size="large" type="primary">{t('mobile.receiving.submitSelected')}</Button>
                </>
              )}
            </section>

            <aside className="mobile-work-queue">
              <div className="mobile-work-queue-heading"><strong>{t('mobile.workQueue')}</strong><span>{selectedTask ? t('mobile.receiving.remainingLines', { count: totalCount }) : t('mobile.selectTaskFirst')}</span></div>
              {!selectedTask ? <Empty description={t('mobile.selectTaskFirst')} image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
                <div className="mobile-task-list receiving-task-list">
                  {selectedTask.items.filter((item) => item.remainingQuantity > 0).map((item: ReceivingTaskItem) => {
                    const draft = drafts[item.id];
                    const active = item.id === activeItemId;
                    const expiryManaged = item.expiryManagementEnabled;
                    const ready = draft?.selected && Boolean(draft.locationId) && draft.quantity > 0 && draft.quantity <= item.remainingQuantity && (!expiryManaged || Boolean(draft.productionDate && draft.expiryDate));
                    return (
                      <article className={`mobile-task-card receiving-task-card${active ? ' is-active' : ''}${ready ? ' is-ready' : ''}`} key={item.id} onClick={() => setActiveItemId(item.id)}>
                        <div className="mobile-task-card-top">
                          <Checkbox checked={draft?.selected} onClick={(event) => event.stopPropagation()} onChange={(event) => patchDraft(item.id, { selected: event.target.checked })}>{item.productName ?? '-'}</Checkbox>
                          {ready ? <CheckCircleFilled /> : <Tag>{t('mobile.receiving.notReady')}</Tag>}
                        </div>
                        <span>{item.skuCode ?? item.barcode ?? '-'}</span>
                        {item.inspectionRequirement && item.inspectionRequirement !== 'EXEMPT'
                          ? <Tag color={item.inspectionRequirement === 'REQUIRED' ? 'blue' : 'default'}>
                            {t('mobile.receiving.inspectionRequirement.' + item.inspectionRequirement)}
                          </Tag> : null}
                        <div className="mobile-task-primary"><InboxOutlined /> {t('mobile.receiving.remaining')} {item.remainingQuantity}</div>
                        <div className="receiving-line-fields" onClick={(event) => event.stopPropagation()}>
                          <label><span>{t('mobile.receiving.thisQuantity')}</span><InputNumber max={item.remainingQuantity} min={1} onChange={(value) => patchDraft(item.id, { quantity: Number(value ?? 0), selected: true })} precision={0} value={draft?.quantity} /></label>
                          <label><span>{t('purchasing.fields.location')}</span><Select onChange={(value) => patchDraft(item.id, { locationId: value, selected: true })} optionFilterProp="label" options={locationOptions} showSearch value={draft?.locationId} /></label>
                          <label><span>{t('purchasing.fields.externalBatchCode')}</span><Input maxLength={100} onChange={(event) => patchDraft(item.id, { externalBatchCode: event.target.value })} value={draft?.externalBatchCode} /></label>
                          <label><span>{t('purchasing.fields.productionDate')}{expiryManaged ? ' *' : ''}</span><Input onChange={(event) => patchDraft(item.id, { productionDate: event.target.value })} type="date" value={draft?.productionDate} /></label>
                          <label><span>{t('purchasing.fields.expiryDate')}{expiryManaged ? ' *' : ''}</span><Input onChange={(event) => patchDraft(item.id, { expiryDate: event.target.value })} type="date" value={draft?.expiryDate} /></label>
                        </div>
                      </article>
                    );
                  })}
                </div>
              )}
            </aside>
          </div>
        </>
      )}
      <ReceivingPlanOperationsModal
        loading={planMutation.isPending}
        onCancel={() => setEditingPlan(undefined)}
        onSubmit={savePlanOperations}
        open={editingPlan !== undefined}
        plan={editingPlan}
      />
      <Modal open={exceptionOpen} title={t('mobile.receiving.arrivalException')}
        confirmLoading={exceptionMutation.isPending}
        onCancel={() => setExceptionOpen(false)}
        onOk={() => void exceptionForm.validateFields().then(async (values) => {
          await exceptionMutation.mutateAsync(values);
          message.success(t('mobile.receiving.arrivalExceptionRecorded'));
          setExceptionOpen(false);
        }).catch((error) => {
          if (error instanceof Error) message.error(getErrorMessage(error, t));
        })}>
        <Form form={exceptionForm} layout="vertical">
          <Form.Item label={t('mobile.receiving.exceptionSku')} name="productSkuId">
            <Select allowClear options={(selectedTask?.items ?? []).map((item) => ({
              value: item.productSkuId, label: (item.skuCode ?? '-') + ' · ' + (item.productName ?? '-'),
            }))} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.exceptionType')} name="exceptionType"
            rules={[{ required: true }]}>
            <Select options={['SHORT_RECEIPT', 'OVER_RECEIPT', 'WRONG_ITEM', 'DAMAGED', 'EXPIRY_FAILED', 'OTHER']
              .map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.exceptionDisposition')} name="disposition"
            rules={[{ required: true }]}>
            <Select options={['AWAITING_REDELIVERY', 'REJECTED_AT_DOCK', 'RECEIVED_FOR_INSPECTION', 'RECORDED_ONLY']
              .map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.exceptionQuantity')} name="observedQuantity"
            rules={[{ required: true }]}>
            <InputNumber min={1} precision={0} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.exceptionReason')} name="reasonDetail">
            <Input.TextArea maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}
