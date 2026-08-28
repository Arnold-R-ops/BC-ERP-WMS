import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Drawer, Space, Table, Tag, type TableColumnsType } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { decidePurchaseRemainderClosure, listPurchaseRemainderClosureRequests, type PurchaseRemainderClosureRequest } from '../../api/purchasing';
import { getErrorMessage } from '../../api/errors';
import { ReasonModal } from '../../components/ReasonModal';
import { formatDateTime } from '../workflowUtils';

type ClosureAction = 'approve' | 'reject' | 'cancel';

interface PendingDecision {
  action: ClosureAction;
  request: PurchaseRemainderClosureRequest;
}

interface PurchaseRemainderClosureDrawerProps {
  canApprove: boolean;
  canRequest: boolean;
  currentUsername?: string;
  onClose: () => void;
  onChanged: () => Promise<void> | void;
  open: boolean;
}

export function PurchaseRemainderClosureDrawer({
  canApprove,
  canRequest,
  currentUsername,
  onClose,
  onChanged,
  open,
}: PurchaseRemainderClosureDrawerProps): JSX.Element {
  const [pending, setPending] = useState<PendingDecision>();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const requestsQuery = useQuery({
    enabled: open,
    queryFn: () => listPurchaseRemainderClosureRequests('PENDING'),
    queryKey: ['purchase-remainder-closure-requests', 'PENDING'],
  });
  const decisionMutation = useMutation({
    mutationFn: ({ action, id, comment }: { action: ClosureAction; id: number; comment: string }) =>
      decidePurchaseRemainderClosure(id, action, comment),
  });

  const decide = async (comment: string): Promise<void> => {
    if (!pending) return;
    try {
      await decisionMutation.mutateAsync({ action: pending.action, id: pending.request.id, comment });
      message.success(t(`purchasing.closureQueue.messages.${pending.action}`));
      setPending(undefined);
      await queryClient.invalidateQueries({ queryKey: ['purchase-remainder-closure-requests'] });
      await onChanged();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const columns: TableColumnsType<PurchaseRemainderClosureRequest> = [
    { title: t('purchasing.fields.poNumber'), dataIndex: 'purchaseOrderNumber', width: 170 },
    { title: t('purchasing.fields.asnNumber'), dataIndex: 'asnNumber', width: 180 },
    { title: t('purchasing.fields.targetWarehouse'), dataIndex: 'warehouseId', width: 110, render: (value) => `#${String(value)}` },
    { title: t('purchasing.closureQueue.reason'), dataIndex: 'reason', width: 240 },
    { title: t('purchasing.closureQueue.requestedBy'), dataIndex: 'requestedByName', width: 140 },
    { title: t('purchasing.closureQueue.requestedAt'), dataIndex: 'requestedAt', width: 180, render: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('purchasing.fields.status'), dataIndex: 'status', width: 110, render: (value) => <Tag>{String(value)}</Tag> },
    {
      title: t('common.actions'), key: 'actions', fixed: 'right', width: 210,
      render: (_, request) => (
        <Space>
          {canApprove && request.requestedByName !== currentUsername && <>
            <Button onClick={() => setPending({ action: 'approve', request })} size="small" type="link">{t('purchasing.closureQueue.approve')}</Button>
            <Button danger onClick={() => setPending({ action: 'reject', request })} size="small" type="link">{t('purchasing.closureQueue.reject')}</Button>
          </>}
          {canRequest && request.requestedByName === currentUsername
            && <Button danger onClick={() => setPending({ action: 'cancel', request })} size="small" type="link">{t('purchasing.closureQueue.cancel')}</Button>}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Drawer onClose={onClose} open={open} title={t('purchasing.closureQueue.title')} width="min(1100px, 96vw)">
        <Table
          columns={columns}
          dataSource={requestsQuery.data ?? []}
          loading={requestsQuery.isLoading}
          locale={{ emptyText: t('purchasing.closureQueue.empty') }}
          pagination={false}
          rowKey="id"
          scroll={{ x: 1160 }}
          size="small"
        />
      </Drawer>
      <ReasonModal
        danger={pending?.action !== 'approve'}
        description={pending ? t(`purchasing.closureQueue.notices.${pending.action}`) : ''}
        loading={decisionMutation.isPending}
        onCancel={() => setPending(undefined)}
        onConfirm={decide}
        open={pending !== undefined}
        title={pending ? t(`purchasing.closureQueue.${pending.action}`) : ''}
      />
    </>
  );
}
