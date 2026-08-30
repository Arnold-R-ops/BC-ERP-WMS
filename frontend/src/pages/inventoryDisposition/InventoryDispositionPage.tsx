import {
  Alert, App as AntdApp, Button, Descriptions, Drawer, Form, Input, InputNumber,
  Modal, Select, Space, Switch, Table, Tabs, Tag, Typography,
  type TableColumnsType,
} from 'antd';
import {
  CheckOutlined, DeleteOutlined, EyeOutlined, SettingOutlined,
  StopOutlined, WarningOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { getErrorMessage } from '../../api/errors';
import {
  createInventoryScrapOrder, decideInventoryDisposition, executeInventoryDisposition,
  getInventoryDispositionPolicy, inventoryDispositionIdempotencyKey,
  INVENTORY_DISPOSITION_QUERY_KEY, listInventoryDispositionCandidates,
  listInventoryDispositions, previewInventoryDamage, reportInventoryDamage,
  submitInventoryDisposition, updateInventoryDispositionPolicy,
  type CreateInventoryScrapPayload, type InventoryDisposition,
  type InventoryDispositionCandidate, type InventoryDispositionReviewMode,
  type InventoryDispositionStatus, type ReportInventoryDamagePayload,
} from '../../api/inventoryDisposition';

type Operation = 'damage' | 'scrap';
type Filter = 'ALL' | InventoryDispositionStatus;
interface OperationValues {
  reasonCode: string;
  reasonDetail?: string;
  externalReference?: string;
  recallNoticeNumber?: string;
}
interface DecisionState { order: InventoryDisposition; action: 'approve' | 'reject' | 'cancel' }

function statusColor(status: InventoryDispositionStatus): string {
  if (status === 'COMPLETED') return 'success';
  if (status === 'READY_TO_EXECUTE') return 'processing';
  if (status === 'PARTIALLY_EXECUTED' || status === 'PENDING_REVIEW') return 'warning';
  return 'default';
}

export function InventoryDispositionPage({ compact = false }: { compact?: boolean }): JSX.Element {
  const { i18n, t } = useTranslation();
  const english = i18n.language === 'en-US';
  const { session } = useAuth();
  const { message, modal } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const permissions = session?.permissionCodes;
  const role = session?.currentRole;
  const canReport = !compact && hasPermission(role, permissions, 'inventory_disposition:report');
  const canCreate = !compact && hasPermission(role, permissions, 'inventory_disposition:create');
  const canExecute = hasPermission(role, permissions, 'inventory_disposition:execute');
  const canReview = !compact && hasPermission(role, permissions, 'inventory_disposition:review');
  const canPolicy = !compact && hasPermission(role, permissions, 'inventory_disposition:policy');

  const [filter, setFilter] = useState<Filter>('ALL');
  const [operation, setOperation] = useState<Operation>();
  const [candidateSearch, setCandidateSearch] = useState('');
  const [selectedBatchIds, setSelectedBatchIds] = useState<number[]>([]);
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [selectedOrder, setSelectedOrder] = useState<InventoryDisposition>();
  const [decision, setDecision] = useState<DecisionState>();
  const [executeOrder, setExecuteOrder] = useState<InventoryDisposition>();
  const [executeQuantities, setExecuteQuantities] = useState<Record<number, number>>({});
  const [policyOpen, setPolicyOpen] = useState(false);
  const [operationForm] = Form.useForm<OperationValues>();
  const [decisionForm] = Form.useForm<{ comment: string }>();
  const [executeForm] = Form.useForm<{ executionNote?: string }>();
  const [policyForm] = Form.useForm<{
    reviewMode: InventoryDispositionReviewMode;
    automaticExpiryEnabled: boolean;
    businessTimezone: string;
    reason: string;
  }>();

  const ordersQuery = useQuery({
    queryKey: [...INVENTORY_DISPOSITION_QUERY_KEY, 'orders', filter, compact],
    queryFn: () => listInventoryDispositions(undefined, filter === 'ALL' ? undefined : filter),
  });
  const candidatesQuery = useQuery({
    queryKey: [...INVENTORY_DISPOSITION_QUERY_KEY, 'candidates', candidateSearch],
    queryFn: () => listInventoryDispositionCandidates(candidateSearch),
    enabled: operation !== undefined,
  });
  const policyQuery = useQuery({
    queryKey: [...INVENTORY_DISPOSITION_QUERY_KEY, 'policy'],
    queryFn: getInventoryDispositionPolicy,
    enabled: canPolicy,
  });

  const candidates = useMemo(() => (candidatesQuery.data ?? []).filter((candidate) => {
    if (operation === 'damage') {
      return !candidate.dateExpired
        && ['AVAILABLE', 'QUARANTINED', 'RECALL_HOLD'].includes(candidate.qualityStatus);
    }
    return candidate.disposableQuantity > 0;
  }), [candidatesQuery.data, operation]);

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: INVENTORY_DISPOSITION_QUERY_KEY });
  };

  const openOperation = (next: Operation): void => {
    setOperation(next); setCandidateSearch(''); setSelectedBatchIds([]); setQuantities({});
    operationForm.resetFields();
    operationForm.setFieldsValue({
      reasonCode: next === 'damage' ? 'PHYSICAL_DAMAGE' : 'DAMAGED_SCRAP',
    });
  };
  const closeOperation = (): void => {
    setOperation(undefined); setSelectedBatchIds([]); setQuantities({});
  };
  const selectedLines = (): { batchId: number; quantity: number }[] => selectedBatchIds.map((batchId) => ({
    batchId, quantity: quantities[batchId] ?? 1,
  }));

  const damageMutation = useMutation({
    mutationFn: ({ payload, key }: { payload: ReportInventoryDamagePayload; key: string }) => (
      reportInventoryDamage(payload, key)
    ),
  });
  const scrapMutation = useMutation({
    mutationFn: ({ payload, key }: { payload: CreateInventoryScrapPayload; key: string }) => (
      createInventoryScrapOrder(payload, key)
    ),
  });
  const decisionMutation = useMutation({
    mutationFn: ({ state, comment }: { state: DecisionState; comment: string }) => (
      decideInventoryDisposition(
        state.order.id, state.action, comment,
        inventoryDispositionIdempotencyKey(state.action),
      )
    ),
  });
  const submitMutation = useMutation({
    mutationFn: (order: InventoryDisposition) => submitInventoryDisposition(
      order.id, inventoryDispositionIdempotencyKey('submit'),
    ),
  });
  const executeMutation = useMutation({
    mutationFn: ({ order, note }: { order: InventoryDisposition; note?: string }) => (
      executeInventoryDisposition(
        order.id,
        order.lines.filter((line) => (executeQuantities[line.id] ?? 0) > 0)
          .map((line) => ({ lineId: line.id, quantity: executeQuantities[line.id] })),
        note,
        inventoryDispositionIdempotencyKey('execute'),
      )
    ),
  });
  const policyMutation = useMutation({
    mutationFn: (values: {
      reviewMode: InventoryDispositionReviewMode;
      automaticExpiryEnabled: boolean;
      businessTimezone: string;
      reason: string;
    }) => updateInventoryDispositionPolicy(
      values.reviewMode, values.automaticExpiryEnabled,
      values.businessTimezone, values.reason,
    ),
  });

  const submitOperation = async (saveAsDraft = false): Promise<void> => {
    try {
      const values = await operationForm.validateFields();
      if (!operation || selectedBatchIds.length === 0) {
        message.warning(english ? 'Select at least one batch.' : '请至少选择一个库存批次'); return;
      }
      if (selectedBatchIds.some((id) => (quantities[id] ?? 0) <= 0)) {
        message.warning(english ? 'Every quantity must be greater than zero.' : '每个处理数量都必须大于 0'); return;
      }
      if (operation === 'damage') {
        const base: ReportInventoryDamagePayload = {
          lines: selectedLines(), reasonCode: values.reasonCode,
          reasonDetail: values.reasonDetail, externalReference: values.externalReference,
          recallNoticeNumber: values.recallNoticeNumber, confirmSalesImpact: false,
        };
        const preview = await previewInventoryDamage(base);
        const execute = async (): Promise<void> => {
          const updated = await damageMutation.mutateAsync({
            payload: { ...base, confirmSalesImpact: preview.confirmationRequired },
            key: inventoryDispositionIdempotencyKey('damage'),
          });
          message.success(english ? 'Damaged stock is now unavailable.' : '破损库存已转为不可售');
          setSelectedOrder(updated); closeOperation(); await refresh();
        };
        if (preview.confirmationRequired) {
          modal.confirm({
            title: english ? 'Confirm sales impact and mark damaged' : '确认销售影响并登记破损',
            content: english
              ? `${preview.totalReallocatedQuantity} units will be reallocated; ${preview.totalBackorderedQuantity} units become backorders, including ${preview.totalQualityExceptionQuantity} units in started picking tasks.`
              : `系统将改派 ${preview.totalReallocatedQuantity} 件预留；${preview.totalBackorderedQuantity} 件转为欠货，其中 ${preview.totalQualityExceptionQuantity} 件已开始拣货并将转为质量异常任务。`,
            okText: english ? 'Confirm and mark damaged' : '确认影响并登记破损',
            onOk: execute,
          });
        } else await execute();
      } else {
        const updated = await scrapMutation.mutateAsync({
          payload: {
            lines: selectedLines(), reasonCode: values.reasonCode,
            reasonDetail: values.reasonDetail, externalReference: values.externalReference,
            recallNoticeNumber: values.recallNoticeNumber, saveAsDraft,
          },
          key: inventoryDispositionIdempotencyKey('scrap'),
        });
        message.success(updated.status === 'DRAFT'
          ? (english ? 'Disposition draft saved.' : '处置单草稿已保存')
          : updated.status === 'PENDING_REVIEW'
          ? (english ? 'Disposition submitted for review.' : '处置单已提交审批')
          : (english ? 'Disposition is ready for warehouse execution.' : '处置单已进入待执行'));
        setSelectedOrder(updated); closeOperation(); await refresh();
      }
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(getErrorMessage(error, t));
    }
  };

  const submitDraft = async (order: InventoryDisposition): Promise<void> => {
    try {
      const updated = await submitMutation.mutateAsync(order);
      setSelectedOrder(updated);
      message.success(updated.status === 'PENDING_REVIEW'
        ? (english ? 'Disposition submitted for review.' : '处置单已提交审批')
        : (english ? 'Disposition is ready for execution.' : '处置单已进入待执行'));
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const submitDecision = async (): Promise<void> => {
    if (!decision) return;
    try {
      const { comment } = await decisionForm.validateFields();
      const updated = await decisionMutation.mutateAsync({ state: decision, comment });
      setSelectedOrder(updated); setDecision(undefined); decisionForm.resetFields();
      message.success(english ? 'Disposition updated.' : '处置单状态已更新'); await refresh();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(getErrorMessage(error, t));
    }
  };

  const openExecution = (order: InventoryDisposition): void => {
    setExecuteOrder(order);
    setExecuteQuantities(Object.fromEntries(order.lines
      .filter((line) => line.outstandingQuantity > 0)
      .map((line) => [line.id, line.outstandingQuantity])));
    executeForm.resetFields();
  };
  const submitExecution = async (): Promise<void> => {
    if (!executeOrder) return;
    try {
      const values = await executeForm.validateFields();
      if (!executeOrder.lines.some((line) => (executeQuantities[line.id] ?? 0) > 0)) {
        message.warning(english ? 'Enter at least one execution quantity.' : '请至少填写一项执行数量'); return;
      }
      const updated = await executeMutation.mutateAsync({ order: executeOrder, note: values.executionNote });
      setExecuteOrder(undefined); setSelectedOrder(updated);
      message.success(english ? 'Physical disposal posted atomically.' : '实物处置与库存流水已原子落账');
      await refresh();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(getErrorMessage(error, t));
    }
  };

  const submitPolicy = async (): Promise<void> => {
    try {
      const values = await policyForm.validateFields();
      const updated = await policyMutation.mutateAsync(values);
      queryClient.setQueryData([...INVENTORY_DISPOSITION_QUERY_KEY, 'policy'], updated);
      setPolicyOpen(false); message.success(english ? 'Disposition policy updated.' : '库存处置策略已更新');
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(getErrorMessage(error, t));
    }
  };

  const orderColumns: TableColumnsType<InventoryDisposition> = [
    { title: english ? 'Document no.' : '单号', dataIndex: 'dispositionNumber', width: 220, fixed: 'left' },
    { title: english ? 'Type' : '类型', dataIndex: 'dispositionType', width: 150 },
    { title: english ? 'Status' : '状态', dataIndex: 'status', width: 160, render: (value: InventoryDispositionStatus) => <Tag color={statusColor(value)}>{value}</Tag> },
    { title: english ? 'Reason' : '原因', dataIndex: 'reasonCode', width: 190 },
    { title: english ? 'Planned' : '计划数量', width: 100, align: 'right', render: (_, row) => row.lines.reduce((sum, line) => sum + line.quantity, 0) },
    { title: english ? 'Executed' : '已执行', width: 100, align: 'right', render: (_, row) => row.lines.reduce((sum, line) => sum + line.executedQuantity, 0) },
    { title: english ? 'Warehouses' : '涉及仓库', width: 220, render: (_, row) => [...new Set(row.lines.map((line) => line.warehouseName))].join('、') },
    { title: english ? 'Created by' : '制单人', dataIndex: 'requestedByName', width: 130 },
    { title: english ? 'Created at' : '制单时间', dataIndex: 'requestedAt', width: 190, render: (value: string) => new Date(value).toLocaleString(i18n.language) },
    { title: english ? 'Actions' : '操作', fixed: 'right', width: 170, render: (_, row) => <Space>
      <Button icon={<EyeOutlined />} onClick={() => setSelectedOrder(row)} size="small" type="link">{english ? 'View' : '查看'}</Button>
      {canExecute && ['READY_TO_EXECUTE', 'PARTIALLY_EXECUTED'].includes(row.status)
        && <Button icon={<DeleteOutlined />} onClick={() => openExecution(row)} size="small" type="link">{english ? 'Execute' : '执行'}</Button>}
    </Space> },
  ];

  const candidateColumns: TableColumnsType<InventoryDispositionCandidate> = [
    { title: 'SKU / 商品', width: 220, render: (_, row) => <Space direction="vertical" size={0}><strong>{row.productSkuCode}</strong><span>{row.productName}</span></Space> },
    { title: english ? 'Batch' : '批次', dataIndex: 'batchCode', width: 180 },
    { title: english ? 'Warehouse / Location' : '仓库 / 库位', width: 220, render: (_, row) => `${row.warehouseName} / ${row.locationCode}` },
    { title: english ? 'Quality' : '质量状态', dataIndex: 'qualityStatus', width: 150, render: (value: string) => <Tag>{value}</Tag> },
    { title: english ? 'Expiry' : '有效期', dataIndex: 'expiryDate', width: 120, render: (value?: string, row?: InventoryDispositionCandidate) => value ? <Tag color={row?.dateExpired ? 'red' : undefined}>{value}</Tag> : '-' },
    { title: english ? 'On hand' : '在手', dataIndex: 'quantity', width: 85, align: 'right' },
    { title: english ? 'Reserved' : '预留', dataIndex: 'reservedQuantity', width: 85, align: 'right' },
    { title: english ? 'Process qty' : '处理数量', width: 120, render: (_, row) => <InputNumber
      min={1} max={operation === 'scrap' ? row.disposableQuantity : row.quantity}
      onChange={(value) => setQuantities((current) => ({ ...current, [row.batchId]: value ?? 1 }))}
      size="small" value={quantities[row.batchId] ?? 1}
    /> },
  ];

  const lineColumns: TableColumnsType<InventoryDisposition['lines'][number]> = [
    { title: 'SKU', dataIndex: 'productSkuCode', width: 130 },
    { title: english ? 'Product' : '商品', dataIndex: 'productName', width: 180 },
    { title: english ? 'Source / result batch' : '来源 / 结果批次', width: 260, render: (_, row) => `${row.sourceBatchCode} / ${row.resultBatchCode ?? '-'}` },
    { title: english ? 'Warehouse / Location' : '仓库 / 库位', width: 220, render: (_, row) => `${row.warehouseName} / ${row.locationCode}` },
    { title: english ? 'Quality' : '质量变化', width: 220, render: (_, row) => `${row.fromQualityStatus} → ${row.toQualityStatus}` },
    { title: english ? 'Planned' : '计划', dataIndex: 'quantity', width: 80, align: 'right' },
    { title: english ? 'Executed' : '已执行', dataIndex: 'executedQuantity', width: 90, align: 'right' },
    { title: english ? 'Remaining' : '剩余', dataIndex: 'outstandingQuantity', width: 80, align: 'right' },
    { title: english ? 'Sales impact' : '销售影响', width: 210, render: (_, row) => `${english ? 'moved' : '改派'} ${row.reallocatedReservedQuantity} / ${english ? 'backorder' : '欠货'} ${row.backorderedQuantity} / ${english ? 'picking exception' : '拣货异常'} ${row.qualityExceptionQuantity}` },
  ];

  return <section className="data-page workflow-page">
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Alert showIcon type="warning" icon={<WarningOutlined />}
        message={english ? 'Marking damage or expiry does not reduce physical on-hand.' : '登记破损或过期不会扣减实存在库'}
        description={english ? 'On-hand is reduced only when an authorised warehouse user confirms physical destruction. Each execution and its stock ledger rows commit atomically.' : '只有具备权限的仓库人员确认实物销毁时才扣减库存；每次执行与对应库存流水作为同一事务提交。'} />
      {!compact && <Tabs activeKey={filter} onChange={(key) => setFilter(key as Filter)} items={[
        'ALL', 'DRAFT', 'PENDING_REVIEW', 'READY_TO_EXECUTE', 'PARTIALLY_EXECUTED', 'COMPLETED', 'REJECTED', 'CANCELLED',
      ].map((key) => ({ key, label: key === 'ALL' ? (english ? 'All' : '全部') : key }))} />}
      <Table columns={orderColumns} dataSource={(ordersQuery.data ?? []).filter((order) => (
        !compact || ['READY_TO_EXECUTE', 'PARTIALLY_EXECUTED'].includes(order.status)
      ))} loading={ordersQuery.isLoading}
        rowKey="id" scroll={{ x: 1640 }} title={() => <Space wrap>
          <Typography.Title level={4} style={{ margin: 0 }}>{compact ? (english ? 'Disposal tasks' : '待处置任务') : (english ? 'Inventory disposition' : '库存处置')}</Typography.Title>
          {canPolicy && <Button icon={<SettingOutlined />} onClick={() => {
            policyForm.setFieldsValue({
              reviewMode: policyQuery.data?.reviewMode ?? 'DIRECT_WITH_REASON',
              automaticExpiryEnabled: policyQuery.data?.automaticExpiryEnabled ?? true,
              businessTimezone: policyQuery.data?.businessTimezone ?? 'Europe/London',
            }); setPolicyOpen(true);
          }}>{english ? 'Policy' : '处置策略'}</Button>}
          {canReport && <Button icon={<WarningOutlined />} onClick={() => openOperation('damage')} type="primary">{english ? 'Report damage' : '登记破损'}</Button>}
          {canCreate && <Button icon={<DeleteOutlined />} onClick={() => openOperation('scrap')}>{english ? 'Create disposal' : '创建报废/销毁单'}</Button>}
        </Space>} />
    </Space>

    <Modal destroyOnHidden width="min(1160px, 96vw)" open={operation !== undefined}
      onCancel={closeOperation} onOk={() => void submitOperation()}
      okButtonProps={{ loading: damageMutation.isPending || scrapMutation.isPending }}
      footer={operation === 'scrap' ? (_, { CancelBtn, OkBtn }) => <Space>
        <CancelBtn />
        <Button loading={scrapMutation.isPending} onClick={() => void submitOperation(true)}>
          {english ? 'Save draft' : '保存草稿'}
        </Button>
        <OkBtn />
      </Space> : undefined}
      title={operation === 'damage' ? (english ? 'Report damaged inventory' : '登记破损库存') : (english ? 'Create scrap/destruction order' : '创建报废/销毁单')}>
      <Form form={operationForm} layout="vertical">
        <Space align="start" wrap>
          <Form.Item label={english ? 'Standard reason' : '标准原因'} name="reasonCode" rules={[{ required: true }]}>
            <Select style={{ minWidth: 240 }} options={(operation === 'damage' ? [
              ['PHYSICAL_DAMAGE', '实物破损'], ['STORAGE_DAMAGE', '储存损坏'],
              ['HANDLING_DAMAGE', '搬运损坏'], ['PACKAGING_DAMAGE', '包装损坏'], ['OTHER_DAMAGE', '其他破损'],
            ] : [
              ['DAMAGED_SCRAP', '破损报废'], ['EXPIRED_SCRAP', '过期报废'],
              ['QUALITY_FAILED_SCRAP', '质量不合格报废'], ['RECALL_DESTRUCTION', '召回销毁'],
              ['OTHER_DESTRUCTION', '其他销毁'],
            ]).map(([value, label]) => ({ value, label: english ? value : label }))} />
          </Form.Item>
          <Form.Item label={english ? 'External reference (optional)' : '外部参考号（选填）'} name="externalReference"><Input maxLength={120} /></Form.Item>
          <Form.Item label={english ? 'Recall notice (optional)' : '召回通知号（选填）'} name="recallNoticeNumber"><Input maxLength={120} /></Form.Item>
        </Space>
        <Form.Item label={english ? 'Details (optional)' : '补充说明（选填）'} name="reasonDetail"><Input.TextArea maxLength={500} rows={2} showCount /></Form.Item>
      </Form>
      <Input.Search allowClear enterButton onSearch={setCandidateSearch}
        placeholder={english ? 'Search SKU, product or batch' : '搜索 SKU、商品或批次'} />
      <Table columns={candidateColumns} dataSource={candidates} loading={candidatesQuery.isLoading}
        pagination={{ pageSize: 6 }} rowKey="batchId" scroll={{ x: 1260 }} size="small"
        rowSelection={{ selectedRowKeys: selectedBatchIds, onChange: (keys) => {
          const ids = keys.map(Number); setSelectedBatchIds(ids);
          setQuantities((current) => Object.fromEntries(ids.map((id) => [id, current[id] ?? 1])));
        } }} />
    </Modal>

    <Drawer open={selectedOrder !== undefined} onClose={() => setSelectedOrder(undefined)}
      title={selectedOrder?.dispositionNumber} width="min(1180px, 96vw)">
      {selectedOrder && <>
        <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small" items={[
          { key: 'type', label: english ? 'Type' : '类型', children: selectedOrder.dispositionType },
          { key: 'status', label: english ? 'Status' : '状态', children: <Tag color={statusColor(selectedOrder.status)}>{selectedOrder.status}</Tag> },
          { key: 'reason', label: english ? 'Reason' : '原因', children: `${selectedOrder.reasonCode}${selectedOrder.reasonDetail ? ` · ${selectedOrder.reasonDetail}` : ''}` },
          { key: 'actor', label: english ? 'Created by' : '制单人', children: selectedOrder.requestedByName },
          { key: 'external', label: english ? 'External reference' : '外部参考号', children: selectedOrder.externalReference ?? '-' },
          { key: 'recall', label: english ? 'Recall notice' : '召回通知号', children: selectedOrder.recallNoticeNumber ?? '-' },
          { key: 'reviewer', label: english ? 'Reviewer' : '审批人', children: selectedOrder.reviewedByName ?? '-' },
          { key: 'review', label: english ? 'Review comment' : '审批意见', children: selectedOrder.reviewComment ?? '-' },
          { key: 'cancel', label: english ? 'Cancellation' : '取消记录', children: selectedOrder.cancelReason ?? '-' },
        ]} />
        <Table columns={lineColumns} dataSource={selectedOrder.lines} pagination={false}
          rowKey="id" scroll={{ x: 1490 }} size="small" style={{ marginTop: 16 }} />
        <Space wrap style={{ marginTop: 16 }}>
          {canCreate && selectedOrder.status === 'DRAFT'
            && <Button loading={submitMutation.isPending} onClick={() => void submitDraft(selectedOrder)} type="primary">{english ? 'Submit' : '提交处置单'}</Button>}
          {canReview && selectedOrder.status === 'PENDING_REVIEW' && <>
            <Button icon={<CheckOutlined />} onClick={() => setDecision({ order: selectedOrder, action: 'approve' })} type="primary">{english ? 'Approve' : '批准'}</Button>
            <Button danger onClick={() => setDecision({ order: selectedOrder, action: 'reject' })}>{english ? 'Reject' : '驳回'}</Button>
          </>}
          {canCreate && ['DRAFT', 'PENDING_REVIEW', 'READY_TO_EXECUTE', 'PARTIALLY_EXECUTED'].includes(selectedOrder.status)
            && <Button icon={<StopOutlined />} onClick={() => setDecision({ order: selectedOrder, action: 'cancel' })}>{english ? 'Cancel remaining' : '取消剩余数量'}</Button>}
          {canExecute && ['READY_TO_EXECUTE', 'PARTIALLY_EXECUTED'].includes(selectedOrder.status)
            && <Button icon={<DeleteOutlined />} onClick={() => openExecution(selectedOrder)} type="primary">{english ? 'Execute physical disposal' : '执行实物处置'}</Button>}
        </Space>
        {selectedOrder.executions.length > 0 && <>
          <Typography.Title level={5} style={{ marginTop: 24 }}>{english ? 'Immutable execution history' : '不可变执行记录'}</Typography.Title>
          {selectedOrder.executions.map((execution) => <Descriptions key={execution.id} bordered size="small" column={{ xs: 1, sm: 3 }} style={{ marginBottom: 12 }} items={[
            { key: 'number', label: english ? 'Execution' : '执行单', children: execution.executionNumber },
            { key: 'actor', label: english ? 'Operator' : '执行人', children: execution.executedByName },
            { key: 'time', label: english ? 'Time' : '时间', children: new Date(execution.executedAt).toLocaleString(i18n.language) },
            { key: 'qty', label: english ? 'Quantity' : '数量', children: execution.lines.reduce((sum, line) => sum + line.quantity, 0) },
            { key: 'note', label: english ? 'Note' : '说明', children: execution.executionNote ?? '-', span: 2 },
          ]} />)}
        </>}
      </>}
    </Drawer>

    <Modal open={decision !== undefined} onCancel={() => setDecision(undefined)}
      onOk={() => void submitDecision()} confirmLoading={decisionMutation.isPending}
      title={decision ? `${decision.action.toUpperCase()} · ${decision.order.dispositionNumber}` : ''}>
      <Form form={decisionForm} layout="vertical"><Form.Item label={english ? 'Required comment' : '处理意见'} name="comment" rules={[{ required: true }]}><Input.TextArea maxLength={500} rows={4} showCount /></Form.Item></Form>
    </Modal>

    <Modal open={executeOrder !== undefined} onCancel={() => setExecuteOrder(undefined)}
      onOk={() => void submitExecution()} confirmLoading={executeMutation.isPending}
      width="min(940px, 96vw)" title={english ? 'Confirm physical disposal' : '确认实物处置'}>
      <Alert showIcon type="error" message={english ? 'This action reduces physical on-hand and cannot delete its ledger history.' : '本操作会扣减实存在库，生成的库存流水不可删除。'} />
      <Table style={{ marginTop: 12 }} pagination={false} rowKey="id" size="small"
        dataSource={executeOrder?.lines.filter((line) => line.outstandingQuantity > 0) ?? []}
        columns={[
          { title: 'SKU', dataIndex: 'productSkuCode' },
          { title: english ? 'Batch' : '批次', dataIndex: 'resultBatchCode' },
          { title: english ? 'Remaining' : '剩余', dataIndex: 'outstandingQuantity', align: 'right' },
          { title: english ? 'Execute now' : '本次执行', render: (_: unknown, line: InventoryDisposition['lines'][number]) => <InputNumber
            min={0} max={line.outstandingQuantity} value={executeQuantities[line.id] ?? 0}
            onChange={(value) => setExecuteQuantities((current) => ({ ...current, [line.id]: value ?? 0 }))} /> },
        ]} />
      <Form form={executeForm} layout="vertical" style={{ marginTop: 12 }}><Form.Item label={english ? 'Execution note (optional)' : '执行说明（选填）'} name="executionNote"><Input.TextArea maxLength={500} rows={3} showCount /></Form.Item></Form>
    </Modal>

    <Modal open={policyOpen} onCancel={() => setPolicyOpen(false)} onOk={() => void submitPolicy()}
      confirmLoading={policyMutation.isPending} title={english ? 'Inventory disposition policy' : '库存处置策略'}>
      <Alert showIcon type="info" message={english ? 'Approval is tenant-optional and not tied to a package.' : '处置审批由租户自行选择开启，不与套餐强制绑定。'} />
      <Form form={policyForm} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item label={english ? 'Final disposal mode' : '最终处置模式'} name="reviewMode" rules={[{ required: true }]}><Select options={[
          { value: 'DIRECT_WITH_REASON', label: english ? 'Direct with required reason' : '填写原因后直接进入待执行' },
          { value: 'APPROVAL_REQUIRED', label: english ? 'Two-person approval required' : '必须双人审批' },
        ]} /></Form.Item>
        <Form.Item label={english ? 'Automatic expiry' : '自动转过期'} name="automaticExpiryEnabled" valuePropName="checked"><Switch /></Form.Item>
        <Form.Item label={english ? 'Business timezone' : '业务时区'} name="businessTimezone" rules={[{ required: true }]} extra={english ? 'IANA identifier; default Europe/London handles GMT/BST automatically.' : '使用 IANA 时区标识；默认 Europe/London，会自动处理 GMT/BST 夏令时。'}><Input maxLength={60} /></Form.Item>
        <Form.Item label={english ? 'Change reason' : '变更原因'} name="reason" rules={[{ required: true }]}><Input.TextArea maxLength={500} /></Form.Item>
      </Form>
    </Modal>
  </section>;
}
