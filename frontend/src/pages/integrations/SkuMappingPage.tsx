import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Select, Space, Switch, Tag, Tooltip } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  createSkuMapping,
  listSkuMappings,
  SKU_MAPPINGS_QUERY_KEY,
  updateSkuMapping,
  type ChannelSkuMapping,
  type ChannelSkuMappingPayload,
} from '../../api/integrations';
import { paginateArray } from '../../api/pagination';
import { listProductSkus } from '../../api/productSkus';
import { ChannelTag } from '../../components/ChannelTag';
import { formatDateTime } from '../workflowUtils';
import { SkuMappingFormModal } from './SkuMappingFormModal';

interface MappingTableParams { current?: number; pageSize?: number; search?: string; statusFilter?: string }

export function SkuMappingPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [channel, setChannel] = useState('SHOPIFY');
  const [formOpen, setFormOpen] = useState(false);
  const [editingMapping, setEditingMapping] = useState<ChannelSkuMapping>();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const productsQuery = useQuery({ queryKey: ['product-skus', 'integration-mapping'], queryFn: () => listProductSkus({ enabledOnly: true }) });
  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id?: number; payload: ChannelSkuMappingPayload }) =>
      id === undefined ? createSkuMapping(payload) : updateSkuMapping(id, payload),
  });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: SKU_MAPPINGS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const save = async (payload: ChannelSkuMappingPayload): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editingMapping?.id, payload: { ...payload, externalSku: payload.externalSku || editingMapping?.externalSku || '' } });
      message.success(t(editingMapping ? 'integrations.mappings.messages.updated' : 'integrations.mappings.messages.created'));
      setFormOpen(false);
      setEditingMapping(undefined);
      await refresh();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const toggleStatus = async (mapping: ChannelSkuMapping, active: boolean): Promise<void> => {
    if (mapping.id === undefined || !mapping.externalSku) return;
    try {
      await saveMutation.mutateAsync({
        id: mapping.id,
        payload: {
          channel: mapping.channel,
          externalSku: mapping.externalSku,
          mappingType: mapping.mappingType,
          productSkuId: mapping.productSkuId,
          quantityRatio: mapping.quantityRatio,
          status: active ? 'ACTIVE' : 'DISABLED',
          remark: mapping.remark,
        },
      });
      message.success(t(active ? 'integrations.mappings.messages.activated' : 'integrations.mappings.messages.deactivated'));
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const columns = useMemo<ProColumns<ChannelSkuMapping>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('integrations.mappings.searchPlaceholder') } },
    {
      title: t('integrations.fields.status'), dataIndex: 'statusFilter', hideInTable: true, valueType: 'select',
      valueEnum: { ACTIVE: { text: t('common.enabled') }, DISABLED: { text: t('common.disabled') } },
    },
    { title: t('integrations.fields.externalSku'), dataIndex: 'externalSku', width: 250, fixed: 'left', search: false, copyable: true },
    { title: t('integrations.fields.channel'), dataIndex: 'channel', width: 105, search: false, render: (_, row) => <ChannelTag channel={row.channel} /> },
    { title: t('integrations.fields.store'), dataIndex: 'storeIdentifier', width: 210, search: false, ellipsis: true },
    {
      title: t('integrations.fields.internalProduct'), dataIndex: 'productName', width: 280, search: false,
      render: (_, row) => row.mappingType === 'VIRTUAL'
        ? <Tag color="purple">{t('integrations.mappings.types.VIRTUAL')}</Tag>
        : <Space direction="vertical" size={0}><strong>{row.productName ?? '-'}</strong><span className="table-secondary">{row.productBarcode ?? '-'}</span></Space>,
    },
    { title: t('integrations.fields.quantityRatio'), dataIndex: 'quantityRatio', width: 130, align: 'right', search: false, renderText: (value) => `1 : ${String(value ?? 1)}` },
    { title: t('integrations.fields.source'), dataIndex: 'source', width: 115, search: false, render: (_, row) => <Tag color={row.source === 'AUTO' ? 'blue' : 'default'}>{t(`integrations.mappings.sources.${row.source ?? 'MANUAL'}`, { defaultValue: row.source ?? '-' })}</Tag> },
    {
      title: t('integrations.fields.status'), dataIndex: 'status', width: 105, search: false,
      render: (_, row) => <Switch checked={row.status === 'ACTIVE'} loading={saveMutation.isPending} onChange={(checked) => void toggleStatus(row, checked)} />,
    },
    { title: t('common.updatedAt'), dataIndex: 'updatedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: 88, fixed: 'right',
      render: (_, row) => [
        <Tooltip key="edit" title={t('common.edit')}>
          <Button aria-label={t('common.edit')} icon={<EditOutlined />} onClick={() => { setEditingMapping(row); setFormOpen(true); }} size="small" type="text" />
        </Tooltip>,
      ],
    },
  ], [i18n.language, saveMutation.isPending, t]);

  return (
    <section className="data-page integration-page">
      <ProTable<ChannelSkuMapping, MappingTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('integrations.mappings.title')}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const mappings = await listSkuMappings(channel);
            const keyword = params.search?.trim().toLowerCase();
            const filtered = mappings.filter((item) => {
              const matchesSearch = !keyword || [item.externalSku, item.productName, item.productBarcode, item.storeIdentifier].some((value) => value?.toLowerCase().includes(keyword));
              return matchesSearch && (!params.statusFilter || item.status === params.statusFilter);
            });
            return paginateArray(filtered, params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1570 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <Select key="channel" onChange={(value) => { setChannel(value); actionRef.current?.reload(); }} options={[{ label: 'Shopify', value: 'SHOPIFY' }]} value={channel} />,
          <Button icon={<PlusOutlined />} key="create" onClick={() => { setEditingMapping(undefined); setFormOpen(true); }} type="primary">{t('integrations.mappings.actions.create')}</Button>,
        ]}
      />

      <SkuMappingFormModal loading={saveMutation.isPending} mapping={editingMapping} onClose={() => { setFormOpen(false); setEditingMapping(undefined); }} onSubmit={save} open={formOpen} products={productsQuery.data ?? []} />
    </section>
  );
}
