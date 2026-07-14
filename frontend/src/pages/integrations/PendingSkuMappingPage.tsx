import { ApartmentOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Space, Tabs, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState, type Key } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  listPendingSkuMappings,
  PENDING_SKU_QUERY_KEY,
  resolvePendingSku,
  SKU_MAPPINGS_QUERY_KEY,
  type PendingSkuMapping,
  type ResolvePendingSkuPayload,
} from '../../api/integrations';
import { paginateArray } from '../../api/pagination';
import { listProducts } from '../../api/products';
import { ChannelTag } from '../../components/ChannelTag';
import { formatDateTime, formatMoney } from '../workflowUtils';
import { isNoSkuExternal } from './integrationUtils';
import { PendingSkuResolutionPanel } from './PendingSkuResolutionPanel';
import { ShopifySyncButton } from './ShopifySyncButton';

interface PendingTableParams { current?: number; pageSize?: number; search?: string }
type PendingStatus = 'PENDING' | 'RESOLVED' | 'IGNORED';

export function PendingSkuMappingPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<PendingStatus>('PENDING');
  const [expandedRowKeys, setExpandedRowKeys] = useState<Key[]>([]);
  const [syncReady, setSyncReady] = useState(false);
  const { i18n, t } = useTranslation();
  const { message, modal } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const productsQuery = useQuery({ queryKey: ['products', 'integration-mapping'], queryFn: () => listProducts(true) });
  const resolveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ResolvePendingSkuPayload }) => resolvePendingSku(id, payload),
  });

  useEffect(() => {
    setExpandedRowKeys([]);
    actionRef.current?.reloadAndRest?.();
  }, [status]);

  const refresh = async (): Promise<void> => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: PENDING_SKU_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: SKU_MAPPINGS_QUERY_KEY }),
    ]);
    actionRef.current?.reload();
  };

  const executeResolve = async (item: PendingSkuMapping, payload: ResolvePendingSkuPayload): Promise<void> => {
    if (item.id === undefined) return;
    try {
      await resolveMutation.mutateAsync({ id: item.id, payload });
      message.success(t(`integrations.pending.messages.${payload.action.toLowerCase()}`));
      if (payload.action === 'MAP' || payload.action === 'VIRTUAL') setSyncReady(true);
      setExpandedRowKeys((keys) => keys.filter((key) => key !== item.id));
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const requestResolve = (item: PendingSkuMapping, payload: ResolvePendingSkuPayload): void => {
    if (payload.action === 'MAP') {
      void executeResolve(item, payload);
      return;
    }
    modal.confirm({
      content: t(`integrations.pending.confirmations.${payload.action.toLowerCase()}`),
      okButtonProps: { danger: payload.action === 'IGNORE' },
      onOk: () => executeResolve(item, payload),
      title: t(`integrations.pending.actions.${payload.action.toLowerCase()}`),
    });
  };

  const columns = useMemo<ProColumns<PendingSkuMapping>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('integrations.pending.searchPlaceholder') } },
    {
      title: t('integrations.fields.externalSku'), dataIndex: 'externalSku', width: 245, fixed: 'left', search: false,
      render: (_, item) => (
        <Space direction="vertical" size={2}>
          <strong>{item.externalSku ?? '-'}</strong>
          {isNoSkuExternal(item.externalSku) && <Tag color="gold">{t('integrations.pending.noSku')}</Tag>}
        </Space>
      ),
    },
    { title: t('integrations.fields.externalTitle'), dataIndex: 'externalTitle', width: 320, search: false, ellipsis: true },
    { title: t('integrations.fields.channel'), dataIndex: 'channel', width: 110, search: false, render: (_, item) => <ChannelTag channel={item.channel} /> },
    { title: t('integrations.fields.store'), dataIndex: 'storeIdentifier', width: 215, search: false, ellipsis: true },
    { title: t('integrations.pending.fields.samplePrice'), dataIndex: 'sampleUnitPrice', width: 125, align: 'right', search: false, renderText: (value) => formatMoney(value as number | undefined, i18n.language) },
    { title: t('integrations.pending.fields.blockedCount'), dataIndex: 'occurrenceCount', width: 115, align: 'right', search: false, sorter: (a, b) => (a.occurrenceCount ?? 0) - (b.occurrenceCount ?? 0), render: (_, item) => <Tag color={(item.occurrenceCount ?? 0) > 1 ? 'red' : 'orange'}>{item.occurrenceCount ?? 0}</Tag> },
    { title: t('integrations.pending.fields.sampleOrder'), dataIndex: 'sampleExternalOrderNo', width: 180, search: false, copyable: true },
    { title: t('integrations.pending.fields.lastSeenAt'), dataIndex: 'lastSeenAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('integrations.fields.resolution'), dataIndex: 'resolution', width: 125, search: false, render: (_, item) => item.resolution ? <Tag>{t(`integrations.pending.resolutions.${item.resolution}`, { defaultValue: item.resolution })}</Tag> : '-' },
    {
      title: t('common.actions'), valueType: 'option', width: 105, fixed: 'right',
      render: (_, item) => status === 'PENDING' ? [
        <Button icon={<ApartmentOutlined />} key="resolve" onClick={() => setExpandedRowKeys((keys) => keys.includes(item.id as number) ? [] : [item.id as number])} size="small" type="link">
          {t('integrations.pending.actions.resolve')}
        </Button>,
      ] : [],
    },
  ], [i18n.language, status, t]);

  return (
    <section className="data-page integration-page workflow-page">
      <Tabs
        activeKey={status}
        items={(['PENDING', 'RESOLVED', 'IGNORED'] as const).map((value) => ({ key: value, label: t(`integrations.pending.tabs.${value}`) }))}
        onChange={(value) => setStatus(value as PendingStatus)}
      />

      {syncReady && (
        <Alert
          action={<ShopifySyncButton onComplete={() => { setSyncReady(false); void refresh(); }} />}
          className="integration-sync-alert"
          closable
          description={t('integrations.pending.syncNotice')}
          onClose={() => setSyncReady(false)}
          showIcon
          type="success"
        />
      )}

      <ProTable<PendingSkuMapping, PendingTableParams>
        actionRef={actionRef}
        columns={columns}
        expandable={{
          expandedRowKeys,
          expandedRowRender: (item) => (
            <PendingSkuResolutionPanel
              item={item}
              loading={resolveMutation.isPending}
              onResolve={requestResolve}
              products={productsQuery.data ?? []}
            />
          ),
          onExpand: (expanded, item) => setExpandedRowKeys(expanded ? [item.id as number] : []),
          rowExpandable: () => status === 'PENDING',
        }}
        headerTitle={t('integrations.pending.title')}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const items = await listPendingSkuMappings({ status });
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword ? items.filter((item) => [item.externalSku, item.externalTitle, item.sampleExternalOrderNo].some((value) => value?.toLowerCase().includes(keyword))) : items;
            return paginateArray(filtered, params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1810 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
      />
    </section>
  );
}
