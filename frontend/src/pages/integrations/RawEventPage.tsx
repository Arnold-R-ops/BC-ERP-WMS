import { EyeOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { App as AntdApp, Button, Select, Space, Tabs, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { listRawEvents, type RawEventSummary } from '../../api/integrations';
import { toProTablePage, toSpringPage } from '../../api/pagination';
import { ChannelTag } from '../../components/ChannelTag';
import { formatDateTime } from '../workflowUtils';
import { getEventStatusColor } from './integrationUtils';
import { RawEventDetailDrawer } from './RawEventDetailDrawer';

interface EventTableParams { current?: number; pageSize?: number }
type ReviewStatus = 'MANUAL_REVIEW' | 'FAILED';

export function RawEventPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [channel, setChannel] = useState('SHOPIFY');
  const [status, setStatus] = useState<ReviewStatus>('MANUAL_REVIEW');
  const [selectedEventId, setSelectedEventId] = useState<number>();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    actionRef.current?.reloadAndRest?.();
  }, [status]);

  const columns = useMemo<ProColumns<RawEventSummary>[]>(() => [
    { title: t('integrations.events.fields.externalId'), dataIndex: 'externalId', width: 205, fixed: 'left', search: false, copyable: true },
    { title: t('integrations.events.fields.eventType'), dataIndex: 'eventType', width: 180, search: false, renderText: (value) => t(`integrations.events.types.${String(value ?? 'ORDER')}`, { defaultValue: String(value ?? '-') }) },
    { title: t('integrations.fields.channel'), dataIndex: 'channel', width: 110, search: false, render: (_, row) => <ChannelTag channel={row.channel} /> },
    { title: t('integrations.fields.store'), dataIndex: 'storeIdentifier', width: 220, search: false, ellipsis: true },
    { title: t('integrations.fields.source'), dataIndex: 'source', width: 110, search: false, renderText: (value) => t(`integrations.events.sources.${String(value ?? 'POLL')}`, { defaultValue: String(value ?? '-') }) },
    { title: t('integrations.events.fields.reason'), dataIndex: 'errorMessage', width: 360, search: false, ellipsis: true },
    { title: t('integrations.fields.status'), dataIndex: 'status', width: 135, search: false, render: (_, row) => <Tag color={getEventStatusColor(row.status)}>{t(`integrations.events.statuses.${row.status ?? 'RECEIVED'}`, { defaultValue: row.status ?? '-' })}</Tag> },
    { title: t('common.createdAt'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('integrations.events.fields.processedAt'), dataIndex: 'processedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: 92, fixed: 'right',
      render: (_, row) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelectedEventId(row.id)} size="small" type="link">{t('common.view')}</Button>],
    },
  ], [i18n.language, t]);

  return (
    <section className="data-page integration-page workflow-page">
      <Tabs
        activeKey={status}
        items={(['MANUAL_REVIEW', 'FAILED'] as const).map((value) => ({ key: value, label: t(`integrations.events.tabs.${value}`) }))}
        onChange={(value) => setStatus(value as ReviewStatus)}
      />
      <ProTable<RawEventSummary, EventTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('integrations.events.title')}
        onRow={(row) => ({ className: 'clickable-table-row', onDoubleClick: () => setSelectedEventId(row.id) })}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const page = toSpringPage(params);
            return toProTablePage(await listRawEvents({ channel, status, ...page }));
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1690 }}
        search={false}
        toolBarRender={() => [
          <Space key="channel">
            <span className="table-secondary">{t('integrations.fields.channel')}</span>
            <Select onChange={(value) => { setChannel(value); actionRef.current?.reload(); }} options={[{ label: 'Shopify', value: 'SHOPIFY' }]} value={channel} />
          </Space>,
        ]}
      />
      <RawEventDetailDrawer eventId={selectedEventId} onClose={() => setSelectedEventId(undefined)} />
    </section>
  );
}
