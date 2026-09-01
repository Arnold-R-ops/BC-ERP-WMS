import { EyeOutlined, PlusOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Card, Input, Space, Table, Tabs, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { getErrorMessage } from '../../api/errors';
import {
  createTransfer,
  createTransferCommandFingerprint,
  createTransferIdempotencyKey,
  getTransfer,
  getTransferPolicy,
  getTransferReferences,
  listTransfers,
  runTransferAction,
  TRANSFER_POLICY_QUERY_KEY,
  TRANSFER_REFERENCES_QUERY_KEY,
  TRANSFERS_QUERY_KEY,
  updateTransfer,
  updateTransferPolicy,
  type Transfer,
  type TransferAction,
  type TransferActionPayloadMap,
  type TransferActionRequest,
  type TransferBusinessStatus,
  type TransferDraftPayload,
  type TransferPolicyUpdatePayload,
} from '../../api/transfers';
import { useAuth } from '../../auth/AuthProvider';
import { OrderStatusTag } from '../../components/OrderStatusTag';
import { TransferDetailDrawer } from './TransferDetailDrawer';
import { TransferFormDrawer } from './TransferFormDrawer';
import { TransferPolicyModal } from './TransferPolicyModal';

interface IdempotencyAttempt { fingerprint: string; key: string }

const BUSINESS_TABS: Array<TransferBusinessStatus | undefined> = [
  undefined, 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'COMPLETED', 'CLOSED', 'REJECTED', 'CANCELLED', 'VOIDED',
];

export function TransferPage(): JSX.Element {
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const english = i18n.language === 'en-US';
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const attempts = useRef(new Map<string, IdempotencyAttempt>());
  const [businessStatus, setBusinessStatus] = useState<TransferBusinessStatus>();
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState<number>();
  const [formOpen, setFormOpen] = useState(false);
  const [editTransfer, setEditTransfer] = useState<Transfer>();
  const [policyOpen, setPolicyOpen] = useState(false);

  const canCreate = hasPermission(session?.currentRole, session?.permissionCodes, 'transfer:create');
  const canApprove = hasPermission(session?.currentRole, session?.permissionCodes, 'transfer:approve');
  const canClose = hasPermission(session?.currentRole, session?.permissionCodes, 'transfer:close');
  const canConfigure = hasPermission(session?.currentRole, session?.permissionCodes, 'transfer:policy');

  const transfers = useQuery({
    queryKey: TRANSFERS_QUERY_KEY,
    queryFn: listTransfers,
  });
  const detail = useQuery({
    queryKey: [...TRANSFERS_QUERY_KEY, 'detail', selectedId],
    queryFn: () => getTransfer(selectedId as number),
    enabled: selectedId !== undefined,
  });
  const references = useQuery({
    queryKey: TRANSFER_REFERENCES_QUERY_KEY,
    queryFn: getTransferReferences,
    enabled: canCreate,
  });
  const policy = useQuery({
    queryKey: TRANSFER_POLICY_QUERY_KEY,
    queryFn: getTransferPolicy,
    enabled: canConfigure || canCreate,
  });

  const commandKey = (operation: string, resource: string | number, payload: object): string => {
    const fingerprint = createTransferCommandFingerprint(resource, payload);
    const attempt = attempts.current.get(operation);
    if (attempt?.fingerprint === fingerprint) return attempt.key;
    const key = createTransferIdempotencyKey(operation, resource);
    attempts.current.set(operation, { fingerprint, key });
    return key;
  };
  const finishCommand = (operation: string): void => { attempts.current.delete(operation); };

  const refresh = async (): Promise<void> => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: TRANSFERS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: ['transfer-warehouse-tasks'] }),
    ]);
  };

  const saveMutation = useMutation({
    mutationFn: async ({ transfer, payload }: { transfer?: Transfer; payload: TransferDraftPayload }) => {
      const operation = transfer ? `update-${transfer.id}` : 'create';
      let result: Transfer;
      if (transfer) {
        const requestPayload = { ...payload, version: transfer.version };
        const idempotencyKey = commandKey(operation, transfer.id, requestPayload);
        result = await updateTransfer(transfer.id, requestPayload, idempotencyKey);
      } else {
        const idempotencyKey = commandKey(operation, 'draft', payload);
        result = await createTransfer(payload, idempotencyKey);
      }
      finishCommand(operation);
      return result;
    },
  });
  const actionMutation = useMutation({
    mutationFn: async ({ action, payload }: TransferActionRequest) => {
      if (!selectedId) throw new Error('No transfer selected');
      const operation = `${action}-${selectedId}`;
      const result = await runTransferAction(selectedId, action, payload, commandKey(operation, selectedId, payload));
      finishCommand(operation);
      return result;
    },
  });
  const policyMutation = useMutation({
    mutationFn: async (payload: TransferPolicyUpdatePayload) => {
      const operation = 'policy';
      const result = await updateTransferPolicy(payload, commandKey(operation, 'tenant', payload));
      finishCommand(operation);
      return result;
    },
  });

  const saveDraft = async (payload: TransferDraftPayload): Promise<void> => {
    try {
      const saved = await saveMutation.mutateAsync({ transfer: editTransfer, payload });
      setFormOpen(false);
      setEditTransfer(undefined);
      setSelectedId(saved.id);
      message.success(english ? 'Transfer draft saved.' : '调拨草稿已保存');
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const performAction = async <TAction extends TransferAction>(
    action: TAction,
    payload: TransferActionPayloadMap[TAction],
  ): Promise<void> => {
    try {
      const result = await actionMutation.mutateAsync({ action, payload } as TransferActionRequest);
      queryClient.setQueryData([...TRANSFERS_QUERY_KEY, 'detail', result.id], result);
      message.success(english ? 'Transfer updated.' : '调拨单已更新');
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
      throw error;
    }
  };

  const savePolicy = async (payload: TransferPolicyUpdatePayload): Promise<void> => {
    try {
      const saved = await policyMutation.mutateAsync(payload);
      queryClient.setQueryData(TRANSFER_POLICY_QUERY_KEY, saved);
      setPolicyOpen(false);
      message.success(english ? 'Transfer policy saved.' : '调拨策略已保存');
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const data = useMemo(() => {
    const statusFiltered = (transfers.data ?? []).filter((transfer) => !businessStatus || transfer.businessStatus === businessStatus);
    const keyword = search.trim().toLowerCase();
    if (!keyword) return statusFiltered;
    return statusFiltered.filter((transfer) => [
      transfer.transferNumber,
      transfer.sourceWarehouseCode,
      transfer.sourceWarehouseName,
      transfer.destinationWarehouseCode,
      transfer.destinationWarehouseName,
    ].some((value) => value?.toLowerCase().includes(keyword)));
  }, [businessStatus, search, transfers.data]);

  const columns: TableColumnsType<Transfer> = [
    { title: english ? 'Transfer' : '调拨单号', dataIndex: 'transferNumber', fixed: 'left', width: 210 },
    { title: english ? 'Source warehouse' : '调出仓', width: 180, render: (_, item) => `${item.sourceWarehouseCode} · ${item.sourceWarehouseName}` },
    { title: english ? 'Destination warehouse' : '调入仓', width: 180, render: (_, item) => `${item.destinationWarehouseCode} · ${item.destinationWarehouseName}` },
    { title: english ? 'Business status' : '业务状态', dataIndex: 'businessStatus', width: 145, render: (value: string) => <OrderStatusTag domain="transfers" status={value} /> },
    { title: english ? 'Dispatch progress' : '调出进度', dataIndex: 'outboundStatus', width: 145, render: (value: string) => <OrderStatusTag domain="transfers" status={value} /> },
    { title: english ? 'Receipt progress' : '调入进度', dataIndex: 'inboundStatus', width: 145, render: (value: string) => <OrderStatusTag domain="transfers" status={value} /> },
    { title: english ? 'Estimated arrival' : '预计到达', dataIndex: 'estimatedArrivalAt', width: 165, render: (value?: string) => value ? value.replace('T', ' ').slice(0, 16) : '-' },
    { title: english ? 'Action' : '操作', fixed: 'right', width: 100, render: (_, item) => <Button icon={<EyeOutlined />} onClick={() => setSelectedId(item.id)} size="small">{english ? 'View' : '查看'}</Button> },
  ];

  return (
    <section>
      <Typography.Title level={2}>{english ? 'Warehouse transfers' : '调拨管理'}</Typography.Title>
      <Typography.Paragraph type="secondary">
        {english ? 'Business approval, source dispatch and destination receipt are tracked independently.' : '业务审批、调出仓执行和调入仓收货分别记录，避免一条混合状态掩盖真实进度。'}
      </Typography.Paragraph>
      <Card style={{ marginBottom: 12 }}>
        <Space wrap>
          <Button icon={<ReloadOutlined spin={transfers.isFetching} />} onClick={() => void refresh()}>{english ? 'Refresh' : '刷新'}</Button>
          {canCreate ? <Button icon={<PlusOutlined />} onClick={() => { setEditTransfer(undefined); setFormOpen(true); }} type="primary">{english ? 'New transfer' : '新建调拨'}</Button> : null}
          {canConfigure ? <Button icon={<SettingOutlined />} onClick={() => setPolicyOpen(true)}>{english ? 'Policy' : '调拨策略'}</Button> : null}
          <Input.Search allowClear onChange={(event) => setSearch(event.target.value)} placeholder={english ? 'Search transfer or warehouse' : '搜索调拨单号或仓库'} style={{ width: 300 }} value={search} />
        </Space>
      </Card>
      <Tabs
        activeKey={businessStatus ?? 'ALL'}
        items={BUSINESS_TABS.map((status) => ({ key: status ?? 'ALL', label: status ? t(`transfers.statuses.${status}`, { defaultValue: status }) : (english ? 'All' : '全部') }))}
        onChange={(value) => setBusinessStatus(value === 'ALL' ? undefined : value as TransferBusinessStatus)}
      />
      <Table columns={columns} dataSource={data} loading={transfers.isLoading} pagination={{ pageSize: 15 }} rowKey="id" scroll={{ x: 1280 }} />

      <TransferFormDrawer
        initial={editTransfer}
        loading={saveMutation.isPending}
        onClose={() => { setFormOpen(false); setEditTransfer(undefined); }}
        onSubmit={saveDraft}
        open={formOpen}
        references={references.data}
      />
      <TransferDetailDrawer
        canApprove={canApprove}
        canClose={canClose}
        canCreate={canCreate}
        currentUsername={session?.username}
        loading={actionMutation.isPending}
        onAction={performAction}
        onClose={() => setSelectedId(undefined)}
        onEdit={(item) => { setEditTransfer(item); setFormOpen(true); }}
        open={selectedId !== undefined}
        transfer={detail.data}
      />
      <TransferPolicyModal
        loading={policyMutation.isPending}
        onCancel={() => setPolicyOpen(false)}
        onSubmit={savePolicy}
        open={policyOpen}
        policy={policy.data}
      />
    </section>
  );
}
