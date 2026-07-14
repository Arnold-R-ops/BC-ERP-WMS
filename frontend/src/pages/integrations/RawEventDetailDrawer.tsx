import { CopyOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Descriptions, Drawer, Space, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../../api/errors';
import { getRawEvent } from '../../api/integrations';
import { ChannelTag } from '../../components/ChannelTag';
import { formatDateTime } from '../workflowUtils';
import { formatRawPayload, getEventStatusColor } from './integrationUtils';

interface RawEventDetailDrawerProps {
  eventId?: number;
  onClose: () => void;
}

export function RawEventDetailDrawer({ eventId, onClose }: RawEventDetailDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const navigate = useNavigate();
  const eventQuery = useQuery({
    queryKey: ['channel-raw-event', eventId],
    queryFn: () => getRawEvent(eventId as number),
    enabled: eventId !== undefined,
  });
  const event = eventQuery.data;
  const prettyPayload = formatRawPayload(event?.payload);

  const copyPayload = async (): Promise<void> => {
    try {
      await navigator.clipboard.writeText(prettyPayload);
      message.success(t('common.copied'));
    } catch {
      message.error(t('integrations.events.copyFailed'));
    }
  };

  return (
    <Drawer
      destroyOnHidden
      extra={event ? (
        <Space wrap>
          <Button icon={<CopyOutlined />} onClick={() => void copyPayload()}>{t('integrations.events.actions.copyPayload')}</Button>
          <Button icon={<ShoppingCartOutlined />} onClick={() => navigate('/sales')} type="primary">{t('integrations.events.actions.openSales')}</Button>
        </Space>
      ) : null}
      loading={eventQuery.isLoading}
      onClose={onClose}
      open={eventId !== undefined}
      title={t('integrations.events.details.title', { id: eventId ?? '-' })}
      width="min(980px, 94vw)"
    >
      {eventQuery.error && <Alert description={getErrorMessage(eventQuery.error, t)} message={t('integrations.events.messages.loadFailed')} showIcon type="error" />}
      {event && (
        <>
          <Alert className="raw-event-guidance" description={t('integrations.events.details.manualNotice')} showIcon type={event.status === 'FAILED' ? 'error' : 'warning'} />
          <Descriptions
            className="workflow-descriptions"
            column={{ xs: 1, sm: 2, lg: 3 }}
            items={[
              { key: 'status', label: t('integrations.fields.status'), children: <Tag color={getEventStatusColor(event.status)}>{t(`integrations.events.statuses.${event.status ?? 'RECEIVED'}`, { defaultValue: event.status ?? '-' })}</Tag> },
              { key: 'channel', label: t('integrations.fields.channel'), children: <ChannelTag channel={event.channel} /> },
              { key: 'store', label: t('integrations.fields.store'), children: event.storeIdentifier ?? '-' },
              { key: 'type', label: t('integrations.events.fields.eventType'), children: t(`integrations.events.types.${event.eventType ?? 'ORDER'}`, { defaultValue: event.eventType ?? '-' }) },
              { key: 'source', label: t('integrations.fields.source'), children: t(`integrations.events.sources.${event.source ?? 'POLL'}`, { defaultValue: event.source ?? '-' }) },
              { key: 'external', label: t('integrations.events.fields.externalId'), children: event.externalId ?? '-' },
              { key: 'webhook', label: t('integrations.events.fields.webhookId'), children: event.webhookEventId ?? '-' },
              { key: 'created', label: t('common.createdAt'), children: formatDateTime(event.createdAt, i18n.language) },
              { key: 'processed', label: t('integrations.events.fields.processedAt'), children: formatDateTime(event.processedAt, i18n.language) },
              { key: 'error', label: t('integrations.events.fields.reason'), children: event.errorMessage ?? '-' },
            ]}
            size="small"
          />
          <div className="raw-event-payload-heading">
            <strong>{t('integrations.events.fields.payload')}</strong>
            <span className="table-secondary">JSON</span>
          </div>
          <pre className="raw-event-payload">{prettyPayload}</pre>
        </>
      )}
    </Drawer>
  );
}
