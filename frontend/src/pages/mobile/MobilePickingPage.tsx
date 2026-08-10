import { CheckCircleFilled, EnvironmentOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntdApp, Button, Empty, InputNumber, Progress, Select, Skeleton, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  confirmOutboundTask,
  listOutboundTasks,
  OUTBOUND_TASKS_QUERY_KEY,
  type ConfirmPickingPayload,
  type OutboundTask,
} from '../../api/outbound';
import { ScanInput } from './ScanInput';
import { WarehouseOperationSteps } from './WarehouseOperationSteps';
import { useMobileOperation } from './WarehouseMobileLayout';

export function MobilePickingPage(): JSX.Element {
  const [activeTaskId, setActiveTaskId] = useState<number>();
  const [locationVerified, setLocationVerified] = useState(false);
  const [identityVerified, setIdentityVerified] = useState(false);
  const [actualQty, setActualQty] = useState(0);
  const { online } = useMobileOperation();
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();

  const tasksQuery = useQuery({
    queryKey: [...OUTBOUND_TASKS_QUERY_KEY, 'mobile-active'],
    queryFn: async () => {
      const [pending, picking] = await Promise.all([
        listOutboundTasks({ status: 'PENDING' }),
        listOutboundTasks({ status: 'PICKING' }),
      ]);
      return [...pending, ...picking];
    },
  });
  const tasks = tasksQuery.data ?? [];
  const activeTask = tasks.find((task) => task.id === activeTaskId);

  useEffect(() => {
    if (activeTaskId !== undefined && !activeTask) setActiveTaskId(undefined);
  }, [activeTask, activeTaskId]);

  useEffect(() => {
    setLocationVerified(false);
    setIdentityVerified(false);
    setActualQty(activeTask?.planQty ?? 0);
  }, [activeTask?.id, activeTask?.planQty]);

  const trackingMode = activeTask?.batchTrackingMode;

  const confirmMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ConfirmPickingPayload }) => confirmOutboundTask(id, payload),
  });

  const scanLocation = (code: string): void => {
    if (code.toLowerCase() !== activeTask?.locationCode?.toLowerCase()) {
      message.error(t('mobile.picking.locationMismatch', { location: activeTask?.locationCode ?? '-' }));
      return;
    }
    setLocationVerified(true);
    message.success(t('mobile.picking.locationVerified'));
  };

  const scanIdentity = (code: string): void => {
    const expected = trackingMode === 'LOCATION_VISUAL' ? activeTask?.productBarcode : activeTask?.batchCode;
    if (!expected || code.toLowerCase() !== expected.toLowerCase()) {
      message.error(t(trackingMode === 'LOCATION_VISUAL' ? 'mobile.picking.productMismatch' : 'mobile.picking.batchMismatch'));
      return;
    }
    setIdentityVerified(true);
    message.success(t('mobile.picking.identityVerified'));
  };

  const confirm = async (): Promise<void> => {
    if (!activeTask?.id || !trackingMode || !online || !locationVerified || !identityVerified) return;
    const payload: ConfirmPickingPayload = trackingMode === 'LOCATION_VISUAL'
      ? { actualQty, locationId: activeTask.locationId, skuCode: activeTask.productSkuCode }
      : { actualQty, batchCode: activeTask.batchCode };
    try {
      await confirmMutation.mutateAsync({ id: activeTask.id, payload });
      message.success(t('mobile.picking.completed'));
      setActiveTaskId(undefined);
      await tasksQuery.refetch();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const completedSteps = Number(locationVerified) + Number(identityVerified);
  const taskOptions = tasks
    .filter((task) => task.id !== undefined)
    .map((task) => ({
      label: `${task.salesOrderNo ?? '-'} · #${task.id} · ${task.locationCode ?? '-'} · ${task.productName ?? '-'} × ${task.planQty ?? 0}`,
      value: task.id as number,
    }));

  return (
    <section className="mobile-operation-page">
      <header className="mobile-operation-heading">
        <div><Typography.Title level={2}>{t('mobile.picking.title')}</Typography.Title><p>{t('mobile.picking.subtitle')}</p></div>
        <Button aria-label={t('common.refresh')} disabled={!online} icon={<ReloadOutlined spin={tasksQuery.isFetching} />} onClick={() => void tasksQuery.refetch()} />
      </header>

      {tasksQuery.isLoading ? <Skeleton active /> : tasks.length === 0 ? (
        <Empty description={t('mobile.picking.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <>
          <WarehouseOperationSteps current={!activeTask ? 0 : locationVerified && identityVerified ? 2 : 1} />
          <div className="mobile-operation-workspace">
            <section className="mobile-operation-panel">
              <label className="mobile-field-label">
                <span>{t('mobile.picking.task')}</span>
                <Select
                  allowClear
                  onChange={setActiveTaskId}
                  optionFilterProp="label"
                  options={taskOptions}
                  placeholder={t('mobile.picking.selectTask')}
                  showSearch
                  value={activeTaskId}
                />
              </label>
              {!activeTask ? (
                <div className="mobile-selection-hint">{t('mobile.picking.selectTaskHint')}</div>
              ) : (
                <section className="mobile-active-task">
                  <div className="mobile-task-card-top"><span>{activeTask.salesOrderNo ?? '-'}</span><Tag color="blue">#{activeTask.id}</Tag></div>
                  <Typography.Title level={3}>{activeTask.productName ?? '-'}</Typography.Title>
                  <div className="mobile-pick-target"><EnvironmentOutlined /><div><span>{t('mobile.picking.goTo')}</span><strong>{activeTask.locationCode ?? '-'}</strong></div></div>
                  <div className="mobile-task-meta"><span>{t('mobile.batch')} {activeTask.batchCode ?? '-'}</span><span>{t('mobile.picking.planQty')} {activeTask.planQty ?? 0}</span></div>
                  <Progress percent={completedSteps * 50} showInfo={false} steps={2} />

                  <ScanInput autoFocus disabled={!online} label={t('mobile.picking.scanLocation')} onScan={scanLocation} placeholder={activeTask.locationCode ?? '-'} />
                  <ScanInput
                    disabled={!online || !locationVerified || !trackingMode}
                    label={t(trackingMode === 'LOCATION_VISUAL' ? 'mobile.picking.scanProduct' : 'mobile.picking.scanBatch')}
                    onScan={scanIdentity}
                    placeholder={t(trackingMode === 'LOCATION_VISUAL' ? 'mobile.picking.productPlaceholder' : 'mobile.picking.batchPlaceholder')}
                  />

                  <label className="mobile-field-label">
                    <span>{t('mobile.picking.actualQty')}</span>
                    <InputNumber disabled={!online} max={activeTask.planQty ?? 0} min={1} onChange={(value) => setActualQty(Number(value ?? 0))} precision={0} value={actualQty} />
                  </label>
                  <Button block disabled={!online || !trackingMode || !locationVerified || !identityVerified || actualQty < 1} loading={confirmMutation.isPending} onClick={() => void confirm()} size="large" type="primary">
                    {t('mobile.picking.confirm')}
                  </Button>
                </section>
              )}
            </section>

            <aside className="mobile-work-queue">
              <div className="mobile-work-queue-heading">
                <strong>{t('mobile.picking.queue', { count: tasks.length })}</strong>
                <span>{t('mobile.picking.routeHint')}</span>
              </div>
              <div className="mobile-task-list compact">
                {tasks.map((task, index) => (
                  <button className={`mobile-task-card${task.id === activeTaskId ? ' is-active' : ''}`} key={task.id} onClick={() => setActiveTaskId(task.id)} type="button">
                    <div className="mobile-task-card-top"><strong>{index + 1}. {task.locationCode ?? '-'}</strong>{task.id === activeTaskId ? <CheckCircleFilled /> : null}</div>
                    <span>{task.productName ?? '-'}</span>
                    <div className="mobile-task-meta"><span>{task.salesOrderNo ?? '-'}</span><span>#{task.id} · × {task.planQty ?? 0}</span></div>
                  </button>
                ))}
              </div>
            </aside>
          </div>
        </>
      )}
    </section>
  );
}
