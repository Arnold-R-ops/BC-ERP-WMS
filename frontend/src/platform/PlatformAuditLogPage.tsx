import { Alert, Button, Card, Empty, Input, InputNumber, Pagination, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { searchPlatformAuditLogs, type PlatformAuditLog, type PlatformAuditLogPage } from './api';

const PAGE_SIZE = 20;
interface AuditFilters {
  from: string;
  to: string;
  tenantId?: number;
  action?: string;
}

function localDate(daysFromToday = 0): string {
  const date = new Date();
  date.setDate(date.getDate() + daysFromToday);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function startOfLocalDay(value: string): string {
  return new Date(`${value}T00:00:00`).toISOString();
}

function endOfLocalDay(value: string): string {
  return new Date(`${value}T23:59:59.999`).toISOString();
}

const ACTIONS = [
  { value: 'READ', zh: '读取', en: 'Read' },
  { value: 'EXPORT', zh: '导出', en: 'Export' },
  { value: 'AUDIT_READ', zh: '查看平台审计', en: 'Audit review' },
  { value: 'OPERATION_REQUESTED', zh: '申请租户操作', en: 'Operation requested' },
  { value: 'OPERATION_EXECUTED', zh: '执行租户操作', en: 'Operation executed' },
  { value: 'MFA_ENROLLED', zh: '启用 MFA', en: 'MFA enrolled' },
  { value: 'MFA_VERIFIED', zh: 'MFA 验证成功', en: 'MFA verified' },
  { value: 'MFA_FAILED', zh: 'MFA 验证失败', en: 'MFA failed' },
  { value: 'MFA_RECOVERY_USED', zh: '使用恢复码', en: 'Recovery code used' },
  { value: 'MFA_RECOVERY_REGENERATED', zh: '重新生成恢复码', en: 'Recovery codes regenerated' },
  { value: 'MFA_RESET', zh: '管理员重置 MFA', en: 'Administrator MFA reset' },
  { value: 'ACCESS_GRANTED', zh: '授予委派授权', en: 'Delegated access granted' },
  { value: 'ACCESS_REVOKED', zh: '撤销委派授权', en: 'Delegated access revoked' },
  { value: 'ADMIN_INVITED', zh: '创建管理员邀请', en: 'Administrator invited' },
  { value: 'ADMIN_INVITATION_REVOKED', zh: '撤销管理员邀请', en: 'Administrator invitation revoked' },
  { value: 'ADMIN_INVITATION_ACCEPTED', zh: '接受管理员邀请', en: 'Administrator invitation accepted' },
];

function summaryText(summary: PlatformAuditLog['summary'], english: boolean): string {
  const labels: Record<string, [string, string]> = {
    page: ['页码', 'Page'], size: ['每页', 'Page size'], returned: ['返回条数', 'Returned'],
    total: ['总数', 'Total'], jobId: ['导出任务号', 'Export job'], recordCount: ['记录数', 'Records'],
    authorizationId: ['授权记录号', 'Authorization'], operation: ['操作', 'Operation'], resourceId: ['资源编号', 'Resource'],
    attempts: ['失败次数', 'Failed attempts'], locked: ['是否锁定', 'Locked'],
    targetPlatformUserId: ['目标平台管理员 ID', 'Target administrator ID'],
    targetSuperAdmin: ['目标为超级管理员', 'Target is super administrator'],
    sessionsRevoked: ['目标会话已撤销', 'Target sessions revoked'],
    backgroundTasksPreserved: ['后台任务保留', 'Background tasks preserved'],
    reason: ['重置原因', 'Reset reason'],
    invitationId: ['邀请记录号', 'Invitation'], roleCode: ['角色', 'Role'], expiresAt: ['到期时间', 'Expires'],
  };
  const parts = Object.entries(summary).map(([key, value]) => `${labels[key]?.[english ? 1 : 0] ?? key}: ${value ?? '-'}`);
  return parts.length ? parts.join(' · ') : '-';
}

function scopeText(value: string, english: boolean): string {
  const labels: Record<string, [string, string]> = {
    TENANT_DIRECTORY_PAGE: ['租户目录（本页汇总）', 'Tenant directory (page summary)'],
    PLATFORM_SCOPE: ['平台范围', 'Platform scope'],
    REMOVED_TENANT: ['已移除租户', 'Removed tenant'],
  };
  return labels[value]?.[english ? 1 : 0] ?? value;
}

export function PlatformAuditLogPage(): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const [filters, setFilters] = useState<AuditFilters>({ from: localDate(-7), to: localDate() });
  const [applied, setApplied] = useState<AuditFilters | null>(null);
  const [result, setResult] = useState<PlatformAuditLogPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const load = async (page: number, nextFilters = filters) => {
    setLoading(true);
    setError(false);
    try {
      const pageResult = await searchPlatformAuditLogs({
        from: startOfLocalDay(nextFilters.from),
        to: endOfLocalDay(nextFilters.to),
        tenantId: nextFilters.tenantId,
        action: nextFilters.action,
        page,
        size: PAGE_SIZE,
      });
      setApplied(nextFilters);
      setResult(pageResult);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  const columns = useMemo<ColumnsType<PlatformAuditLog>>(() => [
    { title: english ? 'Time' : '时间', dataIndex: 'createdAt', width: 190, render: (value: string) => new Date(value).toLocaleString(english ? 'en-US' : 'zh-CN', { hour12: false }) },
    { title: english ? 'Actor' : '操作者', dataIndex: 'actorEmail', width: 210, render: (value: string) => value === 'DEACTIVATED_PLATFORM_ACCOUNT' ? (english ? 'Deactivated platform account' : '已停用的平台账号') : value },
    { title: english ? 'Action' : '操作类型', dataIndex: 'action', width: 180, render: (value: string) => <Tag color="blue">{ACTIONS.find((item) => item.value === value)?.[english ? 'en' : 'zh'] ?? value}</Tag> },
    { title: english ? 'Tenant / scope' : '目标租户／范围', dataIndex: 'targetTenantName', width: 210, render: (value: string) => scopeText(value, english) },
    { title: english ? 'Dataset / resource' : '数据集／资源', dataIndex: 'resourceType', width: 170, render: (value: string | null) => value || '-' },
    { title: english ? 'Result' : '结果', dataIndex: 'result', width: 120, render: (value: string) => <Tag color={['SUCCESS', 'DOWNLOADED'].includes(value) ? 'green' : value === 'FAILED' ? 'red' : 'gold'}>{value}</Tag> },
    { title: english ? 'Safe summary' : '安全摘要', dataIndex: 'summary', width: 360, render: (value: PlatformAuditLog['summary']) => summaryText(value, english) },
  ], [english]);

  return <main className="platform-home-content platform-audit-page">
    <div className="platform-section-heading">
      <div>
        <Typography.Title level={2}>{english ? 'Platform audit log' : '平台审计日志'}</Typography.Title>
        <Typography.Text type="secondary">{english ? 'Read-only records for platform access and operations.' : '只读核对平台访问与操作记录；不提供修改、删除或导出。'}</Typography.Text>
      </div>
    </div>
    <Alert message={english ? 'Raw request metadata and full internal details are never shown here.' : '页面不会显示原始 JWT、网络信息或完整内部详情。'} showIcon type="info" />
    <Card className="platform-audit-filter" title={english ? 'Query conditions' : '查询条件'}>
      <Space align="end" wrap>
        <label className="platform-audit-field">
          <span>{english ? 'Time range (up to 31 days)' : '时间范围（最长 31 天）'}</span>
          <Space.Compact>
            <Input aria-label={english ? 'From date' : '开始日期'} max={filters.to} type="date" value={filters.from} onChange={(event) => setFilters((current) => ({ ...current, from: event.target.value }))} />
            <Input aria-label={english ? 'To date' : '结束日期'} min={filters.from} type="date" value={filters.to} onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))} />
          </Space.Compact>
        </label>
        <label className="platform-audit-field">
          <span>{english ? 'Tenant ID (optional)' : '租户 ID（可选）'}</span>
          <InputNumber min={1} placeholder={english ? 'All tenants' : '全部租户'} value={filters.tenantId} onChange={(value) => setFilters((current) => ({ ...current, tenantId: value ?? undefined }))} />
        </label>
        <label className="platform-audit-field">
          <span>{english ? 'Action (optional)' : '操作类型（可选）'}</span>
          <Select allowClear placeholder={english ? 'All actions' : '全部操作'} value={filters.action} onChange={(value) => setFilters((current) => ({ ...current, action: value }))} options={ACTIONS.map((item) => ({ value: item.value, label: english ? item.en : item.zh }))} />
        </label>
        <Button loading={loading} onClick={() => void load(0)} type="primary">{english ? 'Query' : '查询'}</Button>
      </Space>
    </Card>
    {error ? <Alert message={english ? 'Unable to query platform audit logs.' : '平台审计日志查询失败，请检查条件后重试。'} type="error" showIcon /> : null}
    <Card className="platform-audit-results" title={english ? 'Query results' : '查询结果'}>
      {result ? <>
        <Table columns={columns} dataSource={result.content} loading={loading} pagination={false} rowKey="id" scroll={{ x: 1440 }} size="small" />
        {result.totalElements > 0 ? <Pagination current={result.number + 1} pageSize={PAGE_SIZE} showSizeChanger={false} total={result.totalElements} onChange={(page) => applied && void load(page - 1, applied)} showTotal={(total) => english ? `${total} records` : `共 ${total} 条`} /> : null}
      </> : <Empty description={english ? 'Set the conditions and click Query' : '设置条件后点击“查询”'} image={Empty.PRESENTED_IMAGE_SIMPLE} />}
    </Card>
  </main>;
}
