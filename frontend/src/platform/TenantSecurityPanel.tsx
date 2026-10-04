import { Alert, App, Button, Card, Checkbox, Form, Input, Modal, Select, Space, Table, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { platformRequest } from './api';

type Action = 'SUSPEND' | 'REQUEST_RESUME' | 'APPROVE_RESUME' | 'REJECT_RESUME' | 'WITHDRAW_RESUME' | 'RECORD_CONNECTION_REVIEW' | 'REPLACE_CONNECTION_CREDENTIALS' | 'RECORD_RECONCILIATION' | 'RESOLVE_ALERT';
interface Draft { action: Action; reason: string; requestId?: string; affectedConnectionIds?: number[] | null; configId?: number; remoteStatus?: string; connectionCheckPassed?: boolean; credentials?: { clientSecret?: string; accessToken?: string }; reportEventId?: number; alertId?: number; }
interface RequestRow { public_id: string; initiator_id: number; status: string; reason: string; }
interface ConnectionRow { config_id: number; state: string; remote_status: string; check_status: string; gap_status: string; evidence?: string; }
interface Overview { status: string; suspension_kind?: string; suspension_reason?: string; controlsEnabled: boolean; actorId: number; requests: RequestRow[]; connections: ConnectionRow[]; alerts: { id: number; summary: string; status: string; mail_status: string }[]; }
const labels: Record<Action, string> = { SUSPEND: '紧急安全暂停', REQUEST_RESUME: '提交安全恢复申请', APPROVE_RESUME: '批准并恢复服务', REJECT_RESUME: '驳回恢复申请', WITHDRAW_RESUME: '撤回我的申请', RECORD_CONNECTION_REVIEW: '记录连接核验证据', REPLACE_CONNECTION_CREDENTIALS: '更新本地加密凭据', RECORD_RECONCILIATION: '核验数据补齐结果', RESOLVE_ALERT: '核验并解除告警' };

export function TenantSecurityPanel({ tenantId, onChanged }: { tenantId: number; onChanged: () => void }): JSX.Element {
  const { message } = App.useApp();
  const [data, setData] = useState<Overview>();
  const [failed, setFailed] = useState(false);
  const [revision, setRevision] = useState(0);
  const [draft, setDraft] = useState<Draft>();
  const [busy, setBusy] = useState(false);
  const [connectionRisk, setConnectionRisk] = useState(true);
  const [form] = Form.useForm();
  const retryCommand = useRef<Record<string, unknown>>();
  const base = `/api/platform/tenants/${tenantId}/security`;
  useEffect(() => {
    let active = true;
    setFailed(false);
    void platformRequest<Overview>(base).then(result => { if (active) setData(result); }).catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, [base, revision]);
  function begin(action: Draft) {
    retryCommand.current = undefined;
    form.resetFields();
    setConnectionRisk(true);
    setDraft(action);
  }
  function completed() {
    setDraft(undefined); form.resetFields(); retryCommand.current = undefined;
    message.success('本次命令已完成。租户状态、连接检查和数据补齐结果请分别查看。');
    setRevision(value => value + 1); onChanged();
  }
  async function execute() {
    if (!draft) return;
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    setBusy(true);
    try {
      if (!retryCommand.current) {
        const payload: Draft = { ...draft, reason: values.reason, ...(draft.action === 'SUSPEND' ? { affectedConnectionIds: connectionRisk ? null : [] } : {}), ...(draft.action === 'RECORD_CONNECTION_REVIEW' ? { remoteStatus: values.remoteStatus, connectionCheckPassed: !!values.connectionCheckPassed } : {}) };
        if (draft.action === 'REPLACE_CONNECTION_CREDENTIALS') payload.credentials = { clientSecret: values.clientSecret || undefined, accessToken: values.accessToken || undefined };
        if (draft.action === 'RECORD_RECONCILIATION') payload.reportEventId = Number(values.reportEventId);
        const challenge = await platformRequest<{ challengeToken: string }>(`${base}/challenges`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        retryCommand.current = { idempotencyKey: crypto.randomUUID(), challengeToken: challenge.challengeToken, draft: payload };
      }
      await platformRequest(`${base}/commands`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...retryCommand.current, password: values.password, code: values.code }) });
      completed();
    } catch {
      if (retryCommand.current?.idempotencyKey) {
        try {
          await platformRequest(`${base}/commands/${encodeURIComponent(String(retryCommand.current.idempotencyKey))}`);
          completed(); return;
        } catch { /* No confirmed outcome: keep the same key for an explicit retry. */ }
      }
      message.error('操作未获确认。请检查当前状态；重试保留同一命令编号，不重复执行已完成命令。');
      setRevision(value => value + 1);
    } finally { setBusy(false); }
  }
  if (failed) return <Alert type="error" message="安全状态暂不可用，操作保持关闭" action={<Button onClick={() => setRevision(value => value + 1)}>重试</Button>} />;
  if (!data) return <Card loading />;
  const enabled = data.controlsEnabled && !busy;
  return <Space direction="vertical" style={{ width: '100%' }}>
    <Alert showIcon type="warning" message={`当前状态：${data.status}`} description="租户服务恢复不代表每条连接或历史数据已经恢复。本批不提供关闭、清理或计划暂停。" />
    {!data.controlsEnabled && <Alert type="info" message="安全操作尚未在此环境开放" />}
    {data.suspension_reason && <Typography.Paragraph>最近安全暂停原因：{data.suspension_reason}</Typography.Paragraph>}
    <Space>
      <Button danger disabled={!enabled || data.status !== 'ACTIVE'} onClick={() => begin({ action: 'SUSPEND', reason: '' })}>紧急安全暂停</Button>
      <Button disabled={!enabled || data.status !== 'SUSPENDED' || data.suspension_kind !== 'SECURITY' || data.requests.some(row => row.status === 'PENDING')} onClick={() => begin({ action: 'REQUEST_RESUME', reason: '' })}>提交安全恢复申请</Button>
      <Button onClick={() => setRevision(value => value + 1)}>刷新状态</Button>
    </Space>
    <Table rowKey="public_id" size="small" pagination={false} dataSource={data.requests} columns={[
      { title: '申请编号', dataIndex: 'public_id' }, { title: '状态', dataIndex: 'status' }, { title: '说明', dataIndex: 'reason' },
      { title: '操作', render: (_, row) => row.status !== 'PENDING' ? '-' : row.initiator_id === data.actorId
        ? <Button disabled={!enabled} onClick={() => begin({ action: 'WITHDRAW_RESUME', requestId: row.public_id, reason: '' })}>撤回</Button>
        : <Space><Button disabled={!enabled} onClick={() => begin({ action: 'APPROVE_RESUME', requestId: row.public_id, reason: '' })}>批准并恢复</Button><Button disabled={!enabled} onClick={() => begin({ action: 'REJECT_RESUME', requestId: row.public_id, reason: '' })}>驳回</Button></Space> },
    ]} />
    <Typography.Text>连接结果（CHECK_PASSED 仅表示核验通过；租户暂停时仍不运行。失败连接保持停用）：</Typography.Text>
    <Table rowKey="config_id" size="small" pagination={false} dataSource={data.connections} columns={[
      { title: '连接 ID', dataIndex: 'config_id' }, { title: '本地状态', dataIndex: 'state' }, { title: '远端凭据证据', dataIndex: 'remote_status' }, { title: '连接检查', dataIndex: 'check_status' }, { title: '数据补齐', dataIndex: 'gap_status' },
      { title: '操作', render: (_, row) => <Space wrap>
        <Button disabled={!enabled || !['ACTIVE', 'SUSPENDED'].includes(data.status)} onClick={() => begin({ action: 'RECORD_CONNECTION_REVIEW', configId: row.config_id, reason: '' })}>记录核验</Button>
        <Button disabled={!enabled} onClick={() => begin({ action: 'REPLACE_CONNECTION_CREDENTIALS', configId: row.config_id, reason: '' })}>更新凭据</Button>
        <Button disabled={!enabled || data.status !== 'ACTIVE'} onClick={() => begin({ action: 'RECORD_RECONCILIATION', configId: row.config_id, reason: '' })}>核验补齐</Button>
      </Space> },
    ]} />
    {data.alerts.filter(row => row.status === 'OPEN').map(row => <Alert key={row.id} type="error" message={row.summary} description={`告警 #${row.id}；邮件状态：${row.mail_status}`} action={<Button disabled={!enabled || data.status !== 'ACTIVE'} onClick={() => begin({ action: 'RESOLVE_ALERT', alertId: row.id, reason: '' })}>核验解除</Button>} />)}
    <Modal open={!!draft} title={draft ? labels[draft.action] : ''} confirmLoading={busy} okText={retryCommand.current ? '同编号重试' : '认证并执行'} onOk={() => void execute()} onCancel={() => { if (!busy) { setDraft(undefined); form.resetFields(); retryCommand.current = undefined; } }} destroyOnClose>
      <Alert type="warning" showIcon message={draft?.action === 'SUSPEND' ? '确认后立即暂停业务并撤销全租户会话。' : draft?.action === 'REQUEST_RESUME' ? '这里只提交恢复申请；另一名平台超级管理员批准后才恢复服务。' : draft?.action === 'APPROVE_RESUME' ? '批准与服务恢复同时生效；审批后不能撤销原申请。' : '本次操作记录审计，不向租户主动发送通知。'} />
      <Form form={form} layout="vertical" preserve={false} style={{ marginTop: 16 }}>
        <Form.Item name="reason" label="操作原因／风险解除证据" rules={[{ required: true, whitespace: true }, { max: 2000 }]}><Input.TextArea maxLength={2000} disabled={!!retryCommand.current} /></Form.Item>
        {draft?.action === 'SUSPEND' && <Checkbox checked={connectionRisk} disabled={!!retryCommand.current} onChange={event => setConnectionRisk(event.target.checked)}>涉及连接凭据风险或范围尚不明确：将本租户全部连接标记为待核验。仅确定与连接无关时取消。</Checkbox>}
        {draft?.action === 'RECORD_CONNECTION_REVIEW' && <>
          <Alert type="info" message="这里记录人工核验的真实证据，不会自动执行第三方撤权。凭据可用不等于泄露风险消除。" />
          <Form.Item name="remoteStatus" label="远端凭据核验结果" rules={[{ required: true }]}><Select disabled={!!retryCommand.current} options={[{ value: 'NOT_REQUIRED', label: '未受影响，无需撤权' }, { value: 'REVOCATION_VERIFIED', label: '已核验旧凭据撤销' }, { value: 'ROTATION_VERIFIED', label: '已核验凭据更换及旧凭据失效' }, { value: 'FAILED', label: '未成功，继续阻断' }]} /></Form.Item>
          <Form.Item name="connectionCheckPassed" valuePropName="checked"><Checkbox disabled={!!retryCommand.current}>已检查当前连接有效，且具备核验证据</Checkbox></Form.Item>
        </>}
        {draft?.action === 'REPLACE_CONNECTION_CREDENTIALS' && <>
          <Alert type="info" message="只更新本平台加密保存的凭据，不代表第三方旧凭据已经撤销。保存后必须重新核验；留空表示不修改该项。" />
          <Form.Item name="clientSecret" label="新 Webhook 签名密钥 / Client Secret"><Input.Password autoComplete="off" disabled={!!retryCommand.current} /></Form.Item>
          <Form.Item name="accessToken" label="新 Access Token"><Input.Password autoComplete="off" disabled={!!retryCommand.current} /></Form.Item>
        </>}
        {draft?.action === 'RECORD_RECONCILIATION' && <>
          <Alert type="warning" message="先在既有渠道对账功能生成覆盖暂停期间、无缺失订单的报告。原因中须另行说明取消、修改和退款已人工核验的证据；仅订单数量一致不代表全部补齐。" />
          <Form.Item name="reportEventId" label="恢复后生成的对账报告 ID" rules={[{ required: true, pattern: /^[1-9]\d*$/ }]}><Input disabled={!!retryCommand.current} /></Form.Item>
        </>}
        <Form.Item name="password" label="当前平台管理员密码" rules={[{ required: true }]}><Input.Password autoComplete="off" /></Form.Item>
        <Form.Item name="code" label="六位 TOTP" rules={[{ required: true, pattern: /^\d{6}$/ }]}><Input autoComplete="off" maxLength={6} /></Form.Item>
      </Form>
    </Modal>
  </Space>;
}
