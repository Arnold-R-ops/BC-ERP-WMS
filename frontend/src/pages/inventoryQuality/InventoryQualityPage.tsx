import {
  CheckOutlined, EyeOutlined, LockOutlined,
  SettingOutlined, UnlockOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, App as AntdApp, Button, Descriptions, Drawer, Form, Input, InputNumber,
  Modal, Radio, Select, Space, Table, Tabs, Tag, Typography, type TableColumnsType,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { getErrorMessage } from '../../api/errors';
import {
  createInventoryQualityHold,
  createInventoryQualityIdempotencyKey,
  createInventoryQualityRelease,
  getInventoryQualityPolicy,
  INVENTORY_QUALITY_QUERY_KEY,
  listInventoryQualityActions,
  listInventoryQualityCandidates,
  previewInventoryQualityHold,
  reviewInventoryQualityAction,
  updateInventoryQualityPolicy,
  type InventoryQualityAction,
  type InventoryQualityActionStatus,
  type InventoryQualityCandidate,
  type InventoryQualityHoldPayload,
  type InventoryQualityHoldType,
  type InventoryQualityReleaseMode,
  type InventoryQualityReleasePayload,
} from '../../api/inventoryQuality';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';

type Operation = 'hold' | 'release';
type ActionFilter = 'ALL' | InventoryQualityActionStatus;

interface OperationFormValues {
  holdType: InventoryQualityHoldType;
  reasonCode: string;
  reasonDetail?: string;
  recallNoticeNumber?: string;
  externalReference?: string;
}

interface ReviewState {
  action: InventoryQualityAction;
  decision: 'approve' | 'reject' | 'withdraw';
}

function statusColor(status: InventoryQualityActionStatus): string {
  if (status === 'COMPLETED' || status === 'APPROVED') return 'success';
  if (status === 'PENDING_REVIEW') return 'processing';
  if (status === 'REJECTED') return 'error';
  return 'default';
}

export function InventoryQualityPage(): JSX.Element {
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const english = i18n.language === 'en-US';
  const { message, modal } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [filter, setFilter] = useState<ActionFilter>('ALL');
  const [operation, setOperation] = useState<Operation>();
  const [candidateSearch, setCandidateSearch] = useState(searchParams.get('search') ?? '');
  const [selectedBatchIds, setSelectedBatchIds] = useState<number[]>([]);
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [selectedAction, setSelectedAction] = useState<InventoryQualityAction>();
  const [reviewState, setReviewState] = useState<ReviewState>();
  const [policyOpen, setPolicyOpen] = useState(false);
  const [operationForm] = Form.useForm<OperationFormValues>();
  const [reviewForm] = Form.useForm<{ comment: string }>();
  const [policyForm] = Form.useForm<{ releaseMode: InventoryQualityReleaseMode; reason: string }>();

  const isAdmin = session?.currentRole === 'TENANT_ADMIN';
  const canHold = isAdmin || hasPermission(session?.currentRole, session?.permissionCodes, 'inventory_quality:hold');
  const canRelease = isAdmin || hasPermission(session?.currentRole, session?.permissionCodes, 'inventory_quality:release');
  const canReview = isAdmin || hasPermission(session?.currentRole, session?.permissionCodes, 'inventory_quality:review');
  const canPolicy = isAdmin || hasPermission(session?.currentRole, session?.permissionCodes, 'inventory_quality:policy');

  const actionsQuery = useQuery({
    queryKey: [...INVENTORY_QUALITY_QUERY_KEY, 'actions', filter],
    queryFn: () => listInventoryQualityActions(undefined, filter === 'ALL' ? undefined : filter),
  });
  const policyQuery = useQuery({
    queryKey: [...INVENTORY_QUALITY_QUERY_KEY, 'policy'],
    queryFn: getInventoryQualityPolicy,
  });
  const candidatesQuery = useQuery({
    queryKey: [...INVENTORY_QUALITY_QUERY_KEY, 'candidates', candidateSearch],
    queryFn: () => listInventoryQualityCandidates(candidateSearch),
    enabled: operation !== undefined,
  });

  useEffect(() => {
    const batchId = Number(searchParams.get('batchId'));
    if (batchId > 0 && canHold && operation === undefined) {
      setOperation('hold');
      setSelectedBatchIds([batchId]);
      setQuantities({ [batchId]: 1 });
      setIdempotencyKey(createInventoryQualityIdempotencyKey('hold'));
      operationForm.setFieldsValue({ holdType: 'QUARANTINE', reasonCode: 'QUALITY_RISK' });
    }
  }, [canHold, operation, operationForm, searchParams]);

  const candidates = useMemo(() => (candidatesQuery.data ?? []).filter((candidate) => (
    operation === 'release'
      ? candidate.qualityStatus === 'QUARANTINED' || candidate.qualityStatus === 'RECALL_HOLD'
      : candidate.qualityStatus === 'AVAILABLE'
  )), [candidatesQuery.data, operation]);

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: INVENTORY_QUALITY_QUERY_KEY });
  };

  const openOperation = (next: Operation): void => {
    setOperation(next);
    setSelectedBatchIds([]);
    setQuantities({});
    setCandidateSearch('');
    setIdempotencyKey(createInventoryQualityIdempotencyKey(next));
    operationForm.resetFields();
    operationForm.setFieldsValue({
      holdType: 'QUARANTINE',
      reasonCode: next === 'hold' ? 'QUALITY_RISK' : 'RISK_CLEARED',
    });
  };

  const closeOperation = (): void => {
    setOperation(undefined);
    setSelectedBatchIds([]);
    setQuantities({});
    setSearchParams((current) => {
      current.delete('batchId'); current.delete('search'); return current;
    }, { replace: true });
  };

  const selectedLines = (): { batchId: number; quantity: number }[] => selectedBatchIds.map((batchId) => ({
    batchId,
    quantity: quantities[batchId] ?? 1,
  }));

  const holdMutation = useMutation({
    mutationFn: ({ payload, key }: { payload: InventoryQualityHoldPayload; key: string }) => (
      createInventoryQualityHold(payload, key)
    ),
  });
  const releaseMutation = useMutation({
    mutationFn: ({ payload, key }: { payload: InventoryQualityReleasePayload; key: string }) => (
      createInventoryQualityRelease(payload, key)
    ),
  });
  const reviewMutation = useMutation({
    mutationFn: ({ state, comment }: { state: ReviewState; comment: string }) => (
      reviewInventoryQualityAction(state.action.id, state.decision, comment)
    ),
  });
  const policyMutation = useMutation({
    mutationFn: ({ releaseMode, reason }: { releaseMode: InventoryQualityReleaseMode; reason: string }) => (
      updateInventoryQualityPolicy(releaseMode, reason)
    ),
  });

  const submitOperation = async (): Promise<void> => {
    try {
      const values = await operationForm.validateFields();
      if (selectedBatchIds.length === 0) {
        message.warning(english ? 'Select at least one batch.' : '请至少选择一个库存批次');
        return;
      }
      const invalid = selectedBatchIds.some((id) => (quantities[id] ?? 0) <= 0);
      if (invalid) {
        message.warning(english ? 'Every quantity must be greater than zero.' : '每个处理数量都必须大于 0');
        return;
      }
      if (operation === 'hold') {
        const base: InventoryQualityHoldPayload = {
          holdType: values.holdType,
          lines: selectedLines(),
          reasonCode: values.reasonCode,
          reasonDetail: values.reasonDetail,
          recallNoticeNumber: values.recallNoticeNumber,
          externalReference: values.externalReference,
          confirmReservationReallocation: false,
        };
        const preview = await previewInventoryQualityHold(base);
        if (!preview.executable) {
          message.error(english
            ? `Cannot freeze: reallocation shortage ${preview.totalShortageQuantity}.`
            : `无法冻结：销售预留可改派库存短缺 ${preview.totalShortageQuantity} 件`);
          return;
        }
        const execute = async (): Promise<void> => {
          const action = await holdMutation.mutateAsync({
            payload: { ...base, confirmReservationReallocation: preview.confirmationRequired },
            key: idempotencyKey,
          });
          message.success(english ? 'Inventory hold completed.' : '库存冻结已完成');
          setSelectedAction(action); closeOperation(); await refresh();
        };
        if (preview.confirmationRequired) {
          modal.confirm({
            title: english ? 'Confirm sales reallocation and hold' : '确认改派销售预留并冻结',
            content: english
              ? `${preview.totalReservationQuantityToReallocate} reserved units will be moved to other saleable batches. The hold and all moves run atomically.`
              : `将把 ${preview.totalReservationQuantityToReallocate} 件销售预留改派到其他可售批次。改派与冻结将作为同一事务执行。`,
            okText: english ? 'Reallocate and freeze' : '改派并冻结',
            onOk: execute,
          });
        } else {
          await execute();
        }
      } else {
        const action = await releaseMutation.mutateAsync({
          payload: {
            lines: selectedLines(), reasonCode: values.reasonCode,
            reasonDetail: values.reasonDetail, externalReference: values.externalReference,
          },
          key: idempotencyKey,
        });
        message.success(action.status === 'PENDING_REVIEW'
          ? (english ? 'Release request submitted for review.' : '恢复可售申请已提交审批')
          : (english ? 'Stock restored to saleable.' : '库存已恢复可售'));
        setSelectedAction(action); closeOperation(); await refresh();
      }
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(getErrorMessage(error, t));
    }
  };

  const submitReview = async (): Promise<void> => {
    if (!reviewState) return;
    try {
      const { comment } = await reviewForm.validateFields();
      const updated = await reviewMutation.mutateAsync({ state: reviewState, comment });
      setSelectedAction(updated); setReviewState(undefined); reviewForm.resetFields();
      message.success(english ? 'Action updated.' : '处理结果已更新');
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
      queryClient.setQueryData([...INVENTORY_QUALITY_QUERY_KEY, 'policy'], updated);
      setPolicyOpen(false);
      message.success(english ? 'Release policy updated.' : '恢复可售策略已更新');
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(getErrorMessage(error, t));
    }
  };

  const actionColumns: TableColumnsType<InventoryQualityAction> = [
    { title: english ? 'Action no.' : '处理单号', dataIndex: 'actionNumber', width: 220, fixed: 'left' },
    { title: english ? 'Type' : '类型', width: 130, render: (_, row) => row.actionType === 'HOLD' ? (row.holdType === 'RECALL' ? (english ? 'Recall hold' : '召回冻结') : (english ? 'Quarantine' : '人工冻结')) : (english ? 'Release' : '恢复可售') },
    { title: english ? 'Status' : '状态', dataIndex: 'status', width: 140, render: (status: InventoryQualityActionStatus) => <Tag color={statusColor(status)}>{status}</Tag> },
    { title: english ? 'Reason' : '原因', dataIndex: 'reasonCode', width: 190 },
    { title: english ? 'Quantity' : '数量', width: 90, align: 'right', render: (_, row) => row.lines.reduce((sum, line) => sum + line.quantity, 0) },
    { title: english ? 'Warehouses' : '涉及仓库', width: 220, render: (_, row) => [...new Set(row.lines.map((line) => line.warehouseName))].join('、') },
    { title: english ? 'Operator' : '制单人', dataIndex: 'requestedByName', width: 130 },
    { title: english ? 'Time' : '制单时间', dataIndex: 'requestedAt', width: 190, render: (value: string) => new Date(value).toLocaleString(i18n.language) },
    { title: english ? 'Actions' : '操作', fixed: 'right', width: 110, render: (_, row) => <Button icon={<EyeOutlined />} onClick={() => setSelectedAction(row)} size="small" type="link">{english ? 'View' : '查看'}</Button> },
  ];

  const candidateColumns: TableColumnsType<InventoryQualityCandidate> = [
    { title: english ? 'SKU / Product' : 'SKU / 商品', width: 220, render: (_, row) => <Space direction="vertical" size={0}><strong>{row.productSkuCode}</strong><span>{row.productName}</span></Space> },
    { title: english ? 'Batch' : '批次', dataIndex: 'batchCode', width: 180 },
    { title: english ? 'Warehouse / Location' : '仓库 / 库位', width: 220, render: (_, row) => `${row.warehouseName} / ${row.locationCode}` },
    { title: english ? 'Quality status' : '质量状态', dataIndex: 'qualityStatus', width: 150, render: (value: string) => <Tag>{value}</Tag> },
    { title: english ? 'On hand' : '在手', dataIndex: 'quantity', align: 'right', width: 85 },
    { title: english ? 'Reserved' : '预留', dataIndex: 'reservedQuantity', align: 'right', width: 85 },
    { title: english ? 'Process qty' : '处理数量', width: 120, render: (_, row) => <InputNumber min={1} max={row.quantity} onChange={(value) => setQuantities((current) => ({ ...current, [row.batchId]: value ?? 1 }))} size="small" value={quantities[row.batchId] ?? 1} /> },
  ];

  const lineColumns: TableColumnsType<InventoryQualityAction['lines'][number]> = [
    { title: 'SKU', dataIndex: 'productSkuCode', width: 130 },
    { title: english ? 'Product' : '商品', dataIndex: 'productName', width: 180 },
    { title: english ? 'Source batch' : '来源批次', dataIndex: 'sourceBatchCode', width: 180 },
    { title: english ? 'Result segment' : '结果分段', dataIndex: 'resultBatchCode', width: 180, render: (value?: string) => value ?? '-' },
    { title: english ? 'Warehouse / Location' : '仓库 / 库位', width: 220, render: (_, row) => `${row.warehouseName} / ${row.locationCode}` },
    { title: english ? 'Quantity' : '数量', dataIndex: 'quantity', width: 90, align: 'right' },
    { title: english ? 'Reservation moved' : '改派预留', dataIndex: 'reallocatedReservedQuantity', width: 110, align: 'right' },
    { title: english ? 'State change' : '状态变化', width: 230, render: (_, row) => `${row.fromStatus} → ${row.toStatus}` },
  ];

  return <section className="data-page workflow-page">
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        message={english ? 'Quality holds do not reduce on-hand inventory.' : '冻结不会扣减在手库存'}
        description={english ? 'A partial hold creates a linked quality segment. Reserved sales stock is changed only after an explicit impact preview and confirmation.' : '部分冻结会形成可追溯的质量分段；只有在影响预览和明确确认后，系统才会改派销售预留。'}
        showIcon type="info"
      />
      <Tabs activeKey={filter} onChange={(key) => setFilter(key as ActionFilter)} items={['ALL', 'PENDING_REVIEW', 'COMPLETED', 'APPROVED', 'REJECTED', 'WITHDRAWN'].map((key) => ({ key, label: key === 'ALL' ? (english ? 'All' : '全部') : key }))} />
      <Table
        columns={actionColumns}
        dataSource={actionsQuery.data ?? []}
        loading={actionsQuery.isLoading}
        rowKey="id"
        scroll={{ x: 1490 }}
        title={() => <Space wrap>
          <Typography.Title level={4} style={{ margin: 0 }}>{english ? 'Quality handling records' : '质量处理记录'}</Typography.Title>
          {canPolicy && <Button icon={<SettingOutlined />} onClick={() => { policyForm.setFieldsValue({ releaseMode: policyQuery.data?.releaseMode ?? 'DIRECT_WITH_REASON' }); setPolicyOpen(true); }}>{english ? 'Release policy' : '恢复策略'}</Button>}
          {canHold && <Button icon={<LockOutlined />} onClick={() => openOperation('hold')} type="primary">{english ? 'Freeze inventory' : '冻结库存'}</Button>}
          {canRelease && <Button icon={<UnlockOutlined />} onClick={() => openOperation('release')}>{english ? 'Restore saleable' : '恢复可售'}</Button>}
        </Space>}
      />
    </Space>

    <Modal
      destroyOnHidden
      okButtonProps={{ loading: holdMutation.isPending || releaseMutation.isPending }}
      onCancel={closeOperation}
      onOk={() => void submitOperation()}
      open={operation !== undefined}
      title={operation === 'hold' ? (english ? 'Freeze inventory' : '冻结库存') : (english ? 'Restore saleable' : '恢复可售')}
      width="min(1120px, 96vw)"
    >
      <Form form={operationForm} layout="vertical">
        {operation === 'hold' && <Form.Item label={english ? 'Hold type' : '冻结类型'} name="holdType" rules={[{ required: true }]}>
          <Radio.Group options={[{ value: 'QUARANTINE', label: english ? 'Quarantine' : '人工冻结' }, { value: 'RECALL', label: english ? 'Recall hold' : '召回冻结' }]} />
        </Form.Item>}
        <Space align="start" wrap style={{ width: '100%' }}>
          <Form.Item label={english ? 'Standard reason' : '标准原因'} name="reasonCode" rules={[{ required: true }]}><Select style={{ minWidth: 220 }} options={(operation === 'hold' ? [
            { value: 'QUALITY_RISK', label: english ? 'Quality risk' : '质量风险' },
            { value: 'RECALL_NOTICE', label: english ? 'Recall notice' : '收到召回通知' },
            { value: 'TRACEABILITY_EXCEPTION', label: english ? 'Traceability exception' : '追溯信息异常' },
            { value: 'STORAGE_CONDITION_EXCEPTION', label: english ? 'Storage-condition exception' : '储存条件异常' },
            { value: 'MANUAL_CONTROL', label: english ? 'Manual risk control' : '人工风险管控' },
          ] : [
            { value: 'RISK_CLEARED', label: english ? 'Risk cleared' : '风险已解除' },
            { value: 'RECALL_CLOSED', label: english ? 'Recall closed' : '召回已关闭' },
            { value: 'VERIFICATION_PASSED', label: english ? 'Verification passed' : '复核通过' },
            { value: 'FALSE_ALARM_CORRECTION', label: english ? 'False-alarm correction' : '误冻结纠正' },
          ])} /></Form.Item>
          <Form.Item label={english ? 'External reference (optional)' : '外部参考号（选填）'} name="externalReference"><Input maxLength={120} /></Form.Item>
          {operation === 'hold' && <Form.Item label={english ? 'Recall notice no. (optional)' : '召回通知号（选填）'} name="recallNoticeNumber"><Input maxLength={120} /></Form.Item>}
        </Space>
        <Form.Item label={english ? 'Details (optional)' : '补充说明（选填）'} name="reasonDetail"><Input.TextArea maxLength={500} rows={2} showCount /></Form.Item>
      </Form>
      <Input.Search allowClear enterButton onSearch={setCandidateSearch} placeholder={english ? 'Search SKU, product, internal/external batch' : '搜索 SKU、商品、内部/外部批次号'} />
      <Table
        columns={candidateColumns}
        dataSource={candidates}
        loading={candidatesQuery.isLoading}
        pagination={{ pageSize: 6 }}
        rowKey="batchId"
        rowSelection={{
          selectedRowKeys: selectedBatchIds,
          onChange: (keys) => {
            const ids = keys.map(Number); setSelectedBatchIds(ids);
            setQuantities((current) => Object.fromEntries(ids.map((id) => [id, current[id] ?? 1])));
          },
        }}
        scroll={{ x: 1120 }}
        size="small"
      />
    </Modal>

    <Drawer onClose={() => setSelectedAction(undefined)} open={selectedAction !== undefined} title={selectedAction?.actionNumber} width="min(1120px, 94vw)">
      {selectedAction && <>
        <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small" items={[
          { key: 'type', label: english ? 'Type' : '类型', children: `${selectedAction.actionType}${selectedAction.holdType ? ` / ${selectedAction.holdType}` : ''}` },
          { key: 'status', label: english ? 'Status' : '状态', children: <Tag color={statusColor(selectedAction.status)}>{selectedAction.status}</Tag> },
          { key: 'reason', label: english ? 'Reason' : '原因', children: `${selectedAction.reasonCode}${selectedAction.reasonDetail ? ` · ${selectedAction.reasonDetail}` : ''}` },
          { key: 'actor', label: english ? 'Operator' : '操作人', children: selectedAction.requestedByName },
          { key: 'recall', label: english ? 'Recall notice' : '召回通知号', children: selectedAction.recallNoticeNumber ?? '-' },
          { key: 'external', label: english ? 'External reference' : '外部参考号', children: selectedAction.externalReference ?? '-' },
          { key: 'reviewer', label: english ? 'Reviewer' : '审批人', children: selectedAction.reviewedByName ?? '-' },
          { key: 'comment', label: english ? 'Review comment' : '审批意见', children: selectedAction.reviewComment ?? '-' },
        ]} />
        <Table columns={lineColumns} dataSource={selectedAction.lines} pagination={false} rowKey="id" scroll={{ x: 1340 }} size="small" style={{ marginTop: 16 }} />
        {selectedAction.status === 'PENDING_REVIEW' && <Space style={{ marginTop: 16 }}>
          {canReview && <Button icon={<CheckOutlined />} onClick={() => setReviewState({ action: selectedAction, decision: 'approve' })} type="primary">{english ? 'Approve' : '批准恢复'}</Button>}
          {canReview && <Button danger onClick={() => setReviewState({ action: selectedAction, decision: 'reject' })}>{english ? 'Reject' : '拒绝'}</Button>}
          {canRelease && <Button onClick={() => setReviewState({ action: selectedAction, decision: 'withdraw' })}>{english ? 'Withdraw' : '撤回申请'}</Button>}
        </Space>}
      </>}
    </Drawer>

    <Modal confirmLoading={reviewMutation.isPending} onCancel={() => setReviewState(undefined)} onOk={() => void submitReview()} open={reviewState !== undefined} title={reviewState ? `${reviewState.decision.toUpperCase()} · ${reviewState.action.actionNumber}` : ''}>
      <Form form={reviewForm} layout="vertical"><Form.Item label={english ? 'Comment' : '处理意见'} name="comment" rules={[{ required: true }]}><Input.TextArea maxLength={500} rows={4} showCount /></Form.Item></Form>
    </Modal>

    <Modal confirmLoading={policyMutation.isPending} onCancel={() => setPolicyOpen(false)} onOk={() => void submitPolicy()} open={policyOpen} title={english ? 'Release-to-saleable policy' : '恢复可售策略'}>
      <Alert message={english ? 'This switch is tenant-specific and is not tied to the subscription package.' : '该开关按租户独立设置，不与套餐强制绑定。'} showIcon type="info" />
      <Form form={policyForm} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item label={english ? 'Mode' : '处理模式'} name="releaseMode" rules={[{ required: true }]}><Select options={[
          { value: 'DIRECT_WITH_REASON', label: english ? 'Direct with required reason' : '填写原因后直接恢复' },
          { value: 'APPROVAL_REQUIRED', label: english ? 'Two-person approval required' : '必须双人审批' },
        ]} /></Form.Item>
        <Form.Item label={english ? 'Change reason' : '变更原因'} name="reason" rules={[{ required: true }]}><Input.TextArea maxLength={500} /></Form.Item>
      </Form>
    </Modal>
  </section>;
}
