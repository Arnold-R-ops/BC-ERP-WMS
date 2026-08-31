import { ExperimentOutlined, InboxOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Card, Descriptions, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { getErrorMessage } from '../../api/errors';
import { inspectRmaTask, listRmaWarehouseTasks, receiveRmaTask, RMA_TASK_QUERY_KEY, type RmaDisposition, type RmaWarehouseTask } from '../../api/rma';
import { listLocations } from '../../api/warehouseSetup';
import { useAuth } from '../../auth/AuthProvider';

interface ReceiveValues { remark?: string; lines: Array<{ rmaLineId: number; locationId: number; quantity: number }> }
interface InspectValues { reasonDetail?: string; lines: Array<{ receiptLineId: number; disposition: RmaDisposition; quantity: number }> }

export function MobileRmaPage(): JSX.Element {
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const english = i18n.language === 'en-US';
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [detail, setDetail] = useState<RmaWarehouseTask>();
  const [receiveTask, setReceiveTask] = useState<RmaWarehouseTask>();
  const [inspectTask, setInspectTask] = useState<RmaWarehouseTask>();
  const [receiveForm] = Form.useForm<ReceiveValues>();
  const [inspectForm] = Form.useForm<InspectValues>();
  const admin = session?.currentRole === 'TENANT_ADMIN';
  const canReceive = admin || hasPermission(session?.currentRole, session?.permissionCodes, 'rma:receive');
  const canInspect = admin || hasPermission(session?.currentRole, session?.permissionCodes, 'rma:inspect');
  const tasks = useQuery({ queryKey: RMA_TASK_QUERY_KEY, queryFn: () => listRmaWarehouseTasks() });
  const locations = useQuery({
    queryKey: ['locations', 'rma', receiveTask?.warehouseId],
    queryFn: () => listLocations(receiveTask!.warehouseId), enabled: Boolean(receiveTask),
  });
  const receiveMutation = useMutation({ mutationFn: ({ id, values }: { id: number; values: ReceiveValues }) => receiveRmaTask(id, values) });
  const inspectMutation = useMutation({ mutationFn: ({ id, values }: { id: number; values: InspectValues }) => inspectRmaTask(id, values) });
  const refresh = async (): Promise<void> => { await queryClient.invalidateQueries({ queryKey: RMA_TASK_QUERY_KEY }); };

  const openReceive = (task: RmaWarehouseTask): void => {
    setReceiveTask(task);
    receiveForm.setFieldsValue({ lines: task.lines.filter((line) => line.expectedQuantity > line.receivedQuantity).map((line) => ({ rmaLineId: line.rmaLineId, quantity: line.expectedQuantity - line.receivedQuantity, locationId: undefined as unknown as number })) });
  };
  const openInspect = (task: RmaWarehouseTask): void => {
    setInspectTask(task);
    inspectForm.setFieldsValue({ lines: task.receiptLines.filter((line) => line.quantity > line.inspectedQuantity).map((line) => ({ receiptLineId: line.id, disposition: 'RESTOCK_AVAILABLE', quantity: line.quantity - line.inspectedQuantity })) });
  };

  const columns: TableColumnsType<RmaWarehouseTask> = [
    { title: english ? 'Return task' : '退货任务', dataIndex: 'taskNumber', width: 225 },
    { title: 'RMA', dataIndex: 'rmaNumber', width: 230 },
    { title: english ? 'Warehouse' : '仓库', dataIndex: 'warehouseName', width: 150 },
    { title: english ? 'Priority' : '优先级', dataIndex: 'priority', width: 90 },
    { title: english ? 'Progress' : '进度', width: 120, render: (_, task) => `${task.lines.reduce((sum, line) => sum + line.receivedQuantity, 0)} / ${task.lines.reduce((sum, line) => sum + line.expectedQuantity, 0)}` },
    { title: english ? 'Status' : '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={value === 'RECEIVED' ? 'success' : value === 'PARTIAL' ? 'processing' : 'default'}>{value}</Tag> },
    { title: english ? 'Action' : '操作', width: 270, render: (_, task) => <Space wrap>
      <Button size="small" onClick={() => setDetail(task)}>{english ? 'Details' : '详情'}</Button>
      {canReceive && ['EXPECTED', 'PARTIAL'].includes(task.status) ? <Button type="primary" size="small" icon={<InboxOutlined />} onClick={() => openReceive(task)}>{english ? 'Receive' : '收货'}</Button> : null}
      {canInspect && task.receiptLines.some((line) => line.quantity > line.inspectedQuantity) ? <Button size="small" icon={<ExperimentOutlined />} onClick={() => openInspect(task)}>{english ? 'Inspect' : '质检'}</Button> : null}
    </Space> },
  ];

  return <section className="mobile-operation-page">
    <Typography.Title level={3}>{english ? 'Sales-return operations' : '销售退货作业'}</Typography.Title>
    <Typography.Paragraph type="secondary">{english ? 'The warehouse view deliberately hides customer, reason, carrier and tracking data.' : '仓库视图按权限隔离，不展示客户、退货原因、承运商和运单号。'}</Typography.Paragraph>
    <Card style={{ marginBottom: 12 }}><Button icon={<ReloadOutlined />} onClick={() => void refresh()}>{english ? 'Refresh' : '刷新'}</Button></Card>
    <Table columns={columns} dataSource={tasks.data ?? []} loading={tasks.isLoading} rowKey="id" scroll={{ x: 1200 }} pagination={{ pageSize: 12 }} />

    <Drawer open={Boolean(detail)} onClose={() => setDetail(undefined)} width="min(100%, 900px)" title={detail?.taskNumber}>
      {detail ? <>
        <Descriptions bordered size="small" column={2} items={[
          { key: 'rma', label: 'RMA', children: detail.rmaNumber },
          { key: 'warehouse', label: english ? 'Warehouse' : '仓库', children: detail.warehouseName },
          { key: 'team', label: english ? 'Team' : '班组', children: detail.teamName ?? '-' },
          { key: 'area', label: english ? 'Area' : '作业区', children: detail.operationArea ?? '-' },
          { key: 'remark', label: english ? 'Warehouse note' : '仓库备注', children: detail.warehouseRemark ?? '-', span: 2 },
        ]} />
        <Table style={{ marginTop: 12 }} rowKey="rmaLineId" pagination={false} dataSource={detail.lines} columns={[
          { title: 'SKU', dataIndex: 'skuCode' }, { title: english ? 'Product' : '商品', dataIndex: 'productName' },
          { title: english ? 'Expected' : '应收', dataIndex: 'expectedQuantity' }, { title: english ? 'Received' : '已收', dataIndex: 'receivedQuantity' },
        ]} />
        <Typography.Title level={4}>{english ? 'Received return batches' : '退货收货批次'}</Typography.Title>
        <Table rowKey="id" pagination={false} dataSource={detail.receiptLines} columns={[
          { title: 'SKU', dataIndex: 'skuCode' }, { title: english ? 'Return batch' : '退货批次', dataIndex: 'returnBatchCode' },
          { title: english ? 'Location' : '库位', dataIndex: 'locationCode' }, { title: english ? 'Received' : '收到', dataIndex: 'quantity' },
          { title: english ? 'Inspected' : '已检', dataIndex: 'inspectedQuantity' },
        ]} />
      </> : null}
    </Drawer>

    <Modal open={Boolean(receiveTask)} width={820} title={english ? 'Receive returned goods' : '退货收货'} onCancel={() => setReceiveTask(undefined)} confirmLoading={receiveMutation.isPending} onOk={() => void receiveForm.validateFields().then(async (values) => {
      try { await receiveMutation.mutateAsync({ id: receiveTask!.id, values }); setReceiveTask(undefined); message.success(english ? 'Return received.' : '退货已收货'); await refresh(); }
      catch (error) { if (error instanceof Error) message.error(getErrorMessage(error, t)); }
    })}>
      <Alert showIcon type="info" style={{ marginBottom: 12 }} message={english ? 'One submission is atomic. Every line creates a new RETURN_INSPECTION batch; the original dispatch batch is never increased.' : '一次提交原子执行；每行生成新的 RETURN_INSPECTION 批次，绝不增加原发货批次数量。'} />
      <Form form={receiveForm} layout="vertical">
        <Form.Item name="remark" label={english ? 'Warehouse note' : '仓库备注'}><Input.TextArea maxLength={500} /></Form.Item>
        <Form.List name="lines">{(fields) => <>{fields.map((field, index) => <Space key={field.key} align="baseline" wrap>
          <Form.Item hidden name={[field.name, 'rmaLineId']}><InputNumber /></Form.Item>
          <span style={{ width: 230 }}>{receiveTask?.lines[index]?.skuCode} · {receiveTask?.lines[index]?.productName}</span>
          <Form.Item name={[field.name, 'locationId']} label={english ? 'Location' : '库位'} rules={[{ required: true }]}><Select showSearch optionFilterProp="label" style={{ width: 240 }} options={(locations.data ?? []).filter((location) => location.id && location.enabled).map((location) => ({ value: location.id as number, label: `${location.locationCode} · ${location.zone}` }))} /></Form.Item>
          <Form.Item name={[field.name, 'quantity']} label={english ? 'Quantity' : '数量'} rules={[{ required: true }]}><InputNumber min={1} max={(receiveTask?.lines[index]?.expectedQuantity ?? 0) - (receiveTask?.lines[index]?.receivedQuantity ?? 0)} precision={0} /></Form.Item>
        </Space>)}</>}</Form.List>
      </Form>
    </Modal>

    <Modal open={Boolean(inspectTask)} width={900} title={english ? 'Inspect returned goods' : '退货质检'} onCancel={() => setInspectTask(undefined)} confirmLoading={inspectMutation.isPending} onOk={() => void inspectForm.validateFields().then(async (values) => {
      try { await inspectMutation.mutateAsync({ id: inspectTask!.id, values }); setInspectTask(undefined); message.success(english ? 'Inspection saved.' : '质检结果已保存'); await refresh(); }
      catch (error) { if (error instanceof Error) message.error(getErrorMessage(error, t)); }
    })}>
      <Alert showIcon type="info" style={{ marginBottom: 12 }} message={english ? 'Choose inventory quality only. Refund eligibility is decided separately in tenant ERP.' : '这里只判断库存质量；退款资格由租户 ERP 业务人员单独确认。'} />
      <Form form={inspectForm} layout="vertical">
        <Form.Item name="reasonDetail" label={english ? 'Inspection note' : '质检说明'}><Input.TextArea maxLength={500} /></Form.Item>
        <Form.List name="lines">{(fields) => <>{fields.map((field, index) => <Space key={field.key} align="baseline" wrap>
          <Form.Item hidden name={[field.name, 'receiptLineId']}><InputNumber /></Form.Item>
          <span style={{ width: 220 }}>{inspectTask?.receiptLines.filter((line) => line.quantity > line.inspectedQuantity)[index]?.skuCode} · {inspectTask?.receiptLines.filter((line) => line.quantity > line.inspectedQuantity)[index]?.returnBatchCode}</span>
          <Form.Item name={[field.name, 'disposition']} label={english ? 'Disposition' : '库存处理'} rules={[{ required: true }]}><Select style={{ width: 220 }} options={['RESTOCK_AVAILABLE', 'DAMAGED', 'QUARANTINE', 'REJECT_CUSTOMER'].map((value) => ({ value, label: value }))} /></Form.Item>
          <Form.Item name={[field.name, 'quantity']} label={english ? 'Quantity' : '数量'} rules={[{ required: true }]}><InputNumber min={1} max={(inspectTask?.receiptLines.filter((line) => line.quantity > line.inspectedQuantity)[index]?.quantity ?? 0) - (inspectTask?.receiptLines.filter((line) => line.quantity > line.inspectedQuantity)[index]?.inspectedQuantity ?? 0)} precision={0} /></Form.Item>
        </Space>)}</>}</Form.List>
      </Form>
    </Modal>
  </section>;
}
