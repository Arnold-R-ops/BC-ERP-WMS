import { refundProgress } from './refundProgress';
import { CheckOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Card, Descriptions, Drawer, Form, Input, InputNumber, Modal, Radio, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { getErrorMessage } from '../../api/errors';
import { listOutboundTasks } from '../../api/outbound';
import {
  confirmRmaRefund, createRma, decideRma, getRmaPolicy, listRmaChannelCases, listRmas, requestRmaResolution,
  RMA_QUERY_KEY, setRefundEligibility, terminateRma, updateRmaPolicy,
  type CreateRmaPayload, type Rma, type RmaApprovalMode, type RmaChannelCase, type RmaPolicy,
  type RmaStatutoryBasis, type RmaType,
} from '../../api/rma';
import { listSalesOrders } from '../../api/sales';
import { listWarehouses } from '../../api/warehouseSetup';
import { useAuth } from '../../auth/AuthProvider';
import { formatDateTime, formatMoney } from '../workflowUtils';

interface CreateValues {
  salesOrderId: number; warehouseId: number; rmaType: RmaType;
  channelCaseId?: number;
  statutoryBasis: RmaStatutoryBasis; reasonCode: string; reasonDetail?: string;
  returnCarrier?: string; returnTrackingNo?: string;
  lines: Array<{ outboundTaskId: number; quantity: number }>;
}

interface PolicyValues extends RmaPolicy { reason: string }
interface RefundConfirmationValues { reference: string; amount: number; currency: string; succeeded: boolean; failureReason?: string }

function statusColor(value: string): string {
  if (value.includes('CONFIRMED') || value === 'AUTHORIZED' || value === 'COMPLETED' || value === 'RECEIVED') return 'success';
  if (value.includes('PENDING') || value === 'EXPECTED' || value === 'PARTIAL') return 'processing';
  if (value === 'EXCEPTION' || value === 'REJECTED') return 'error';
  return 'default';
}

export function RmaPage(): JSX.Element {
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const english = i18n.language === 'en-US';
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<Rma>();
  const [createOpen, setCreateOpen] = useState(false);
  const [policyOpen, setPolicyOpen] = useState(false);
  const [eligibilityOpen, setEligibilityOpen] = useState(false);
  const [resolutionOpen, setResolutionOpen] = useState(false);
  const [refundConfirmationOpen, setRefundConfirmationOpen] = useState(false);
  const [createForm] = Form.useForm<CreateValues>();
  const [policyForm] = Form.useForm<PolicyValues>();
  const [eligibilityForm] = Form.useForm<{ reason: string; lines: Array<{ rmaLineId: number; eligibleQuantity: number }> }>();
  const [resolutionForm] = Form.useForm<{ resolutionType: 'REFUND' | 'EXCHANGE'; refundAmount?: number; currency: string; advance: boolean; reason?: string }>();
  const [refundConfirmationForm] = Form.useForm<RefundConfirmationValues>();
  const watchedOrderId = Form.useWatch('salesOrderId', createForm);
  const admin = session?.currentRole === 'TENANT_ADMIN';
  const permitted = (code: string): boolean => admin || hasPermission(session?.currentRole, session?.permissionCodes, code);

  const rmas = useQuery({ queryKey: RMA_QUERY_KEY, queryFn: () => listRmas() });
  const channelCases = useQuery({ queryKey: [...RMA_QUERY_KEY, 'channel-cases'], queryFn: listRmaChannelCases });
  const sales = useQuery({ queryKey: ['sales-orders', 'rma-options'], queryFn: () => listSalesOrders() });
  const warehouses = useQuery({ queryKey: ['warehouse-setup', 'warehouses'], queryFn: listWarehouses });
  const outbound = useQuery({
    queryKey: ['outbound-tasks', 'rma-options', watchedOrderId],
    queryFn: () => listOutboundTasks({ salesOrderId: watchedOrderId, status: 'COMPLETED' }),
    enabled: Boolean(watchedOrderId),
  });
  const policy = useQuery({ queryKey: [...RMA_QUERY_KEY, 'policy'], queryFn: getRmaPolicy });
  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: RMA_QUERY_KEY });
  };

  const createMutation = useMutation({ mutationFn: (values: CreateValues) => createRma(values as CreateRmaPayload) });
  const policyMutation = useMutation({ mutationFn: updateRmaPolicy });

  const run = async (action: () => Promise<Rma>, success: string): Promise<void> => {
    try {
      const result = await action();
      setSelected(result); message.success(success); await refresh();
    } catch (error) { if (error instanceof Error) message.error(getErrorMessage(error, t)); }
  };

  const columns = useMemo<TableColumnsType<Rma>>(() => [
    { title: 'RMA', dataIndex: 'rmaNumber', width: 235 },
    { title: english ? 'Sales order' : '销售单', dataIndex: 'salesOrderNo', width: 165 },
    { title: english ? 'Customer' : '客户', dataIndex: 'customerName', width: 180 },
    { title: english ? 'Type' : '类型', dataIndex: 'rmaType', width: 145, render: (value: string) => <Tag>{value}</Tag> },
    { title: english ? 'Business' : '业务授权', dataIndex: 'businessStatus', width: 150, render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag> },
    { title: english ? 'Receipt' : '收货', dataIndex: 'receiptStatus', width: 125, render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag> },
    { title: english ? 'Inspection' : '质检', dataIndex: 'inspectionStatus', width: 125, render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag> },
    { title: english ? 'Resolution' : '结果处理', dataIndex: 'resolutionStatus', width: 170, render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag> },
    { title: english ? 'Requested' : '申请时间', dataIndex: 'requestedAt', width: 170, render: (value: string) => formatDateTime(value, i18n.language) },
    { title: english ? 'Action' : '操作', fixed: 'right', width: 90, render: (_, row) => <Button icon={<EyeOutlined />} size="small" onClick={() => setSelected(row)}>{english ? 'View' : '查看'}</Button> },
  ], [english]);

  const submitCreate = async (): Promise<void> => {
    try {
      const values = await createForm.validateFields();
      if (!values.lines?.length) { message.warning(english ? 'Add at least one dispatched line.' : '至少选择一条已发货明细'); return; }
      await createMutation.mutateAsync(values);
      setCreateOpen(false); createForm.resetFields(); message.success(english ? 'RMA created.' : '退货/换货单已创建'); await refresh();
    } catch (error) { if (error instanceof Error) message.error(getErrorMessage(error, t)); }
  };

  const openEligibility = (): void => {
    if (!selected) return;
    eligibilityForm.setFieldsValue({ reason: '', lines: selected.lines.map((line) => ({ rmaLineId: line.id, eligibleQuantity: line.refundEligibleQuantity })) });
    setEligibilityOpen(true);
  };

  const openResolution = (): void => {
    resolutionForm.setFieldsValue({ resolutionType: selected?.rmaType === 'EXCHANGE' ? 'EXCHANGE' : 'REFUND', currency: 'GBP', advance: false });
    setResolutionOpen(true);
  };

  return <section>
    <Typography.Title level={2}>{english ? 'Sales returns (RMA)' : '销售退货/换货'}</Typography.Title>
    <Typography.Paragraph type="secondary">
      {english ? 'Authorisation, physical receipt, inspection and refund/exchange are independent, auditable stages.' : '业务授权、实物收货、质检与退款/换货相互独立并全程留痕。'}
    </Typography.Paragraph>
    <Card style={{ marginBottom: 12 }}>
      <Space wrap>
        <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>{english ? 'Refresh' : '刷新'}</Button>
        {permitted('rma:create') ? <Button type="primary" icon={<PlusOutlined />} onClick={() => { createForm.resetFields(); createForm.setFieldsValue({ rmaType: 'RETURN_REFUND', statutoryBasis: 'VOLUNTARY_POLICY', lines: [] }); setCreateOpen(true); }}>{english ? 'Create RMA' : '新建退货/换货'}</Button> : null}
        {permitted('rma:policy') ? <Button icon={<SettingOutlined />} onClick={() => { if (policy.data) policyForm.setFieldsValue({ ...policy.data, reason: '' }); setPolicyOpen(true); }}>{english ? 'Policy' : '退货策略'}</Button> : null}
      </Space>
    </Card>
    {(channelCases.data ?? []).some((item) => item.status === 'MATCHED_PENDING' || item.status === 'UNMATCHED_PENDING') ? <Card title={english ? 'Channel refund review queue' : '渠道退款待处理'} style={{ marginBottom: 12 }}>
      <Alert showIcon type="info" style={{ marginBottom: 12 }} message={english ? 'A matched channel refund is only a review case. It never creates stock or a refund automatically.' : '渠道退款匹配成功后也只形成待处理案例，绝不会自动创建库存或执行退款。'} />
      <Table<RmaChannelCase> size="small" rowKey="id" pagination={false} dataSource={(channelCases.data ?? []).filter((item) => item.status === 'MATCHED_PENDING' || item.status === 'UNMATCHED_PENDING')} columns={[
        { title: english ? 'Channel' : '渠道', dataIndex: 'channel' },
        { title: english ? 'External case' : '渠道案例', dataIndex: 'externalCaseId' },
        { title: english ? 'Sales order' : '已匹配销售单', dataIndex: 'salesOrderNumber', render: (value?: string) => value ?? '-' },
        { title: english ? 'Status' : '状态', dataIndex: 'status', render: (value: string) => <Tag color={value === 'MATCHED_PENDING' ? 'processing' : 'warning'}>{value}</Tag> },
        { title: english ? 'Action' : '操作', render: (_, item) => item.status === 'MATCHED_PENDING' && permitted('rma:create') ? <Button size="small" onClick={() => { createForm.resetFields(); createForm.setFieldsValue({ channelCaseId: item.id, salesOrderId: item.salesOrderId, rmaType: 'RETURN_REFUND', statutoryBasis: 'VOLUNTARY_POLICY', reasonCode: 'CHANNEL_REFUND', lines: [] }); setCreateOpen(true); }}>{english ? 'Create linked RMA' : '创建关联退货'}</Button> : null },
      ]} />
    </Card> : null}
    <Table columns={columns} dataSource={rmas.data ?? []} loading={rmas.isLoading} rowKey="id" scroll={{ x: 1510 }} pagination={{ pageSize: 15 }} />

    <Modal open={createOpen} width={900} title={english ? 'Create linked RMA' : '新建退货/换货'} okText={english ? 'OK' : '确定'} cancelText={english ? 'Cancel' : '取消'} onCancel={() => { setCreateOpen(false); createForm.resetFields(); }} onOk={() => void submitCreate()} confirmLoading={createMutation.isPending}>
      <Alert showIcon type="info" style={{ marginBottom: 12 }} message={english ? 'Only completed dispatch tasks are returnable. This page does not support unlinked returns.' : '只能退回已有完成发货证据的数量；本页面不支持无原销售单退货。'} />
      <Form form={createForm} layout="vertical">
        <Form.Item hidden name="channelCaseId"><InputNumber /></Form.Item>
        <Space wrap align="start">
          <Form.Item name="salesOrderId" label={english ? 'Sales order' : '原销售单'} rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" style={{ width: 260 }} options={(sales.data ?? []).filter((order) => Number(order.id) > 0).map((order) => ({ value: order.id as number, label: `${order.orderNo} · ${order.customerName ?? ''}` }))} />
          </Form.Item>
          <Form.Item name="warehouseId" label={english ? 'Return warehouse' : '退货目标仓库'} rules={[{ required: true }]}>
            <Select style={{ width: 210 }} options={(warehouses.data ?? []).filter((warehouse) => warehouse.id && warehouse.isActive).map((warehouse) => ({ value: warehouse.id as number, label: `${warehouse.code} · ${warehouse.name}` }))} />
          </Form.Item>
          <Form.Item name="rmaType" label={english ? 'Type' : '类型'} rules={[{ required: true }]}>
            <Select style={{ width: 190 }} options={['RETURN_REFUND', 'EXCHANGE', ...(policy.data?.refundOnlyEnabled ? ['REFUND_ONLY'] : [])].map((value) => ({ value, label: english ? value.replaceAll('_', ' ') : ({ RETURN_REFUND: '退货退款', EXCHANGE: '换货', REFUND_ONLY: '仅退款', VOLUNTARY_POLICY: '普通退货', DISTANCE_CANCELLATION: '远程销售撤销', FAULTY_NOT_AS_DESCRIBED: '商品质量/描述不符', OTHER_STATUTORY: '其他法定原因' } as Record<string, string>)[value] ?? value }))} />
          </Form.Item>
          <Form.Item name="statutoryBasis" label={english ? 'Basis' : '退货依据'} rules={[{ required: true }]}>
            <Select style={{ width: 230 }} options={['VOLUNTARY_POLICY', 'DISTANCE_CANCELLATION', 'FAULTY_NOT_AS_DESCRIBED', 'OTHER_STATUTORY'].map((value) => ({ value, label: english ? value.replaceAll('_', ' ') : ({ RETURN_REFUND: '退货退款', EXCHANGE: '换货', REFUND_ONLY: '仅退款', VOLUNTARY_POLICY: '普通退货', DISTANCE_CANCELLATION: '远程销售撤销', FAULTY_NOT_AS_DESCRIBED: '商品质量/描述不符', OTHER_STATUTORY: '其他法定原因' } as Record<string, string>)[value] ?? value }))} />
          </Form.Item>
        </Space>
        <Form.Item name="reasonCode" label={english ? 'Reason' : '退货原因'} rules={[{ required: true }]}><Select options={[
          { value: 'DAMAGED', label: english ? 'Damaged goods' : '商品破损' },
          { value: 'WRONG_ITEM', label: english ? 'Wrong item' : '商品发错' },
          { value: 'QUALITY', label: english ? 'Quality issue' : '质量问题' },
          { value: 'CUSTOMER_REQUEST', label: english ? 'Customer request' : '客户申请' },
          { value: 'CHANNEL_REFUND', label: english ? 'Channel refund' : '渠道退款' },
          { value: 'OTHER', label: english ? 'Other (describe below)' : '其他（填写说明）' },
        ]} /></Form.Item>
        <Form.Item name="reasonDetail" label={english ? 'Details' : '原因说明'}><Input.TextArea maxLength={1000} rows={2} /></Form.Item>
        <Space wrap align="start">
          <Form.Item name="returnCarrier" label={english ? 'Customer carrier (sales only)' : '客户退回承运商（仅业务端）'}><Input maxLength={80} /></Form.Item>
          <Form.Item name="returnTrackingNo" label={english ? 'Tracking (sales only)' : '客户退回运单（仅业务端）'}><Input maxLength={120} /></Form.Item>
        </Space>
        <Form.List name="lines">
          {(fields, { add, remove }) => <>
            {fields.map((field) => <Space key={field.key} align="baseline" wrap>
              <Form.Item {...field} name={[field.name, 'outboundTaskId']} label={english ? 'Completed dispatch' : '已完成发货明细'} rules={[{ required: true }]}>
                <Select style={{ width: 470 }} options={(outbound.data ?? []).filter((task) => task.id && task.status === 'COMPLETED').map((task) => ({ value: task.id as number, label: `${task.productSkuCode} · ${task.productName} · ${task.batchCode} · ${task.actualQty ?? 0}` }))} />
              </Form.Item>
              <Form.Item {...field} name={[field.name, 'quantity']} label={english ? 'Quantity' : '数量'} rules={[{ required: true }]}><InputNumber min={1} precision={0} /></Form.Item>
              <Button danger onClick={() => remove(field.name)}>{english ? 'Remove' : '删除'}</Button>
            </Space>)}
            <Button onClick={() => add()} disabled={!watchedOrderId}>{english ? 'Add dispatched line' : '添加已发货明细'}</Button>
          </>}
        </Form.List>
      </Form>
    </Modal>

    <Drawer open={Boolean(selected)} width={980} title={selected?.rmaNumber} onClose={() => setSelected(undefined)}>
      {selected ? <>
        <Alert showIcon style={{marginBottom:12}} type={selected.resolutionStatus==='EXCEPTION'?'warning':'info'} message={
          selected.businessStatus==='PENDING_APPROVAL' ? (english?'Next: review return request':'下一步：审批退货申请') :
          ['REFUND_REQUESTED','EXCEPTION'].includes(selected.resolutionStatus) ? (english?'Next: confirm the external refund result':'下一步：核对实际退款回执并确认结果') :
          selected.resolutionStatus==='REFUND_CONFIRMED' ? (english?'Refund confirmed':'退款已确认') :
          selected.resolutionStatus==='EXCHANGE_CREATED' ? (english?'Next: fulfil the linked replacement order':'下一步：处理关联换货销售单') :
          ['CANCELLED','REJECTED','CLOSED'].includes(selected.businessStatus) ? (english?'No further business action':'当前业务已结束') :
          ['PARTIAL','COMPLETED'].includes(selected.inspectionStatus) ? (english?'Next: confirm eligibility and refund/exchange':'下一步：确认退款资格并处理退款或换货') :
          ['PARTIAL','RECEIVED'].includes(selected.receiptStatus) ? (english?'Next: inspect the received goods':'下一步：质检已收到的退货') :
          selected.rmaType==='REFUND_ONLY' ? (english?'Next: confirm refund amount':'下一步：确认退款金额') : (english?'Next: receive returned goods':'下一步：等待退货并收货')
        } />
        {selected.rmaType!=='EXCHANGE' && <Descriptions size="small" column={3} items={[
          {key:'expected',label:english?'Refund due':'应退',children:selected.refundAmount==null?(english?'To be confirmed':'待确认'):selected.refundCurrency+' '+formatMoney(refundProgress(selected).expected,i18n.language)},
          {key:'confirmed',label:english?'Refund confirmed':'已退',children:(selected.refundCurrency??'GBP')+' '+formatMoney(refundProgress(selected).confirmed,i18n.language)},
          {key:'remaining',label:english?'Refund pending':'待退',children:selected.refundAmount==null?(english?'To be confirmed':'待确认'):selected.refundCurrency+' '+formatMoney(refundProgress(selected).pending,i18n.language)},
        ]} />}
        {selected.outsideStandardWindow ? <Alert showIcon type="warning" style={{ marginBottom: 12 }} message={english ? `Outside the ${selected.standardReturnDaysSnapshot}-day commercial window; statutory rights remain separate.` : `已超过 ${selected.standardReturnDaysSnapshot} 天商业默认期限；法定权利不受该期限截断。`} /> : null}
        <Descriptions bordered size="small" column={2} items={[
          { key: 'order', label: english ? 'Sales order' : '销售单', children: selected.salesOrderNo },
          { key: 'customer', label: english ? 'Customer' : '客户', children: selected.customerName },
          { key: 'warehouse', label: english ? 'Warehouse' : '目标仓库', children: selected.warehouseName },
          { key: 'basis', label: english ? 'Basis' : '依据', children: selected.statutoryBasis },
          { key: 'reason', label: english ? 'Reason' : '原因', children: `${selected.reasonCode}${selected.reasonDetail ? ` · ${selected.reasonDetail}` : ''}` },
          { key: 'refund', label: english ? 'Refund' : '退款', children: selected.refundAmount === undefined ? '-' : `${selected.refundCurrency} ${formatMoney(selected.refundAmount, i18n.language)}` },
        ]} />
        <Space wrap style={{ margin: '16px 0' }}>
          {permitted('rma:approve') && selected.businessStatus === 'PENDING_APPROVAL' ? <>
            <Button type="primary" icon={<CheckOutlined />} onClick={() => void run(() => decideRma(selected.id, 'approve', english ? 'Approved' : '审批通过'), english ? 'Approved.' : '已批准')}>{english ? 'Approve' : '批准'}</Button>
            <Button danger onClick={() => void run(() => decideRma(selected.id, 'reject', english ? 'Rejected' : '审批驳回'), english ? 'Rejected.' : '已驳回')}>{english ? 'Reject' : '驳回'}</Button>
          </> : null}
          {permitted('rma:create') && ['PENDING_APPROVAL', 'AUTHORIZED'].includes(selected.businessStatus) && selected.lines.every((line) => line.receivedQuantity === 0) ? <Button danger onClick={() => void run(() => terminateRma(selected.id, 'cancel', english ? 'Cancelled by user' : '用户取消'), english ? 'Cancelled.' : '已取消')}>{english ? 'Cancel' : '取消'}</Button> : null}
          {permitted('rma:create') && selected.receiptStatus === 'PARTIAL' ? <Button onClick={() => void run(() => terminateRma(selected.id, 'close-remainder', english ? 'Close unreceived remainder' : '关闭未收到余量'), english ? 'Remainder closed.' : '余量已关闭')}>{english ? 'Close remainder' : '关闭未收余量'}</Button> : null}
          {permitted('rma:resolve') && selected.rmaType !== 'REFUND_ONLY' && ['PARTIAL', 'COMPLETED'].includes(selected.inspectionStatus) && ['PENDING', 'REJECTED_GOODS_PENDING'].includes(selected.resolutionStatus) ? <Button onClick={openEligibility}>{english ? 'Refund eligibility' : '确认退款资格'}</Button> : null}
          {permitted('rma:resolve') && ['PENDING', 'REJECTED_GOODS_PENDING'].includes(selected.resolutionStatus) ? <Button type="primary" onClick={openResolution}>{english ? 'Resolve' : '退款/换货处理'}</Button> : null}
          {permitted('rma:resolve') && ['REFUND_REQUESTED', 'EXCEPTION'].includes(selected.resolutionStatus) ? <Button type="primary" onClick={() => { refundConfirmationForm.setFieldsValue({ reference: selected.refundReference ?? '', amount: selected.refundAmount ?? 0, currency: selected.refundCurrency ?? 'GBP', succeeded: true, failureReason: '' }); setRefundConfirmationOpen(true); }}>{english ? 'Confirm refund result' : '确认退款结果'}</Button> : null}
        </Space>
        <Typography.Title level={4}>{english ? 'Quantities and trace' : '数量与来源追溯'}</Typography.Title>
        <Table rowKey="id" pagination={false} scroll={{ x: 1250 }} dataSource={selected.lines} columns={[
          { title: 'SKU', dataIndex: 'skuCode' }, { title: english ? 'Original batch' : '原发货批次', dataIndex: 'originalBatchCode' },
          { title: english ? 'Shipped' : '已发', dataIndex: 'shippedQuantity' }, { title: english ? 'Authorised' : '授权', dataIndex: 'authorizedQuantity' },
          { title: english ? 'Received' : '收到', dataIndex: 'receivedQuantity' }, { title: english ? 'Inspected' : '已检', dataIndex: 'inspectedQuantity' },
          { title: english ? 'Available' : '恢复可售', dataIndex: 'restockQuantity' }, { title: english ? 'Damaged' : '破损', dataIndex: 'damagedQuantity' },
          { title: english ? 'Quarantine' : '隔离', dataIndex: 'quarantineQuantity' }, { title: english ? 'Reject' : '退回客户', dataIndex: 'rejectedQuantity' },
          { title: english ? 'Refund eligible' : '可退款', dataIndex: 'refundEligibleQuantity' },
        ]} />
        <Typography.Title level={4}>{english ? 'Audit timeline' : '审计时间线'}</Typography.Title>
        {selected.events.map((event) => <Card key={event.id} size="small" style={{ marginBottom: 8 }} title={<Space><Tag>{event.eventType}</Tag><span>{event.actorName}</span></Space>} extra={formatDateTime(event.occurredAt, i18n.language)}><Typography.Text code>{event.payloadJson}</Typography.Text></Card>)}
      </> : null}
    </Drawer>

    <Modal open={eligibilityOpen} title={english ? 'Set refund eligibility' : '确认退款资格'} onCancel={() => setEligibilityOpen(false)} onOk={() => void eligibilityForm.validateFields().then((values) => run(() => setRefundEligibility(selected!.id, values.reason, values.lines), english ? 'Eligibility saved.' : '退款资格已保存').then(() => setEligibilityOpen(false)))}>
      <Alert showIcon type="info" style={{ marginBottom: 12 }} message={english ? 'This commercial decision does not change inventory quality.' : '此业务判断不会改变库存质量状态。'} />
      <Form form={eligibilityForm} layout="vertical">
        <Form.Item name="reason" label={english ? 'Reason' : '判断依据'} rules={[{ required: true }]}><Input.TextArea maxLength={500} /></Form.Item>
        <Form.List name="lines">{(fields) => <>{fields.map((field, index) => <Space key={field.key}>
          <Form.Item hidden name={[field.name, 'rmaLineId']}><InputNumber /></Form.Item>
          <span style={{ width: 240 }}>{selected?.lines[index]?.skuCode} · {english ? 'inspected' : '已检'} {selected?.lines[index]?.inspectedQuantity}</span>
          <Form.Item name={[field.name, 'eligibleQuantity']} rules={[{ required: true }]}><InputNumber min={0} max={selected?.lines[index]?.inspectedQuantity} precision={0} /></Form.Item>
        </Space>)}</>}</Form.List>
      </Form>
    </Modal>

    <Modal open={resolutionOpen} title={english ? 'Refund / exchange' : '退款/换货处理'} onCancel={() => setResolutionOpen(false)} onOk={() => void resolutionForm.validateFields().then((values) => run(() => requestRmaResolution(selected!.id, { ...values, exchangeLines: values.resolutionType === 'EXCHANGE' ? selected!.lines.map((line) => ({ rmaLineId: line.id, quantity: values.advance ? line.authorizedQuantity : line.refundEligibleQuantity })).filter((line) => line.quantity > 0) : undefined }), english ? 'Resolution requested.' : '处理请求已创建').then(() => setResolutionOpen(false)))}>
      <Form form={resolutionForm} layout="vertical">
        <Form.Item name="resolutionType" label={english ? 'Resolution' : '处理方式'} rules={[{ required: true }]}><Radio.Group options={[{ value: 'REFUND', label: english ? 'Refund' : '退款' }, { value: 'EXCHANGE', label: english ? 'Exchange' : '换货', disabled: selected?.rmaType !== 'EXCHANGE' }]} /></Form.Item>
        <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => getFieldValue('resolutionType') === 'REFUND' ? <Space><Form.Item name="refundAmount" label={english ? 'Amount (blank = maximum)' : '金额（留空按上限）'}><InputNumber min={0} precision={2} /></Form.Item><Form.Item name="currency" label={english ? 'Currency' : '币种'} rules={[{ required: true }]}><Input maxLength={3} /></Form.Item></Space> : null}</Form.Item>
        <Form.Item name="advance" label={english ? 'Advance resolution' : '提前处理'} valuePropName="checked"><Switch disabled={!policy.data?.advanceResolutionEnabled || !permitted('rma:advance-resolution')} /></Form.Item>
        <Form.Item name="reason" label={english ? 'Notes' : '处理说明'}><Input.TextArea maxLength={500} /></Form.Item>
      </Form>
    </Modal>

    <Modal open={refundConfirmationOpen} title={english ? 'Confirm external refund result' : '确认外部退款结果'} onCancel={() => setRefundConfirmationOpen(false)} onOk={() => void refundConfirmationForm.validateFields().then((values) => run(() => confirmRmaRefund(selected!.id, values), english ? 'Refund result recorded.' : '退款结果已留痕').then(() => setRefundConfirmationOpen(false)))}>
      <Alert showIcon type="warning" style={{ marginBottom: 12 }} message={english ? 'This records evidence returned by the payment channel or a manual refund. It does not call a payment API.' : '这里只记录支付渠道或人工退款的回执证据，不会调用支付接口。'} />
      <Form form={refundConfirmationForm} layout="vertical">
        <Form.Item name="reference" label={english ? 'Confirmation reference' : '退款回执号'} rules={[{ required: true }]}><Input maxLength={120} /></Form.Item>
        <Space align="start">
          <Form.Item name="amount" label={english ? 'Confirmed amount' : '确认金额'} rules={[{ required: true }]}><InputNumber min={0} precision={2} /></Form.Item>
          <Form.Item name="currency" label={english ? 'Currency' : '币种'} rules={[{ required: true, pattern: /^[A-Z]{3}$/ }]}><Input maxLength={3} /></Form.Item>
        </Space>
        <Form.Item name="succeeded" label={english ? 'Refund succeeded' : '退款成功'} valuePropName="checked"><Switch /></Form.Item>
        <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => getFieldValue('succeeded') ? null : <Form.Item name="failureReason" label={english ? 'Failure reason' : '失败原因'} rules={[{ required: true }]}><Input.TextArea maxLength={500} /></Form.Item>}</Form.Item>
      </Form>
    </Modal>

    <Modal open={policyOpen} title={english ? 'RMA policy' : '退货策略'} onCancel={() => setPolicyOpen(false)} onOk={() => void policyForm.validateFields().then(async (values) => { await policyMutation.mutateAsync(values); setPolicyOpen(false); await refresh(); message.success(english ? 'Policy updated.' : '策略已更新'); })} confirmLoading={policyMutation.isPending}>
      <Form form={policyForm} layout="vertical">
        <Form.Item name="approvalMode" label={english ? 'Approval mode' : '审批模式'} rules={[{ required: true }]}><Select options={(['DIRECT', 'RISK_BASED', 'ALWAYS'] as RmaApprovalMode[]).map((value) => ({ value, label: english ? value.replaceAll('_', ' ') : ({ RETURN_REFUND: '退货退款', EXCHANGE: '换货', REFUND_ONLY: '仅退款', VOLUNTARY_POLICY: '普通退货', DISTANCE_CANCELLATION: '远程销售撤销', FAULTY_NOT_AS_DESCRIBED: '商品质量/描述不符', OTHER_STATUTORY: '其他法定原因' } as Record<string, string>)[value] ?? value }))} /></Form.Item>
        <Form.Item name="standardReturnDays" label={english ? 'Commercial default days' : '商业默认退货天数'} rules={[{ required: true }]}><InputNumber min={1} max={365} precision={0} /></Form.Item>
        <Form.Item name="refundOnlyEnabled" label={english ? 'Linked refund-only' : '允许关联原单仅退款'} valuePropName="checked"><Switch /></Form.Item>
        <Form.Item name="advanceResolutionEnabled" label={english ? 'Advance resolution' : '允许提前处理'} valuePropName="checked"><Switch /></Form.Item>
        <Form.Item name="quarantineStagingEnabled" label={english ? 'Q-zone staging' : '启用 Q 区中转'} valuePropName="checked"><Switch /></Form.Item>
        <Form.Item name="reason" label={english ? 'Change reason' : '修改原因'} rules={[{ required: true }]}><Input.TextArea maxLength={500} /></Form.Item>
      </Form>
    </Modal>
  </section>;
}
