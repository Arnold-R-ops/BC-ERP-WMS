import {
  AuditOutlined, CheckOutlined, ExperimentOutlined, ReloadOutlined,
  SettingOutlined, StopOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, App as AntdApp, Button, Card, Drawer, Form, Input, InputNumber,
  Modal, Radio, Select, Space, Switch, Table, Tag, Typography,
  type TableColumnsType,
} from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import {
  createReceivingReinspection, getReceivingInspectionPolicy, inspectionIdempotencyKey,
  listReceivingInspections, RECEIVING_INSPECTION_QUERY_KEY, reviewReceivingInspection,
  submitReceivingInspection, updateReceivingInspectionPolicy,
  type ReceivingInspectionMethod, type ReceivingInspectionResult,
  type ReceivingInspectionReviewMode, type ReceivingInspectionTask,
  type ReceivingInspectionTaskStatus,
} from '../../api/receivingInspection';
import { getErrorMessage } from '../../api/errors';
import { useAuth } from '../../auth/AuthProvider';

type Filter = 'ALL' | ReceivingInspectionTaskStatus;
interface Decision { passed: number; quarantined: number }
interface ExecuteValues {
  method: ReceivingInspectionMethod; sampleQuantity?: number; defectQuantity: number;
  reasonCode: string; reasonDetail?: string;
}
interface ReviewState { result: ReceivingInspectionResult; decision: 'approve' | 'reject' }
interface ReinspectionState {
  result: ReceivingInspectionResult; batchId: number; maxQuantity: number;
}

function color(status: ReceivingInspectionTaskStatus): string {
  if (status === 'COMPLETED') return 'success';
  if (status === 'PENDING_REVIEW') return 'processing';
  if (status === 'IN_PROGRESS') return 'warning';
  return 'default';
}

export function ReceivingInspectionPage({ compact = false }: { compact?: boolean }): JSX.Element {
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const english = i18n.language === 'en-US';
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [detail, setDetail] = useState<ReceivingInspectionTask>();
  const [executeTask, setExecuteTask] = useState<ReceivingInspectionTask>();
  const [decisions, setDecisions] = useState<Record<number, Decision>>({});
  const [review, setReview] = useState<ReviewState>();
  const [reinspection, setReinspection] = useState<ReinspectionState>();
  const [policyOpen, setPolicyOpen] = useState(false);
  const [executeForm] = Form.useForm<ExecuteValues>();
  const [reviewForm] = Form.useForm<{ comment: string }>();
  const [correctionForm] = Form.useForm<{
    quantity: number; reason: string; confirmReservationReallocation: boolean;
  }>();
  const [policyForm] = Form.useForm<{
    defaultInspectionRequired: boolean; reviewMode: ReceivingInspectionReviewMode; reason: string;
  }>();

  const admin = session?.currentRole === 'TENANT_ADMIN';
  const permitted = (code: string): boolean => admin || hasPermission(
    session?.currentRole, session?.permissionCodes, code);
  const canExecute = permitted('receiving_inspection:execute');
  const canReview = permitted('receiving_inspection:review');
  const canCorrect = permitted('receiving_inspection:correct');
  const canPolicy = permitted('receiving_inspection:policy');

  const tasksQuery = useQuery({
    queryKey: [...RECEIVING_INSPECTION_QUERY_KEY, filter],
    queryFn: () => listReceivingInspections(filter === 'ALL' ? undefined : filter),
  });
  const policyQuery = useQuery({
    queryKey: [...RECEIVING_INSPECTION_QUERY_KEY, 'policy'],
    queryFn: getReceivingInspectionPolicy,
  });
  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: RECEIVING_INSPECTION_QUERY_KEY });
  };

  const submitMutation = useMutation({
    mutationFn: ({ task, values }: { task: ReceivingInspectionTask; values: ExecuteValues }) =>
      submitReceivingInspection(task.id, {
        inspectionMethod: values.method,
        sampleQuantity: values.method === 'SAMPLING' ? values.sampleQuantity : undefined,
        defectQuantity: values.defectQuantity,
        reasonCode: values.reasonCode,
        reasonDetail: values.reasonDetail,
        lines: task.lines.filter((line) => {
          const value = decisions[line.id];
          return value && value.passed + value.quarantined > 0;
        }).map((line) => ({
          inspectionTaskLineId: line.id,
          passedQuantity: decisions[line.id].passed,
          quarantinedQuantity: decisions[line.id].quarantined,
        })),
      }, inspectionIdempotencyKey('submit')),
  });
  const reviewMutation = useMutation({
    mutationFn: ({ state, comment }: { state: ReviewState; comment: string }) =>
      reviewReceivingInspection(
        state.result.id, state.decision, comment, inspectionIdempotencyKey('review')),
  });
  const correctionMutation = useMutation({
    mutationFn: (values: {
      quantity: number; reason: string; confirmReservationReallocation: boolean;
    }) => createReceivingReinspection({
      originalResultId: reinspection!.result.id,
      inventoryBatchId: reinspection!.batchId,
      quantity: values.quantity,
      reason: values.reason,
      confirmReservationReallocation: values.confirmReservationReallocation,
    }, inspectionIdempotencyKey('correction')),
  });
  const policyMutation = useMutation({
    mutationFn: (values: {
      defaultInspectionRequired: boolean; reviewMode: ReceivingInspectionReviewMode; reason: string;
    }) => updateReceivingInspectionPolicy(
      values.defaultInspectionRequired, values.reviewMode, values.reason),
  });

  const openExecute = (task: ReceivingInspectionTask): void => {
    setExecuteTask(task);
    setDecisions(Object.fromEntries(task.lines.map((line) => [
      line.id, { passed: line.pendingQuantity, quarantined: 0 },
    ])));
    executeForm.setFieldsValue({
      method: 'FULL', defectQuantity: 0, reasonCode: 'INSPECTION_PASSED',
    });
  };

  const submit = async (): Promise<void> => {
    if (!executeTask) return;
    try {
      const values = await executeForm.validateFields();
      const selected = executeTask.lines.filter((line) => {
        const value = decisions[line.id];
        return value && value.passed + value.quarantined > 0;
      });
      if (selected.length === 0 || selected.some((line) => {
        const value = decisions[line.id];
        return value.passed < 0 || value.quarantined < 0
          || value.passed + value.quarantined > line.pendingQuantity;
      })) {
        message.warning(english ? 'Enter a valid quantity.' : '请填写有效数量且不能超过待检数量');
        return;
      }
      await submitMutation.mutateAsync({ task: executeTask, values });
      message.success(english ? 'Inspection submitted.' : '质检结果已提交');
      setExecuteTask(undefined); await refresh();
    } catch (error) {
      if (error instanceof Error) message.error(getErrorMessage(error, t));
    }
  };

  const columns = useMemo<TableColumnsType<ReceivingInspectionTask>>(() => [
    { title: english ? 'Task' : '质检任务', dataIndex: 'taskNumber', width: 210 },
    { title: english ? 'Source' : '来源单', dataIndex: 'sourceReference', width: 150 },
    { title: english ? 'Warehouse' : '仓库', dataIndex: 'warehouseName', width: 160 },
    {
      title: english ? 'Progress' : '进度', width: 160,
      render: (_, task) => {
        const total = task.lines.reduce((sum, line) => sum + line.plannedQuantity, 0);
        const waiting = task.lines.reduce(
          (sum, line) => sum + line.pendingQuantity + line.pendingReviewQuantity, 0);
        return String(total - waiting) + ' / ' + String(total);
      },
    },
    {
      title: english ? 'Status' : '状态', dataIndex: 'status', width: 150,
      render: (status: ReceivingInspectionTaskStatus) => <Tag color={color(status)}>{status}</Tag>,
    },
    {
      title: english ? 'Actions' : '操作', width: 210,
      render: (_, task) => <Space>
        <Button onClick={() => setDetail(task)} size="small">{english ? 'Details' : '详情'}</Button>
        {canExecute && task.lines.some((line) => line.pendingQuantity > 0) ? (
          <Button icon={<ExperimentOutlined />} onClick={() => openExecute(task)}
            size="small" type="primary">{english ? 'Inspect' : '执行质检'}</Button>
        ) : null}
      </Space>,
    },
  ], [canExecute, english]);

  const lineColumns: TableColumnsType<ReceivingInspectionTask['lines'][number]> = [
    { title: 'SKU', dataIndex: 'skuCode' },
    { title: english ? 'Batch' : '批次', dataIndex: 'batchCode' },
    { title: english ? 'Location' : '库位', dataIndex: 'locationCode' },
    { title: english ? 'Pending' : '待检', dataIndex: 'pendingQuantity' },
    { title: english ? 'Review' : '待复核', dataIndex: 'pendingReviewQuantity' },
    { title: english ? 'Passed' : '合格', dataIndex: 'passedQuantity' },
    { title: english ? 'Quarantined' : '隔离', dataIndex: 'quarantinedQuantity' },
  ];

  return <section className={compact ? 'mobile-operation-page' : undefined}>
    <Typography.Title level={compact ? 3 : 2}>
      {english ? 'Receiving inspection' : '收货质检'}
    </Typography.Title>
    <Typography.Paragraph type="secondary">
      {english
        ? 'Pending stock remains on-hand; only passed quantities become saleable.'
        : '待检商品仍计入实际在库，只有合格数量才进入可售库存。'}
    </Typography.Paragraph>
    <Card style={{ marginBottom: 12 }}>
      <Space wrap>
        <Select<Filter> value={filter} onChange={setFilter} style={{ width: 180 }}
          options={['ALL', 'PENDING', 'IN_PROGRESS', 'PENDING_REVIEW', 'COMPLETED']
            .map((value) => ({ value, label: value }))} />
        <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>
          {english ? 'Refresh' : '刷新'}
        </Button>
        {canPolicy ? <Button icon={<SettingOutlined />} onClick={() => {
          policyForm.setFieldsValue({
            defaultInspectionRequired: policyQuery.data?.defaultInspectionRequired ?? false,
            reviewMode: policyQuery.data?.reviewMode ?? 'DIRECT',
          });
          setPolicyOpen(true);
        }}>{english ? 'Policy' : '质检策略'}</Button> : null}
      </Space>
    </Card>
    <Table columns={columns} dataSource={tasksQuery.data ?? []} loading={tasksQuery.isLoading}
      pagination={{ pageSize: compact ? 8 : 15 }} rowKey="id" scroll={{ x: 1040 }} />

    <Modal open={Boolean(executeTask)} onCancel={() => setExecuteTask(undefined)}
      onOk={() => void submit()} width={920} confirmLoading={submitMutation.isPending}
      title={english ? 'Submit inspection result' : '提交质检结果'}>
      <Alert showIcon type="info" style={{ marginBottom: 12 }}
        message={executeTask?.reviewMode === 'REVIEW_REQUIRED'
          ? (english ? 'Quarantine is immediate; passed stock waits for review.'
            : '隔离立即生效，合格数量等待另一名用户复核。')
          : (english ? 'The decision takes effect immediately.' : '质检结果立即生效。')} />
      <Form form={executeForm} layout="vertical">
        <Space wrap align="start">
          <Form.Item label={english ? 'Method' : '方式'} name="method" rules={[{ required: true }]}>
            <Radio.Group options={[
              { value: 'FULL', label: english ? 'Full' : '全检' },
              { value: 'SAMPLING', label: english ? 'Sampling' : '抽检' },
            ]} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate>
            {({ getFieldValue }) => getFieldValue('method') === 'SAMPLING' ? (
              <Form.Item label={english ? 'Sample' : '抽样数'} name="sampleQuantity"
                rules={[{ required: true }]}><InputNumber min={1} precision={0} /></Form.Item>
            ) : null}
          </Form.Item>
          <Form.Item label={english ? 'Defects' : '缺陷数'} name="defectQuantity"
            rules={[{ required: true }]}><InputNumber min={0} precision={0} /></Form.Item>
          <Form.Item label={english ? 'Result' : '标准结果'} name="reasonCode"
            rules={[{ required: true }]}>
            <Select style={{ width: 190 }} options={[
              'INSPECTION_PASSED', 'INSPECTION_PARTIAL', 'QUALITY_FAILED',
              'DAMAGE_FOUND', 'EXPIRY_FAILED', 'REINSPECTION_DECISION', 'OTHER',
            ].map((value) => ({ value, label: value }))} />
          </Form.Item>
        </Space>
        <Form.Item label={english ? 'Notes' : '质检说明'} name="reasonDetail">
          <Input.TextArea maxLength={500} rows={2} />
        </Form.Item>
      </Form>
      <Table dataSource={executeTask?.lines ?? []} pagination={false} rowKey="id"
        scroll={{ x: 760 }} columns={[
          { title: 'SKU', dataIndex: 'skuCode' },
          { title: english ? 'Batch' : '批次', dataIndex: 'batchCode' },
          { title: english ? 'Pending' : '待检', dataIndex: 'pendingQuantity' },
          {
            title: english ? 'Pass' : '合格',
            render: (_, line) => <InputNumber min={0} max={line.pendingQuantity}
              value={decisions[line.id]?.passed ?? 0} precision={0}
              onChange={(value) => setDecisions((current) => ({
                ...current, [line.id]: {
                  ...(current[line.id] ?? { passed: 0, quarantined: 0 }),
                  passed: Number(value ?? 0),
                },
              }))} />,
          },
          {
            title: english ? 'Quarantine' : '隔离',
            render: (_, line) => <InputNumber min={0} max={line.pendingQuantity}
              value={decisions[line.id]?.quarantined ?? 0} precision={0}
              onChange={(value) => setDecisions((current) => ({
                ...current, [line.id]: {
                  ...(current[line.id] ?? { passed: 0, quarantined: 0 }),
                  quarantined: Number(value ?? 0),
                },
              }))} />,
          },
        ]} />
    </Modal>

    <Drawer open={Boolean(detail)} onClose={() => setDetail(undefined)}
      width={compact ? '100%' : 920} title={detail?.taskNumber}>
      {detail ? <>
        <Table columns={lineColumns} dataSource={detail.lines} pagination={false}
          rowKey="id" scroll={{ x: 820 }} />
        <Typography.Title level={4}>{english ? 'History' : '质检历史'}</Typography.Title>
        {detail.results.map((result) => <Card key={result.id} size="small"
          style={{ marginBottom: 10 }}
          title={<Space><span>{result.resultNumber}</span><Tag>{result.status}</Tag></Space>}
          extra={canReview && result.status === 'PENDING_REVIEW' ? <Space>
            <Button icon={<CheckOutlined />} size="small"
              onClick={() => { setReview({ result, decision: 'approve' }); reviewForm.resetFields(); }}>
              {english ? 'Approve' : '批准'}
            </Button>
            <Button danger icon={<StopOutlined />} size="small"
              onClick={() => { setReview({ result, decision: 'reject' }); reviewForm.resetFields(); }}>
              {english ? 'Reject' : '驳回'}
            </Button>
          </Space> : null}>
          <Typography.Paragraph>
            {result.inspectionMethod} · {result.submittedByName} · {result.reasonCode}
          </Typography.Paragraph>
          {canCorrect && (result.status === 'COMPLETED' || result.status === 'APPROVED')
            ? <Space wrap>{result.lines.flatMap((line) => [
              line.passedBatchId ? <Button key={'pass-' + String(line.id)}
                icon={<AuditOutlined />} size="small" onClick={() => {
                  setReinspection({
                    result, batchId: line.passedBatchId as number,
                    maxQuantity: line.passedQuantity,
                  });
                  correctionForm.setFieldsValue({
                    quantity: line.passedQuantity, confirmReservationReallocation: false,
                  });
                }}>{english ? 'Correct passed stock' : '更正/复检合格库存'}</Button> : null,
              line.quarantinedBatchId ? <Button key={'hold-' + String(line.id)}
                icon={<AuditOutlined />} size="small" onClick={() => {
                  setReinspection({
                    result, batchId: line.quarantinedBatchId as number,
                    maxQuantity: line.quarantinedQuantity,
                  });
                  correctionForm.setFieldsValue({
                    quantity: line.quarantinedQuantity, confirmReservationReallocation: false,
                  });
                }}>{english ? 'Reinspect quarantine' : '复检隔离库存'}</Button> : null,
            ])}</Space> : null}
        </Card>)}
      </> : null}
    </Drawer>

    <Modal open={Boolean(review)} onCancel={() => setReview(undefined)}
      confirmLoading={reviewMutation.isPending}
      title={review?.decision === 'approve' ? (english ? 'Approve release' : '批准放行')
        : (english ? 'Reject release' : '驳回放行')}
      onOk={() => void reviewForm.validateFields().then(async ({ comment }) => {
        await reviewMutation.mutateAsync({ state: review as ReviewState, comment });
        setReview(undefined); setDetail(undefined); await refresh();
      }).catch((error) => {
        if (error instanceof Error) message.error(getErrorMessage(error, t));
      })}>
      <Form form={reviewForm} layout="vertical">
        <Form.Item label={english ? 'Comment' : '复核意见'} name="comment"
          rules={[{ required: true }]}><Input.TextArea maxLength={500} /></Form.Item>
      </Form>
    </Modal>

    <Modal open={Boolean(reinspection)} onCancel={() => setReinspection(undefined)}
      confirmLoading={correctionMutation.isPending}
      title={english ? 'Correction/reinspection' : '更正/复检'}
      onOk={() => void correctionForm.validateFields().then(async (values) => {
        await correctionMutation.mutateAsync(values);
        setReinspection(undefined); setDetail(undefined); await refresh();
      }).catch((error) => {
        if (error instanceof Error) message.error(getErrorMessage(error, t));
      })}>
      <Alert showIcon type="warning" style={{ marginBottom: 12 }}
        message={english ? 'The affected stock is automatically removed from saleability.'
          : '系统会自动停止对应库存的可售和出库。'} />
      <Form form={correctionForm} layout="vertical">
        <Form.Item label={english ? 'Quantity' : '复检数量'} name="quantity"
          rules={[{ required: true }]}>
          <InputNumber min={1} max={reinspection?.maxQuantity} precision={0} />
        </Form.Item>
        <Form.Item label={english ? 'Reason' : '更正原因'} name="reason"
          rules={[{ required: true }]}><Input.TextArea maxLength={500} /></Form.Item>
        <Form.Item name="confirmReservationReallocation" valuePropName="checked"
          label={english ? 'Allow reservation reallocation' : '允许销售预留改派'}>
          <Switch />
        </Form.Item>
      </Form>
    </Modal>

    <Modal open={policyOpen} onCancel={() => setPolicyOpen(false)}
      confirmLoading={policyMutation.isPending}
      title={english ? 'Inspection policy' : '质检策略'}
      onOk={() => void policyForm.validateFields().then(async (values) => {
        await policyMutation.mutateAsync(values);
        setPolicyOpen(false); await refresh();
      }).catch((error) => {
        if (error instanceof Error) message.error(getErrorMessage(error, t));
      })}>
      <Form form={policyForm} layout="vertical">
        <Form.Item name="defaultInspectionRequired" valuePropName="checked"
          label={english ? 'Require inspection by default' : '租户默认要求质检'}><Switch /></Form.Item>
        <Form.Item name="reviewMode" label={english ? 'Passed-stock release' : '合格库存放行'}
          rules={[{ required: true }]}>
          <Radio.Group options={[
            { value: 'DIRECT', label: english ? 'Direct' : '直接放行' },
            { value: 'REVIEW_REQUIRED', label: english ? 'Second-person review' : '另一人复核' },
          ]} />
        </Form.Item>
        <Form.Item name="reason" label={english ? 'Change reason' : '修改原因'}
          rules={[{ required: true }]}><Input.TextArea maxLength={500} /></Form.Item>
      </Form>
    </Modal>
  </section>;
}
