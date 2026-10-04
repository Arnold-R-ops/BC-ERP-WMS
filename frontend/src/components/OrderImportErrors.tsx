import { Alert, Table } from 'antd';
import { ApiError } from '../api/http';

interface ImportRowError { rowIndex: number; error: string }

export function orderImportErrors(error: unknown): ImportRowError[] {
  if (!(error instanceof ApiError)) return [];
  const rows = error.body?.params?.errors;
  if (!Array.isArray(rows)) return [];
  return rows.filter((row): row is ImportRowError =>
    typeof row === 'object' && row !== null && Number.isInteger(row.rowIndex) && typeof row.error === 'string');
}

export function OrderImportErrors({ error }: { error: unknown }) {
  const rows = orderImportErrors(error);
  const params = error instanceof ApiError ? error.body?.params : undefined;
  return <>
    <Alert type="warning" showIcon message="本次未导入，请修正后重新上传。"
      description={`可解析 ${Number(params?.validRows ?? 0)} 行，错误 ${rows.length} 行，跳过空行 ${Number(params?.skippedRows ?? 0)} 行。`} />
    <Table<ImportRowError> size="small" rowKey="rowIndex" dataSource={rows} pagination={{ pageSize: 10 }}
      columns={[{ title: 'Excel 行号', dataIndex: 'rowIndex', width: 110 }, { title: '错误原因', dataIndex: 'error' }]} />
  </>;
}
