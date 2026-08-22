import { Alert, App, Button, Card, Descriptions, Empty, Modal, Pagination, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  createPlatformExport, downloadPlatformExport, getPlatformExport, readPlatformTenantData,
  type PlatformDataPage, type PlatformEffectiveAccess, type PlatformExportJob, type PlatformTenant,
} from './api';

const DATA_PAGE_SIZE = 20;

export interface DatasetDefinition { code: string; zh: string; en: string; fields: string[]; }

const DATASETS: DatasetDefinition[] = [
  { code: 'users', zh: '用户', en: 'Users', fields: ['id', 'username', 'display_name', 'enabled', 'created_at', 'updated_at'] },
  { code: 'roles', zh: '角色', en: 'Roles', fields: ['id', 'role_code', 'role_name', 'role_type', 'status', 'created_at', 'updated_at'] },
  { code: 'warehouses', zh: '仓库', en: 'Warehouses', fields: ['id', 'code', 'name', 'address', 'contact', 'phone', 'active', 'created_at', 'updated_at'] },
  { code: 'products', zh: '产品', en: 'Products', fields: ['id', 'product_code', 'product_name', 'category_id', 'brand', 'enabled', 'created_at', 'updated_at'] },
  { code: 'inventory', zh: '库存', en: 'Inventory', fields: ['id', 'product_sku_id', 'location_id', 'quantity', 'version', 'created_at', 'updated_at'] },
  { code: 'sales_orders', zh: '销售订单', en: 'Sales orders', fields: ['id', 'order_no', 'customer_id', 'total_amount', 'status', 'channel', 'external_order_no', 'created_at', 'updated_at'] },
];

const FIELD_LABELS: Record<string, [string, string]> = {
  id: ['ID', 'ID'], username: ['用户名', 'Username'], display_name: ['显示名称', 'Display name'], enabled: ['启用', 'Enabled'],
  created_at: ['创建时间', 'Created at'], updated_at: ['更新时间', 'Updated at'], role_code: ['角色编码', 'Role code'],
  role_name: ['角色名称', 'Role name'], role_type: ['角色类型', 'Role type'], status: ['状态', 'Status'], code: ['编码', 'Code'],
  name: ['名称', 'Name'], address: ['地址', 'Address'], contact: ['联系人', 'Contact'], phone: ['电话', 'Phone'], active: ['启用', 'Active'],
  product_code: ['产品编码', 'Product code'], product_name: ['产品名称', 'Product name'], category_id: ['类别 ID', 'Category ID'],
  brand: ['品牌', 'Brand'], product_sku_id: ['SKU ID', 'SKU ID'], location_id: ['库位 ID', 'Location ID'], quantity: ['数量', 'Quantity'],
  version: ['版本', 'Version'], order_no: ['订单号', 'Order number'], customer_id: ['客户 ID', 'Customer ID'], total_amount: ['总金额', 'Total amount'],
  channel: ['渠道', 'Channel'], external_order_no: ['外部订单号', 'External order number'],
};

function displayValue(value: unknown, english: boolean): React.ReactNode {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'boolean') return <Tag color={value ? 'green' : 'default'}>{value ? (english ? 'Yes' : '是') : (english ? 'No' : '否')}</Tag>;
  return String(value);
}

export function effectiveDatasetAccess(access: PlatformEffectiveAccess, tenantId: number | undefined, datasetCode: string | undefined): { read: boolean; export: boolean } {
  if (access.superAdmin) return { read: true, export: true };
  if (tenantId === undefined || !datasetCode) return { read: false, export: false };
  const scope = access.scopes.find((item) => item.tenantId === tenantId && item.datasetCode === datasetCode);
  return { read: Boolean(scope?.read), export: Boolean(scope?.export) };
}

export function effectiveDatasets(access: PlatformEffectiveAccess, tenantId: number | undefined): DatasetDefinition[] {
  if (access.superAdmin) return DATASETS;
  if (tenantId === undefined) return [];
  const codes = new Set(access.scopes.filter((item) => item.tenantId === tenantId).map((item) => item.datasetCode));
  return DATASETS.filter((item) => codes.has(item.code));
}

export function TenantDatasetBrowser({ tenant, access }: { tenant: PlatformTenant; access: PlatformEffectiveAccess }): JSX.Element {
  const { message } = App.useApp();
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const mounted = useRef(true);
  const [datasetCode, setDatasetCode] = useState<string>();
  const [dataPage, setDataPage] = useState(0);
  const [data, setData] = useState<PlatformDataPage | null>(null);
  const [dataLoading, setDataLoading] = useState(false);
  const [dataError, setDataError] = useState(false);
  const [exportConfirmOpen, setExportConfirmOpen] = useState(false);
  const [exportSubmitting, setExportSubmitting] = useState(false);
  const [exportJob, setExportJob] = useState<PlatformExportJob | null>(null);
  const [exportError, setExportError] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const selectedAccess = effectiveDatasetAccess(access, tenant.id, datasetCode);
  const availableDatasets = effectiveDatasets(access, tenant.id);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!datasetCode || !selectedAccess.read) {
      setData(null);
      setDataLoading(false);
      setDataError(false);
      return undefined;
    }
    let active = true;
    setDataLoading(true);
    setDataError(false);
    void readPlatformTenantData(tenant.id, datasetCode, dataPage, DATA_PAGE_SIZE)
      .then((result) => { if (active) setData(result); })
      .catch(() => { if (active) setDataError(true); })
      .finally(() => { if (active) setDataLoading(false); });
    return () => { active = false; };
  }, [dataPage, datasetCode, selectedAccess.read, tenant.id]);

  useEffect(() => {
    if (!exportJob || !['PENDING', 'RUNNING'].includes(exportJob.status)) return undefined;
    let active = true;
    const timer = window.setTimeout(() => {
      void getPlatformExport(tenant.id, exportJob.id)
        .then((job) => { if (active) setExportJob(job); })
        .catch(() => { if (active) setExportError(true); });
    }, 1_500);
    return () => { active = false; window.clearTimeout(timer); };
  }, [exportJob, tenant.id]);

  const dataset = DATASETS.find((item) => item.code === datasetCode) ?? DATASETS[0];
  const columns = useMemo<ColumnsType<Record<string, unknown>>>(() => dataset.fields.map((field) => ({
    title: FIELD_LABELS[field]?.[english ? 1 : 0] ?? field, dataIndex: field, key: field,
    width: field.endsWith('_at') ? 190 : 140, render: (value: unknown) => displayValue(value, english),
  })), [dataset, english]);

  const selectDataset = (value: string | undefined) => {
    setDatasetCode(value);
    setDataPage(0);
    setData(null);
    setDataError(false);
    setExportConfirmOpen(false);
    setExportJob(null);
    setExportError(false);
  };

  const createExport = async () => {
    if (!datasetCode) return;
    setExportSubmitting(true);
    setExportError(false);
    try {
      const job = await createPlatformExport(tenant.id, datasetCode);
      if (!mounted.current) return;
      setExportJob(job);
      setExportConfirmOpen(false);
      message.success(english ? 'Export task created.' : '导出任务已创建。');
    } catch {
      if (!mounted.current) return;
      setExportError(true);
      message.error(english ? 'Unable to create the export task.' : '无法创建导出任务。');
    } finally {
      if (mounted.current) setExportSubmitting(false);
    }
  };

  const downloadExport = async () => {
    if (!exportJob) return;
    setDownloading(true);
    try {
      const blob = await downloadPlatformExport(tenant.id, exportJob.id);
      if (!mounted.current) return;
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${tenant.tenantCode}-${exportJob.resource}.zip`;
      link.click();
      window.URL.revokeObjectURL(url);
      message.success(english ? 'Download started.' : '已开始下载。');
    } catch {
      if (!mounted.current) return;
      setExportError(true);
      message.error(english ? 'Unable to download the export.' : '无法下载导出文件。');
    } finally {
      if (mounted.current) setDownloading(false);
    }
  };

  return <>
    <Card className="platform-dataset-card" title={english ? 'Authorized datasets' : '已授权数据集'} extra={<Space wrap>
      <Select allowClear placeholder={english ? 'Choose a dataset' : '请选择数据集'} value={datasetCode} onChange={selectDataset} options={availableDatasets.map((item) => ({ value: item.code, label: english ? item.en : item.zh }))} />
      {selectedAccess.export ? <Button onClick={() => setExportConfirmOpen(true)}>{english ? 'Export' : '导出'}</Button> : null}
    </Space>}>
      {datasetCode ? <>
        {dataError ? <Alert message={english ? 'Unable to load this dataset.' : '该数据集加载失败。'} type="error" /> : null}
        {exportError ? <Alert message={english ? 'The export task could not be completed. Please try again or review the platform audit log.' : '导出任务未能完成，请重试或查看平台审计日志。'} type="error" /> : null}
        {exportJob ? <Alert className="platform-export-status" message={english ? `Export status: ${exportJob.status}` : `导出状态：${exportJob.status}`} description={exportJob.status === 'COMPLETED' ? (english ? `Ready. ${exportJob.recordCount ?? 0} records. File expires at ${exportJob.expiresAt}.` : `已完成，共 ${exportJob.recordCount ?? 0} 条记录；文件将在 ${exportJob.expiresAt} 失效。`) : (english ? 'The task is running in the background. This status updates automatically.' : '任务正在后台处理，状态会自动更新。')} type={exportJob.status === 'FAILED' ? 'error' : exportJob.status === 'COMPLETED' ? 'success' : 'info'} action={exportJob.status === 'COMPLETED' ? <Button loading={downloading} onClick={() => void downloadExport()} size="small" type="primary">{english ? 'Download ZIP' : '下载 ZIP'}</Button> : null} showIcon /> : null}
        {selectedAccess.read ? <>
          <Table<Record<string, unknown>> columns={columns} dataSource={data?.content ?? []} loading={dataLoading} pagination={false} rowKey="id" scroll={{ x: 'max-content' }} size="small" />
          {(data?.totalElements ?? 0) > 0 ? <Pagination className="platform-data-pagination" current={dataPage + 1} pageSize={DATA_PAGE_SIZE} showSizeChanger={false} total={data?.totalElements ?? 0} onChange={(page) => setDataPage(page - 1)} showTotal={(total) => english ? `${total} records` : `共 ${total} 条`} /> : null}
        </> : <Empty description={english ? 'Export only. This page will not read or display tenant records.' : '当前仅获授权导出；本页面不会读取或显示租户记录。'} image={Empty.PRESENTED_IMAGE_SIMPLE} />}
      </> : <Empty description={english ? 'Choose a dataset to read data' : '请选择数据集后再读取数据'} image={Empty.PRESENTED_IMAGE_SIMPLE} />}
    </Card>
    <Modal cancelText={english ? 'Cancel' : '取消'} confirmLoading={exportSubmitting} okText={english ? 'Create export task' : '创建导出任务'} onCancel={() => setExportConfirmOpen(false)} onOk={() => void createExport()} open={exportConfirmOpen} title={english ? 'Confirm export' : '确认导出'}>
      <Typography.Paragraph>{english ? 'This creates one ZIP export for the selected tenant and dataset. Creation and download are recorded in the platform audit log.' : '将为当前选定的租户和数据集创建一个 ZIP 导出文件；创建和下载都会写入平台审计日志。'}</Typography.Paragraph>
      <Descriptions column={1} size="small">
        <Descriptions.Item label={english ? 'Tenant' : '租户'}>{tenant.displayName}</Descriptions.Item>
        <Descriptions.Item label={english ? 'Dataset' : '数据集'}>{datasetCode ? (english ? dataset.en : dataset.zh) : '-'}</Descriptions.Item>
      </Descriptions>
    </Modal>
  </>;
}
