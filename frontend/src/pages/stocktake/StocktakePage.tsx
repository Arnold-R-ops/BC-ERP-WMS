import { EyeOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Progress, Tabs, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { listActiveWarehouses } from '../../api/masterData';
import { paginateArray } from '../../api/pagination';
import {
  createStocktakeTask,
  reviewStocktake,
  startStocktake,
  STOCKTAKE_TASKS_QUERY_KEY,
  submitStocktakeCount,
  listStocktakeTasks,
  type CreateStocktakePayload,
  type ReviewStocktakePayload,
  type StocktakeItem,
  type StocktakeStatus,
  type StocktakeTask,
  type SubmitCountPayload,
} from '../../api/stocktake';
import { useAuth } from '../../auth/AuthProvider';
import { getStocktakeCapabilities } from '../inventoryGovernance/capabilities';
import { formatDateTime, getStatusColor } from '../workflowUtils';
import { StocktakeCountModal } from './StocktakeCountModal';
import { StocktakeCreateModal } from './StocktakeCreateModal';
import { StocktakeReviewModal } from './StocktakeReviewModal';
import { StocktakeTaskDrawer } from './StocktakeTaskDrawer';

interface StocktakeTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  warehouseId?: number;
}

type StocktakeTab = 'ALL' | StocktakeStatus;

export function StocktakePage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<StocktakeTab>('COUNTING');
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<number>();
  const [countItem, setCountItem] = useState<StocktakeItem>();
  const [reviewTask, setReviewTask] = useState<StocktakeTask>();
  const { session } = useAuth();
  const capabilities = getStocktakeCapabilities(session?.currentRole, session?.permissionCodes);
  const { i18n, t } = useTranslation();
  const { message, modal } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const warehousesQuery = useQuery({ queryKey: ['active-warehouses'], queryFn: listActiveWarehouses });

  useEffect(() => { actionRef.current?.reloadAndRest?.(); }, [status]);

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: STOCKTAKE_TASKS_QUERY_KEY });
    await queryClient.invalidateQueries({ queryKey: ['stocktake-task'] });
    await queryClient.invalidateQueries({ queryKey: ['stocktake-items'] });
    actionRef.current?.reload();
  };

  const createMutation = useMutation({ mutationFn: createStocktakeTask });
  const startMutation = useMutation({ mutationFn: startStocktake });
  const countMutation = useMutation({
    mutationFn: ({ taskId, itemId, payload }: { taskId: number; itemId: number; payload: SubmitCountPayload }) =>
      submitStocktakeCount(taskId, itemId, payload),
  });
  const reviewMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ReviewStocktakePayload }) => reviewStocktake(id, payload),
  });

  const submitCreate = async (payload: CreateStocktakePayload): Promise<void> => {
    try {
      const task = await createMutation.mutateAsync(payload);
      message.success(t('stocktake.messages.created'));
      setCreateOpen(false);
      setStatus('CREATED');
      setSelectedTaskId(task.id);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const requestStart = (task: StocktakeTask): void => {
    if (!task.id) return;
    modal.confirm({
      title: t('stocktake.start.title'),
      content: t('stocktake.start.notice'),
      okText: t('stocktake.actions.start'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await startMutation.mutateAsync(task.id as number);
          message.success(t('stocktake.messages.started'));
          await refresh();
        } catch (error) { message.error(getErrorMessage(error, t)); throw error; }
      },
    });
  };

  const submitCount = async (payload: SubmitCountPayload): Promise<void> => {
    if (!countItem?.taskId || !countItem.id) return;
    try {
      await countMutation.mutateAsync({ taskId: countItem.taskId, itemId: countItem.id, payload });
      message.success(t('stocktake.messages.countSaved'));
      setCountItem(undefined);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const submitReview = async (payload: ReviewStocktakePayload): Promise<void> => {
    if (!reviewTask?.id) return;
    try {
      await reviewMutation.mutateAsync({ id: reviewTask.id, payload });
      message.success(t(payload.approved ? 'stocktake.messages.approved' : 'stocktake.messages.rejected'));
      setReviewTask(undefined);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const warehouseOptions = (warehousesQuery.data ?? [])
    .filter((warehouse) => warehouse.id !== undefined)
    .map((warehouse) => ({ label: warehouse.name ?? warehouse.code ?? String(warehouse.id), value: warehouse.id }));

  const columns = useMemo<ProColumns<StocktakeTask>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('stocktake.searchPlaceholder') } },
    { title: t('stocktake.fields.warehouse'), dataIndex: 'warehouseId', hideInTable: true, valueType: 'select', fieldProps: { options: warehouseOptions, showSearch: true, optionFilterProp: 'label' } },
    { title: t('stocktake.fields.taskNo'), dataIndex: 'taskNo', fixed: 'left', width: 180, copyable: true, search: false },
    { title: t('stocktake.fields.warehouse'), dataIndex: 'warehouseName', width: 220, search: false },
    { title: t('stocktake.fields.cycleType'), dataIndex: 'cycleType', width: 125, search: false, render: (_, task) => task.cycleTypeDescription ?? task.cycleType ?? '-' },
    { title: t('stocktake.fields.status'), dataIndex: 'status', width: 120, search: false, render: (_, task) => <Tag color={getStatusColor(task.status)}>{task.statusDescription ?? task.status ?? '-'}</Tag> },
    { title: t('stocktake.fields.progress'), dataIndex: 'progress', width: 180, search: false, render: (_, task) => <Progress percent={task.progress ?? 0} size="small" /> },
    { title: t('stocktake.fields.itemProgress'), dataIndex: 'countedItems', width: 120, align: 'right', search: false, render: (_, task) => `${task.countedItems ?? 0} / ${task.totalItems ?? 0}` },
    { title: t('stocktake.fields.differenceItems'), dataIndex: 'differenceItems', width: 110, align: 'right', search: false, render: (_, task) => <strong className={(task.differenceItems ?? 0) > 0 ? 'quantity-danger' : 'quantity-ok'}>{task.differenceItems ?? 0}</strong> },
    { title: t('stocktake.fields.snapshotTime'), dataIndex: 'snapshotTime', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', fixed: 'right', width: 100, render: (_, task) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelectedTaskId(task.id)} size="small" type="link">{t('common.view')}</Button>] },
  ], [i18n.language, t, warehouseOptions]);

  const tabs: StocktakeTab[] = ['CREATED', 'COUNTING', 'REVIEWING', 'COMPLETED', 'ALL'];

  return (
    <section className="data-page workflow-page">
      <Tabs activeKey={status} items={tabs.map((value) => ({ key: value, label: t(`stocktake.tabs.${value}`) }))} onChange={(key) => setStatus(key as StocktakeTab)} />
      <ProTable<StocktakeTask, StocktakeTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('stocktake.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const tasks = await listStocktakeTasks({
              status: status === 'ALL' ? undefined : status,
              warehouseId: params.warehouseId ? Number(params.warehouseId) : undefined,
            });
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword
              ? tasks.filter((task) => [task.taskNo, task.warehouseName, task.createdByName].some((value) => value?.toLowerCase().includes(keyword)))
              : tasks;
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey="id"
        scroll={{ x: 1415 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => capabilities.canCreate ? [
          <Button icon={<PlusOutlined />} key="create" onClick={() => setCreateOpen(true)} type="primary">{t('stocktake.actions.create')}</Button>,
        ] : []}
      />

      <StocktakeCreateModal loading={createMutation.isPending} onCancel={() => setCreateOpen(false)} onConfirm={submitCreate} open={createOpen} />
      <StocktakeTaskDrawer
        canCount={capabilities.canCount}
        canReview={capabilities.canReview}
        onClose={() => setSelectedTaskId(undefined)}
        onCount={setCountItem}
        onReview={setReviewTask}
        onStart={requestStart}
        open={selectedTaskId !== undefined}
        taskId={selectedTaskId}
      />
      <StocktakeCountModal item={countItem} loading={countMutation.isPending} onCancel={() => setCountItem(undefined)} onConfirm={submitCount} open={countItem !== undefined} />
      <StocktakeReviewModal loading={reviewMutation.isPending} onCancel={() => setReviewTask(undefined)} onConfirm={submitReview} open={reviewTask !== undefined} task={reviewTask} />
    </section>
  );
}
