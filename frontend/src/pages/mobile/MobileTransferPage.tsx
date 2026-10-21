import { CheckCircleFilled, EditOutlined, ExportOutlined, InboxOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Checkbox, Empty, Form, Input, InputNumber, Modal, Progress, Select, Skeleton, Space, Tag, Typography, type FormInstance } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { getErrorMessage } from '../../api/errors';
import {
  createTransferCommandFingerprint,
  createTransferIdempotencyKey,
  dispatchTransferTask,
  listTransferTaskLocations,
  listTransferWarehouseTasks,
  receiveTransferTask,
  TRANSFER_WAREHOUSE_TASKS_QUERY_KEY,
  updateTransferWarehouseTask,
  type TransferDispatchPayload,
  type TransferReceiptDisposition,
  type TransferReceiptPayload,
  type TransferWarehouseDirection,
  type TransferWarehouseTask,
  type TransferWarehouseDispatchLine,
  type TransferWarehouseTaskUpdatePayload,
} from '../../api/transfers';
import { useAuth } from '../../auth/AuthProvider';
import type { PackagingSelection, PackagingView } from '../../api/packaging';
import { PackagingPicker } from '../inventory/PackagingPicker';
import { formatDateTime } from '../workflowUtils';
import { ScanInput } from './ScanInput';
import { WarehouseOperationSteps } from './WarehouseOperationSteps';
import { useMobileOperation } from './WarehouseMobileLayout';

interface IdempotencyAttempt { fingerprint: string; key: string }
interface DispatchDraft { locationVerified: boolean; productVerified: boolean; quantity: number; selected: boolean; packagingSelection?: PackagingSelection; packagingValid?: boolean }
interface DispatchFormValues { handoverReference?: string; estimatedArrivalAt?: string; warehouseRemark?: string }
interface ReceiptFormValues {
  remark?: string;
  lines: Array<{ dispatchLineId: number; locationId?: number; disposition: TransferReceiptDisposition; quantity: number; packagingSelection?: PackagingSelection; packagingValid?: boolean }>;
}
interface TransferOperationLine {
  id: number;
  transferLineId: number;
  allocationId?: number;
  dispatchLineId?: number;
  productSkuId: number;
  skuCode: string;
  productName: string;
  sourceBatchId?: number;
  sourceBatchCode?: string;
  externalBatchCode?: string | null;
  sourceLocationId?: number;
  sourceLocationCode?: string;
  productionDate?: string | null;
  expiryDate?: string | null;
  requestedQuantity: number;
  allocatedQuantity: number;
  dispatchedQuantity: number;
  receivedQuantity: number;
  remainingQuantity: number;
}

function isCompleteReceiptLine(
  line: ReceiptFormValues['lines'][number],
): line is TransferReceiptPayload['lines'][number] {
  return Number.isInteger(line.dispatchLineId) && line.dispatchLineId > 0
    && Number.isInteger(line.locationId) && Number(line.locationId) > 0
    && Number.isInteger(line.quantity) && line.quantity > 0;
}

function ReceiptPackagingField({ form, index, lines }: {
  form: FormInstance<ReceiptFormValues>; index: number; lines: TransferWarehouseDispatchLine[];
}): JSX.Element | null {
  const id = Form.useWatch(['lines', index, 'dispatchLineId'], form);
  const quantity = Form.useWatch(['lines', index, 'quantity'], form);
  const selection = Form.useWatch(['lines', index, 'packagingSelection'], form);
  const source = lines.find(line => line.dispatchLineId === id);
  const view = useMemo<PackagingView | undefined>(() => source ? {
    batchId: source.sourceBatchId, batchCode: source.sourceBatchCode, productSkuId: source.productSkuId,
    warehouseId: 0, locationId: 0, locationCode: '', quantity: source.openQuantity,
    version: source.packagingVersion ?? 0, reservedQuantity: 0, baseUnit: source.baseUnit,
    standardPackSize: source.packaging?.packSize ?? 1, packaging: source.packaging,
  } : undefined, [source]);
  if (!view) return null;
  return <div style={{ width: '100%' }}><PackagingPicker suppliedView={view} quantity={quantity ?? 0}
    value={selection} onChange={value => form.setFieldValue(['lines', index, 'packagingSelection'], value)}
    onValid={valid => { if (form.getFieldValue(['lines', index, 'packagingValid']) !== valid)
      form.setFieldValue(['lines', index, 'packagingValid'], valid); }} /></div>;
}

export function MobileTransferPage(): JSX.Element {
  const { session } = useAuth();
  const { online } = useMobileOperation();
  const { i18n, t } = useTranslation();
  const english = i18n.language === 'en-US';
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const canDispatch = hasPermission(session?.currentRole, session?.permissionCodes, 'transfer:dispatch');
  const canReceive = hasPermission(session?.currentRole, session?.permissionCodes, 'transfer:receive');
  const [direction, setDirection] = useState<TransferWarehouseDirection>(canDispatch ? 'OUTBOUND' : 'INBOUND');
  const [selectedTaskId, setSelectedTaskId] = useState<number>();
  const [activeLineId, setActiveLineId] = useState<number>();
  const [dispatchDrafts, setDispatchDrafts] = useState<Record<number, DispatchDraft>>({});
  const [receiptOpen, setReceiptOpen] = useState(false);
  const [operationsOpen, setOperationsOpen] = useState(false);
  const [dispatchForm] = Form.useForm<DispatchFormValues>();
  const [receiptForm] = Form.useForm<ReceiptFormValues>();
  const [operationsForm] = Form.useForm<TransferWarehouseTaskUpdatePayload>();
  const dispatchAttempt = useRef<IdempotencyAttempt>();
  const receiptAttempt = useRef<IdempotencyAttempt>();
  const updateAttempt = useRef<IdempotencyAttempt>();

  useEffect(() => {
    if (!canDispatch && canReceive) setDirection('INBOUND');
    else if (canDispatch && !canReceive) setDirection('OUTBOUND');
  }, [canDispatch, canReceive]);

  const tasksQuery = useQuery({
    queryKey: [...TRANSFER_WAREHOUSE_TASKS_QUERY_KEY, direction],
    queryFn: () => listTransferWarehouseTasks(direction),
  });
  const tasks = (tasksQuery.data ?? []).filter((task) => task.status !== 'COMPLETED' && task.status !== 'CLOSED');
  const selectedTask = tasks.find((task) => task.id === selectedTaskId);
  const operationLines = useMemo<TransferOperationLine[]>(() => {
    if (!selectedTask) return [];
    if (direction === 'OUTBOUND') {
      return selectedTask.lines.flatMap((line) => line.allocations.map((allocation) => ({
        id: allocation.allocationId,
        transferLineId: line.transferLineId,
        allocationId: allocation.allocationId,
        productSkuId: line.productSkuId,
        skuCode: line.skuCode,
        productName: line.productName,
        sourceBatchId: allocation.sourceBatchId,
        sourceBatchCode: allocation.sourceBatchCode,
        externalBatchCode: allocation.externalBatchCode,
        sourceLocationId: allocation.sourceLocationId,
        sourceLocationCode: allocation.sourceLocationCode,
        productionDate: allocation.productionDate,
        expiryDate: allocation.expiryDate,
        requestedQuantity: line.requestedQuantity,
        allocatedQuantity: line.allocatedQuantity,
        dispatchedQuantity: line.dispatchedQuantity,
        receivedQuantity: line.receivedQuantity,
        remainingQuantity: allocation.remainingQuantity,
      })));
    }
    return selectedTask.dispatchLines.map((line) => ({
      id: line.dispatchLineId,
      transferLineId: line.transferLineId,
      dispatchLineId: line.dispatchLineId,
      productSkuId: line.productSkuId,
      skuCode: line.skuCode,
      productName: line.productName,
      sourceBatchId: line.sourceBatchId,
      sourceBatchCode: line.sourceBatchCode,
      externalBatchCode: line.externalBatchCode,
      productionDate: line.productionDate,
      expiryDate: line.expiryDate,
      requestedQuantity: line.dispatchedQuantity,
      allocatedQuantity: line.dispatchedQuantity,
      dispatchedQuantity: line.dispatchedQuantity,
      receivedQuantity: line.receivedQuantity,
      remainingQuantity: line.openQuantity,
    }));
  }, [direction, selectedTask]);
  const locationsQuery = useQuery({
    queryKey: [...TRANSFER_WAREHOUSE_TASKS_QUERY_KEY, selectedTaskId, 'locations'],
    queryFn: () => listTransferTaskLocations(selectedTaskId as number),
    enabled: direction === 'INBOUND' && selectedTaskId !== undefined,
  });

  useEffect(() => {
    setSelectedTaskId(undefined);
    setActiveLineId(undefined);
    setDispatchDrafts({});
  }, [direction]);

  useEffect(() => {
    if (!selectedTask || direction !== 'OUTBOUND') {
      setDispatchDrafts({});
      setActiveLineId(undefined);
      return;
    }
    setDispatchDrafts(Object.fromEntries(operationLines.filter((line) => line.remainingQuantity > 0).map((line) => [
      line.id,
      { selected: false, quantity: line.remainingQuantity, productVerified: false, locationVerified: false },
    ])));
    dispatchForm.resetFields();
  }, [direction, dispatchForm, operationLines, selectedTask]);

  const patchDraft = (lineId: number, patch: Partial<DispatchDraft>): void => {
    setDispatchDrafts((current) => {
      const next = { ...current[lineId], ...patch };
      return JSON.stringify(next) === JSON.stringify(current[lineId]) ? current : { ...current, [lineId]: next };
    });
  };

  const selectedDispatchLines = useMemo(() => operationLines.filter((line) => dispatchDrafts[line.id]?.selected), [dispatchDrafts, operationLines]);
  const readyDispatchLines = selectedDispatchLines.filter((line) => {
    const draft = dispatchDrafts[line.id];
    return line.allocationId && draft?.productVerified && draft.locationVerified
      && draft.packagingValid && draft.quantity > 0 && draft.quantity <= line.remainingQuantity;
  });
  const readyPercent = selectedDispatchLines.length
    ? Math.round((readyDispatchLines.length / selectedDispatchLines.length) * 100) : 0;

  const dispatchMutation = useMutation({
    mutationFn: ({ id, payload, key }: { id: number; payload: TransferDispatchPayload; key: string }) => dispatchTransferTask(id, payload, key),
  });
  const receiptMutation = useMutation({
    mutationFn: ({ id, payload, key }: { id: number; payload: TransferReceiptPayload; key: string }) => receiveTransferTask(id, payload, key),
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, payload, key }: { id: number; payload: TransferWarehouseTaskUpdatePayload; key: string }) => updateTransferWarehouseTask(id, payload, key),
  });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: TRANSFER_WAREHOUSE_TASKS_QUERY_KEY });
  };

  const scanProduct = (value: string): void => {
    const normalized = value.trim().toLowerCase();
    const line = operationLines.find((candidate) => candidate.remainingQuantity > 0 && [
      candidate.skuCode, candidate.sourceBatchCode, candidate.externalBatchCode,
    ].some((code) => code?.toLowerCase() === normalized));
    if (!line) { message.error(english ? 'No matching line in this task.' : '当前任务中未找到对应商品或批次'); return; }
    setActiveLineId(line.id);
    patchDraft(line.id, { productVerified: true, selected: true });
    message.success(english ? 'Product verified.' : '商品已核验');
  };

  const scanSourceLocation = (value: string): void => {
    const line = operationLines.find((candidate) => candidate.id === activeLineId);
    if (!line || line.sourceLocationCode?.toLowerCase() !== value.trim().toLowerCase()) {
      message.error(english ? 'Source location does not match.' : '来源库位不匹配');
      return;
    }
    patchDraft(line.id, { locationVerified: true, selected: true });
    message.success(english ? 'Source location verified.' : '来源库位已核验');
  };

  const submitDispatch = async (): Promise<void> => {
    if (!selectedTask || !online || selectedDispatchLines.length === 0 || readyDispatchLines.length !== selectedDispatchLines.length) return;
    const metadata = dispatchForm.getFieldsValue();
    const payload: TransferDispatchPayload = {
      version: selectedTask.version,
      lines: readyDispatchLines.map((line) => ({
        allocationId: line.allocationId as number,
        quantity: dispatchDrafts[line.id].quantity,
        packagingSelection: dispatchDrafts[line.id].packagingSelection,
      })),
    };
    if (metadata.handoverReference) payload.handoverReference = metadata.handoverReference;
    if (metadata.estimatedArrivalAt) payload.estimatedArrivalAt = metadata.estimatedArrivalAt;
    if (metadata.warehouseRemark) payload.warehouseRemark = metadata.warehouseRemark;
    const fingerprint = createTransferCommandFingerprint(selectedTask.id, payload);
    if (dispatchAttempt.current?.fingerprint !== fingerprint) {
      dispatchAttempt.current = { fingerprint, key: createTransferIdempotencyKey('dispatch', selectedTask.id) };
    }
    try {
      await dispatchMutation.mutateAsync({ id: selectedTask.id, payload, key: dispatchAttempt.current.key });
      dispatchAttempt.current = undefined;
      message.success(english ? 'This dispatch was posted atomically.' : '本次调出已原子提交');
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const openReceipt = (): void => {
    if (!selectedTask) return;
    receiptForm.setFieldsValue({
      lines: operationLines.filter((line) => line.dispatchLineId && line.remainingQuantity > 0).map((line) => ({
        dispatchLineId: line.dispatchLineId as number,
        quantity: line.remainingQuantity,
        disposition: 'AVAILABLE',
      })),
    });
    setReceiptOpen(true);
  };

  const submitReceipt = async (): Promise<void> => {
    if (!selectedTask || !online) return;
    const values = await receiptForm.validateFields();
    if (values.lines.some(line => line.packagingValid !== true)) {
      message.error(t('packaging.selectSource'));
      return;
    }
    if (!values.lines.length) {
      message.error(english ? 'Add at least one receipt line.' : '请至少添加一条调入明细');
      return;
    }
    const completeLines = values.lines.filter(isCompleteReceiptLine);
    if (completeLines.length !== values.lines.length) {
      message.error(english ? 'Complete every receipt line.' : '请完整填写每条调入明细');
      return;
    }
    const receivedByDispatch = new Map<number, number>();
    completeLines.forEach((line) => receivedByDispatch.set(
      line.dispatchLineId,
      (receivedByDispatch.get(line.dispatchLineId) ?? 0) + line.quantity,
    ));
    const exceedsOpenQuantity = [...receivedByDispatch.entries()].some(([dispatchLineId, quantity]) =>
      quantity > (operationLines.find((line) => line.dispatchLineId === dispatchLineId)?.remainingQuantity ?? 0));
    if (exceedsOpenQuantity) {
      message.error(english ? 'Split rows exceed the open quantity of a dispatch line.' : '拆分行合计数量超过对应调出明细的未收数量');
      return;
    }
    const payload: TransferReceiptPayload = {
      version: selectedTask.version,
      remark: values.remark,
      lines: completeLines.map(line => ({ dispatchLineId: line.dispatchLineId, locationId: line.locationId,
        disposition: line.disposition, quantity: line.quantity, packagingSelection: line.packagingSelection })),
    };
    const fingerprint = createTransferCommandFingerprint(selectedTask.id, payload);
    if (receiptAttempt.current?.fingerprint !== fingerprint) {
      receiptAttempt.current = { fingerprint, key: createTransferIdempotencyKey('receive', selectedTask.id) };
    }
    try {
      await receiptMutation.mutateAsync({ id: selectedTask.id, payload, key: receiptAttempt.current.key });
      receiptAttempt.current = undefined;
      setReceiptOpen(false);
      message.success(english ? 'This receipt was posted atomically.' : '本次调入已原子提交');
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const openOperations = (): void => {
    if (!selectedTask) return;
    operationsForm.setFieldsValue({
      version: selectedTask.version,
      priority: selectedTask.priority,
      expectedFrom: selectedTask.expectedFrom ?? undefined,
      expectedTo: selectedTask.expectedTo ?? undefined,
      assignee: selectedTask.assignee ?? undefined,
      teamName: selectedTask.teamName ?? undefined,
      operationArea: selectedTask.operationArea ?? undefined,
      warehouseRemark: selectedTask.warehouseRemark ?? undefined,
    });
    setOperationsOpen(true);
  };

  const submitOperations = async (): Promise<void> => {
    if (!selectedTask || !online) return;
    const payload = await operationsForm.validateFields();
    const fingerprint = createTransferCommandFingerprint(selectedTask.id, payload);
    if (updateAttempt.current?.fingerprint !== fingerprint) {
      updateAttempt.current = { fingerprint, key: createTransferIdempotencyKey('task-update', selectedTask.id) };
    }
    try {
      await updateMutation.mutateAsync({ id: selectedTask.id, payload, key: updateAttempt.current.key });
      updateAttempt.current = undefined;
      setOperationsOpen(false);
      message.success(english ? 'Task operations updated.' : '作业安排已更新');
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const taskOptions = tasks.map((task) => ({
    label: `${task.taskNumber} · ${task.sourceWarehouseName} → ${task.destinationWarehouseName}`,
    value: task.id,
  }));
  const activeLine = operationLines.find((line) => line.id === activeLineId);
  const receivedTotal = selectedTask?.lines.reduce((sum, line) => sum + line.receivedQuantity, 0) ?? 0;
  const dispatchedTotal = selectedTask?.lines.reduce((sum, line) => sum + line.dispatchedQuantity, 0) ?? 0;

  return (
    <section className="mobile-operation-page">
      <header className="mobile-operation-heading">
        <div>
          <Typography.Title level={2}>{english ? 'Warehouse transfer operations' : '仓库调拨作业'}</Typography.Title>
          <p>{english ? 'Dispatch and receipt are separate warehouse-scoped operations. Commercial and approval data is intentionally hidden.' : '待调出与待调入分开作业；本页面不展示客户、供应商、成本、销售欠货或审批意见。'}</p>
        </div>
        <Button aria-label={english ? 'Refresh' : '刷新'} disabled={!online} icon={<ReloadOutlined spin={tasksQuery.isFetching} />} onClick={() => void refresh()} />
      </header>

      <div aria-label={english ? 'Transfer direction' : '调拨方向'} className="mobile-receiving-switch" role="group">
        {canDispatch ? <Button aria-pressed={direction === 'OUTBOUND'} icon={<ExportOutlined />} onClick={() => setDirection('OUTBOUND')} type={direction === 'OUTBOUND' ? 'primary' : 'default'}>{english ? 'Pending dispatch' : '待调出'}</Button> : null}
        {canReceive ? <Button aria-pressed={direction === 'INBOUND'} icon={<InboxOutlined />} onClick={() => setDirection('INBOUND')} type={direction === 'INBOUND' ? 'primary' : 'default'}>{english ? 'Pending receipt' : '待调入'}</Button> : null}
      </div>

      {tasksQuery.isLoading ? <Skeleton active /> : tasks.length === 0 ? (
        <Empty description={english ? 'No transfer tasks for this warehouse.' : '当前仓库没有待处理调拨任务'} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <>
          <WarehouseOperationSteps current={!selectedTask ? 0 : direction === 'OUTBOUND' && readyDispatchLines.length > 0 ? 2 : 1} />
          <div className="mobile-operation-workspace">
            <section className="mobile-operation-panel">
              <label className="mobile-field-label">
                <span>{english ? 'Transfer task' : '调拨任务'}</span>
                <Select allowClear onChange={setSelectedTaskId} optionFilterProp="label" options={taskOptions} placeholder={english ? 'Select task' : '选择任务'} showSearch value={selectedTaskId} />
              </label>
              {selectedTask ? (
                <>
                  <div className="mobile-task-summary">
                    <strong>{selectedTask.taskNumber}</strong>
                    <span>{selectedTask.sourceWarehouseName} → {selectedTask.destinationWarehouseName}</span>
                  </div>
                  <div className="transfer-mobile-meta">
                    <span>{english ? 'Transfer' : '调拨单'}：{selectedTask.transferNumber}</span>
                    <span>{english ? 'Outbound' : '调出'}：{selectedTask.outboundStatus}</span>
                    <span>{english ? 'Inbound' : '调入'}：{selectedTask.inboundStatus}</span>
                    <span>{english ? 'ETA' : '预计到达'}：{formatDateTime(selectedTask.estimatedArrivalAt, i18n.language)}</span>
                  </div>
                  <Button block icon={<EditOutlined />} disabled={!online} onClick={openOperations}>{english ? 'Task operations' : '作业安排'}</Button>
                  {direction === 'OUTBOUND' ? (
                    <>
                      <div className="mobile-progress-band"><span>{english ? `${readyDispatchLines.length} / ${selectedDispatchLines.length} selected lines ready` : `已选择 ${selectedDispatchLines.length} 行，已核验 ${readyDispatchLines.length} 行`}</span><Progress percent={readyPercent} showInfo={false} /></div>
                      <ScanInput disabled={!online} label={english ? 'Scan SKU / batch' : '扫描 SKU / 批次'} onScan={scanProduct} placeholder={english ? 'Scan and press Enter' : '扫描后按回车'} />
                      <ScanInput disabled={!online || !activeLine} label={english ? 'Scan source location' : '扫描来源库位'} manualValue={activeLine?.sourceLocationCode} onScan={scanSourceLocation} placeholder={english ? 'Scan source location' : '扫描来源库位码'} />
                      <Form className="transfer-dispatch-metadata" form={dispatchForm} layout="vertical">
                        <Form.Item label={english ? 'Handover reference' : '交接参考号'} name="handoverReference"><Input maxLength={100} /></Form.Item>
                        <Form.Item label={english ? 'Estimated arrival' : '预计到达时间'} name="estimatedArrivalAt"><Input type="datetime-local" /></Form.Item>
                        <Form.Item label={english ? 'Warehouse note' : '仓库备注'} name="warehouseRemark"><Input.TextArea maxLength={500} /></Form.Item>
                      </Form>
                      <Button block disabled={!online || selectedDispatchLines.length === 0 || readyDispatchLines.length !== selectedDispatchLines.length} loading={dispatchMutation.isPending} onClick={() => void submitDispatch()} size="large" type="primary">{english ? 'Submit this dispatch' : '提交本次调出'}</Button>
                    </>
                  ) : (
                    <>
                      <Alert message={english ? `${receivedTotal} received from ${dispatchedTotal} dispatched. A short quantity remains an in-transit exception until separately closed.` : `累计调出 ${dispatchedTotal}，累计调入 ${receivedTotal}；少收数量继续保留为在途差异，不能在这里自动核销。`} showIcon style={{ marginTop: 12 }} type="info" />
                      <Button block disabled={!online || !operationLines.some((line) => line.remainingQuantity > 0)} icon={<InboxOutlined />} loading={receiptMutation.isPending} onClick={openReceipt} size="large" type="primary">{english ? 'Record this receipt' : '登记本次调入'}</Button>
                    </>
                  )}
                </>
              ) : <div className="mobile-selection-hint">{english ? 'Select a warehouse-scoped task first.' : '请先选择当前仓库范围内的任务'}</div>}
            </section>

            <aside className="mobile-work-queue">
              <div className="mobile-work-queue-heading"><strong>{english ? 'Task lines' : '任务明细'}</strong><span>{operationLines.length}</span></div>
              {!selectedTask ? <Empty description={english ? 'Select a task first' : '请先选择任务'} image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
                <div className="mobile-task-list">
                  {operationLines.map((line) => {
                    const draft = dispatchDrafts[line.id];
                    const ready = draft?.selected && draft.productVerified && draft.locationVerified;
                    return (
                      <article className={`mobile-task-card${activeLineId === line.id ? ' is-active' : ''}${ready ? ' is-ready' : ''}`} key={line.id} onClick={() => setActiveLineId(line.id)}>
                        <div className="mobile-task-card-top">
                          {direction === 'OUTBOUND' ? <Checkbox checked={draft?.selected} onChange={(event) => patchDraft(line.id, { selected: event.target.checked })} onClick={(event) => event.stopPropagation()}>{line.productName}</Checkbox> : <strong>{line.productName}</strong>}
                          {ready ? <CheckCircleFilled /> : <Tag>{line.remainingQuantity}</Tag>}
                        </div>
                        <span>{line.skuCode} · {line.sourceBatchCode ?? '-'}</span>
                        <span>{english ? 'Source location' : '来源库位'}：{line.sourceLocationCode ?? '-'}</span>
                        <span>{english ? 'Production / expiry' : '生产 / 效期'}：{line.productionDate ?? '-'} / {line.expiryDate ?? '-'}</span>
                        <div className="mobile-task-primary">{english ? 'Remaining' : '本次剩余'} {line.remainingQuantity}</div>
                        {direction === 'OUTBOUND' && draft ? <Space onClick={(event) => event.stopPropagation()} wrap>
                          <InputNumber min={1} max={line.remainingQuantity} onChange={(value) => patchDraft(line.id, { quantity: Number(value ?? 0), selected: true })} precision={0} value={draft.quantity} />
                          <Button onClick={() => patchDraft(line.id, { selected: true, productVerified: true, locationVerified: true })} size="small">{english ? 'Manual verify' : '人工核验'}</Button>
                        </Space> : null}
                        {direction === 'OUTBOUND' && draft?.selected && <div onClick={event => event.stopPropagation()}>
                          <PackagingPicker batchId={line.sourceBatchId} quantity={draft.quantity} value={draft.packagingSelection}
                            onChange={value => patchDraft(line.id, { packagingSelection: value })}
                            onValid={valid => patchDraft(line.id, { packagingValid: valid })} />
                        </div>}
                      </article>
                    );
                  })}
                </div>
              )}
            </aside>
          </div>
        </>
      )}

      <Modal confirmLoading={receiptMutation.isPending} onCancel={() => setReceiptOpen(false)} onOk={() => void submitReceipt()} open={receiptOpen} title={english ? 'Record destination receipt' : '登记目标仓调入'} width={920}>
        <Alert message={english ? 'One submission is atomic. Split one dispatch line into multiple rows when stock goes to different locations or quality segments.' : '一次提交原子执行；同一调出行进入不同库位或质量段时，请拆成多行。'} showIcon style={{ marginBottom: 12 }} type="info" />
        <Form form={receiptForm} layout="vertical">
          <Form.Item label={english ? 'Warehouse note' : '仓库备注'} name="remark"><Input.TextArea maxLength={500} /></Form.Item>
          <Form.List name="lines">{(fields, { add, remove }) => <>
            {fields.map((field) => {
              const dispatchLineId = receiptForm.getFieldValue(['lines', field.name, 'dispatchLineId']);
              const source = operationLines.find((line) => line.dispatchLineId === dispatchLineId);
              return <div className="transfer-receipt-line" key={field.key}>
                <Form.Item label={english ? 'Dispatched line' : '调出明细'} name={[field.name, 'dispatchLineId']} rules={[{ required: true }]}>
                  <Select options={operationLines.filter((line) => line.dispatchLineId && line.remainingQuantity > 0).map((line) => ({ label: `${line.skuCode} · ${line.sourceBatchCode ?? '-'} · ${line.remainingQuantity}`, value: line.dispatchLineId }))} style={{ width: 280 }} />
                </Form.Item>
                <Form.Item label={english ? 'Target location' : '目标库位'} name={[field.name, 'locationId']} rules={[{ required: true }]}>
                  <Select loading={locationsQuery.isLoading} optionFilterProp="label" options={(locationsQuery.data ?? []).map((location) => ({ label: `${location.locationCode}${location.locationName ? ` · ${location.locationName}` : ''}`, value: location.id }))} showSearch style={{ width: 230 }} />
                </Form.Item>
                <Form.Item label={english ? 'Physical condition' : '实物状态'} name={[field.name, 'disposition']} rules={[{ required: true }]}>
                  <Select options={(['AVAILABLE', 'DAMAGED', 'QUARANTINED'] as TransferReceiptDisposition[]).map((value) => ({ value, label: value }))} style={{ width: 165 }} />
                </Form.Item>
                <Form.Item label={english ? 'Quantity' : '数量'} name={[field.name, 'quantity']} rules={[{ required: true }]}>
                  <InputNumber min={1} max={source?.remainingQuantity} precision={0} />
                </Form.Item>
                <Button danger onClick={() => remove(field.name)}>{english ? 'Remove' : '删除'}</Button>
                <ReceiptPackagingField form={receiptForm} index={field.name} lines={selectedTask?.dispatchLines ?? []} />
              </div>;
            })}
            <Button onClick={() => add({ disposition: 'AVAILABLE' })}>{english ? 'Add split row' : '添加拆分行'}</Button>
          </>}</Form.List>
        </Form>
      </Modal>

      <Modal confirmLoading={updateMutation.isPending} onCancel={() => setOperationsOpen(false)} onOk={() => void submitOperations()} open={operationsOpen} title={english ? 'Transfer task operations' : '调拨作业安排'}>
        <Form form={operationsForm} layout="vertical">
          <Form.Item hidden name="version"><InputNumber /></Form.Item>
          <Form.Item label={english ? 'Priority' : '优先级'} name="priority"><InputNumber min={1} max={9} precision={0} /></Form.Item>
          <Form.Item label={english ? 'Expected start' : '预计开始时间'} name="expectedFrom"><Input type="datetime-local" /></Form.Item>
          <Form.Item label={english ? 'Expected finish' : '预计结束时间'} name="expectedTo"><Input type="datetime-local" /></Form.Item>
          <Form.Item label={english ? 'Assignee' : '收货人员'} name="assignee"><Input maxLength={100} /></Form.Item>
          <Form.Item label={english ? 'Team' : '班组'} name="teamName"><Input maxLength={100} /></Form.Item>
          <Form.Item label={english ? 'Operation area' : '作业区'} name="operationArea"><Input maxLength={120} /></Form.Item>
          <Form.Item label={english ? 'Warehouse note' : '仓库备注'} name="warehouseRemark"><Input.TextArea maxLength={500} /></Form.Item>
        </Form>
      </Modal>
    </section>
  );
}
