import { AuditOutlined, CheckCircleFilled, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntdApp, Button, Empty, Input, InputNumber, Progress, Select, Skeleton, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  finishStocktake,
  listStocktakeItems,
  listStocktakeTasks,
  STOCKTAKE_TASKS_QUERY_KEY,
  submitStocktakeCount,
  type StocktakeItem,
  type SubmitCountPayload,
} from '../../api/stocktake';
import { ScanInput } from './ScanInput';
import { resolveStocktakeScan } from './stocktakeScan';
import { WarehouseOperationSteps } from './WarehouseOperationSteps';
import { useMobileOperation } from './WarehouseMobileLayout';

export function MobileStocktakePage(): JSX.Element {
  const [selectedTaskId, setSelectedTaskId] = useState<number>();
  const [selectedItem, setSelectedItem] = useState<StocktakeItem>();
  const [candidateIds, setCandidateIds] = useState<number[]>([]);
  const [countedQty, setCountedQty] = useState<number>();
  const [remark, setRemark] = useState('');
  const { online } = useMobileOperation();
  const { message, modal } = AntdApp.useApp();
  const { t } = useTranslation();

  const tasksQuery = useQuery({
    queryKey: [...STOCKTAKE_TASKS_QUERY_KEY, 'COUNTING', 'mobile'],
    queryFn: () => listStocktakeTasks({ status: 'COUNTING' }),
  });
  const tasks = tasksQuery.data ?? [];
  const selectedTask = tasks.find((task) => task.id === selectedTaskId);

  useEffect(() => {
    if (selectedTaskId !== undefined && !selectedTask) setSelectedTaskId(undefined);
  }, [selectedTask, selectedTaskId]);

  const itemsQuery = useQuery({
    queryKey: ['stocktake-items', selectedTaskId, 'mobile'],
    queryFn: () => listStocktakeItems(selectedTaskId as number),
    enabled: selectedTaskId !== undefined,
  });
  const items = itemsQuery.data ?? [];
  const countedCount = items.filter((item) => item.isCounted).length;
  const totalCount = items.length;
  const complete = totalCount > 0 && countedCount === totalCount;

  useEffect(() => {
    setSelectedItem(undefined);
    setCandidateIds([]);
    setCountedQty(undefined);
    setRemark('');
  }, [selectedTaskId]);

  const taskOptions = useMemo(() => [...tasks]
    .sort((left, right) => (right.id ?? 0) - (left.id ?? 0))
    .filter((task) => task.id !== undefined)
    .map((task) => ({
      label: `${task.taskNo ?? `#${task.id}`} / ${task.warehouseName ?? '-'}`,
      value: task.id as number,
    })), [tasks]);

  const countMutation = useMutation({
    mutationFn: ({ taskId, itemId, payload }: { taskId: number; itemId: number; payload: SubmitCountPayload }) =>
      submitStocktakeCount(taskId, itemId, payload),
  });
  const finishMutation = useMutation({ mutationFn: finishStocktake });

  const scan = (code: string): void => {
    const result = resolveStocktakeScan(items, code, candidateIds);
    if (result.kind === 'none') {
      message.error(t('mobile.stocktake.scanNotFound'));
      return;
    }
    if (result.kind === 'ambiguous') {
      setCandidateIds(result.candidateIds);
      message.warning(t('mobile.stocktake.scanAmbiguous', { count: result.candidateIds.length }));
      return;
    }
    setSelectedItem(result.item);
    setCandidateIds([]);
    setCountedQty(result.item.countedQty);
    setRemark(result.item.remark ?? '');
    message.success(t('mobile.stocktake.itemSelected', { name: result.item.productName ?? '-' }));
  };

  const saveCount = async (): Promise<void> => {
    if (!online || !selectedTaskId || !selectedItem?.id || countedQty === undefined) return;
    try {
      await countMutation.mutateAsync({
        taskId: selectedTaskId,
        itemId: selectedItem.id,
        payload: { countedQty, remark: remark.trim() || undefined },
      });
      message.success(t('mobile.stocktake.countSaved'));
      setSelectedItem(undefined);
      setCountedQty(undefined);
      setRemark('');
      await itemsQuery.refetch();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const requestFinish = (): void => {
    if (!selectedTaskId || !online || !complete) return;
    modal.confirm({
      title: t('mobile.stocktake.finishTitle'),
      content: t('mobile.stocktake.finishNotice'),
      okText: t('mobile.stocktake.finish'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await finishMutation.mutateAsync(selectedTaskId);
          message.success(t('mobile.stocktake.finished'));
          setSelectedTaskId(undefined);
          await tasksQuery.refetch();
        } catch (error) {
          message.error(getErrorMessage(error, t));
          throw error;
        }
      },
    });
  };

  return (
    <section className="mobile-operation-page">
      <header className="mobile-operation-heading">
        <div><Typography.Title level={2}>{t('mobile.stocktake.title')}</Typography.Title><p>{t('mobile.stocktake.subtitle')}</p></div>
        <Button aria-label={t('common.refresh')} disabled={!online} icon={<ReloadOutlined spin={tasksQuery.isFetching || itemsQuery.isFetching} />} onClick={() => void Promise.all([tasksQuery.refetch(), itemsQuery.refetch()])} />
      </header>

      {tasksQuery.isLoading ? <Skeleton active /> : tasks.length === 0 ? (
        <Empty description={t('mobile.stocktake.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <>
          <WarehouseOperationSteps current={!selectedTask ? 0 : selectedItem || complete ? 2 : 1} />
          <div className="mobile-operation-workspace">
            <section className="mobile-operation-panel">
              <label className="mobile-field-label">
                <span>{t('mobile.stocktake.task')}</span>
                <Select
                  allowClear
                  onChange={setSelectedTaskId}
                  optionFilterProp="label"
                  options={taskOptions}
                  placeholder={t('mobile.stocktake.selectTask')}
                  showSearch
                  value={selectedTaskId}
                />
              </label>
              {!selectedTask ? (
                <div className="mobile-selection-hint">{t('mobile.stocktake.selectTaskHint')}</div>
              ) : (
                <>
                  <div className="mobile-progress-band"><span>{t('mobile.stocktake.progress', { ready: countedCount, total: totalCount })}</span><Progress percent={totalCount === 0 ? 0 : Math.round((countedCount / totalCount) * 100)} showInfo={false} /></div>
                  <div className="mobile-blind-count-notice"><AuditOutlined /><div><strong>{t('mobile.stocktake.blindTitle')}</strong><span>{t('mobile.stocktake.blindNotice')}</span></div></div>

                  <ScanInput autoFocus disabled={!online || itemsQuery.isLoading || totalCount === 0} label={t('mobile.stocktake.scanItem')} onScan={scan} placeholder={t('mobile.stocktake.scanPlaceholder')} />
                  {candidateIds.length > 1 ? <Tag color="gold">{t('mobile.stocktake.candidates', { count: candidateIds.length })}</Tag> : null}

                  {selectedItem ? (
                    <section className="mobile-count-panel">
                      <div className="mobile-task-card-top"><strong>{selectedItem.productName ?? '-'}</strong><CheckCircleFilled /></div>
                      <div className="mobile-count-identifiers">
                        <span>{selectedItem.locationCode ?? '-'}</span>
                        <span>{t('mobile.batch')} {selectedItem.batchCode ?? '-'}</span>
                        <span>{selectedItem.productBarcode ?? '-'}</span>
                      </div>
                      <label className="mobile-field-label"><span>{t('mobile.stocktake.countedQty')}</span><InputNumber autoFocus disabled={!online} min={0} onChange={(value) => setCountedQty(value === null ? undefined : Number(value))} precision={0} value={countedQty} /></label>
                      <label className="mobile-field-label"><span>{t('common.remark')}</span><Input.TextArea disabled={!online} maxLength={500} onChange={(event) => setRemark(event.target.value)} rows={2} value={remark} /></label>
                      <Button block disabled={!online || countedQty === undefined} loading={countMutation.isPending} onClick={() => void saveCount()} size="large" type="primary">{t('mobile.stocktake.saveCount')}</Button>
                    </section>
                  ) : null}

                  <Button block disabled={!online || !complete} loading={finishMutation.isPending} onClick={requestFinish} size="large">{t('mobile.stocktake.finish')}</Button>
                </>
              )}
            </section>

            <aside className="mobile-work-queue">
              <div className="mobile-work-queue-heading">
                <strong>{t('mobile.workQueue')}</strong>
                <span>{selectedTask ? t('mobile.stocktake.progress', { ready: countedCount, total: totalCount }) : t('mobile.selectTaskFirst')}</span>
              </div>
              {!selectedTask ? <Empty description={t('mobile.selectTaskFirst')} image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
                <div className="mobile-task-list compact">
                  {items.map((item) => (
                    <button className={`mobile-task-card${item.id === selectedItem?.id ? ' is-active' : ''}${item.isCounted ? ' is-ready' : ''}`} disabled={item.isCounted} key={item.id} onClick={() => { setSelectedItem(item); setCountedQty(item.countedQty); setRemark(item.remark ?? ''); }} type="button">
                      <div className="mobile-task-card-top"><strong>{item.locationCode ?? '-'}</strong>{item.isCounted ? <CheckCircleFilled /> : <Tag>{t('mobile.pendingScan')}</Tag>}</div>
                      <span>{item.productName ?? '-'}</span>
                      <div className="mobile-task-meta"><span>{t('mobile.batch')} {item.batchCode ?? '-'}</span><span>{item.productBarcode ?? '-'}</span></div>
                    </button>
                  ))}
                </div>
              )}
            </aside>
          </div>
        </>
      )}
    </section>
  );
}
