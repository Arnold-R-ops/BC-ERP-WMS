import { EyeOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { App as AntdApp, Button, Descriptions, Drawer, Tag } from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { listCustomers, type Customer } from '../../api/masterData';
import { paginateArray } from '../../api/pagination';
import { formatDateTime } from '../workflowUtils';

interface ConsumerTableParams { current?: number; pageSize?: number; search?: string }

export function ConsumerPage(): JSX.Element {
  const [selected, setSelected] = useState<Customer>();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();

  const columns = useMemo<ProColumns<Customer>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('consumers.searchPlaceholder') } },
    { title: t('consumers.fields.name'), dataIndex: 'name', width: 190, fixed: 'left', search: false },
    { title: t('consumers.fields.email'), dataIndex: 'email', width: 230, search: false, ellipsis: true },
    { title: t('consumers.fields.code'), dataIndex: 'code', width: 160, search: false, copyable: true },
    { title: t('consumers.fields.source'), dataIndex: 'source', width: 130, search: false, render: (_, row) => <Tag color="cyan">{t(`consumers.sources.${row.source ?? 'CHANNEL'}`)}</Tag> },
    { title: t('consumers.fields.externalId'), dataIndex: 'externalCustomerId', width: 190, search: false, copyable: true, renderText: (value) => value ?? '-' },
    { title: t('consumers.fields.status'), dataIndex: 'isActive', width: 110, search: false, render: (_, row) => <Tag color={row.isActive ? 'success' : 'default'}>{t(row.isActive ? 'common.enabled' : 'common.disabled')}</Tag> },
    { title: t('consumers.fields.firstSeen'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('consumers.fields.lastSeen'), dataIndex: 'updatedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', width: 90, fixed: 'right', render: (_, row) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelected(row)} size="small" type="link">{t('common.view')}</Button>] },
  ], [i18n.language, t]);

  return (
    <section className="data-page">
      <ProTable<Customer, ConsumerTableParams>
        columns={columns}
        headerTitle={t('consumers.title')}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const consumers = await listCustomers(false, 'CONSUMER');
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword
              ? consumers.filter((consumer) => [consumer.name, consumer.email, consumer.code, consumer.externalCustomerId]
                .some((value) => value?.toLowerCase().includes(keyword)))
              : consumers;
            return paginateArray(filtered, params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1500 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
      />

      <Drawer destroyOnHidden onClose={() => setSelected(undefined)} open={selected !== undefined} title={t('consumers.detailsTitle')} width="min(680px, 94vw)">
        <Descriptions
          column={1}
          items={[
            { key: 'name', label: t('consumers.fields.name'), children: selected?.name ?? '-' },
            { key: 'email', label: t('consumers.fields.email'), children: selected?.email ?? '-' },
            { key: 'code', label: t('consumers.fields.code'), children: selected?.code ?? '-' },
            { key: 'source', label: t('consumers.fields.source'), children: t(`consumers.sources.${selected?.source ?? 'CHANNEL'}`) },
            { key: 'externalId', label: t('consumers.fields.externalId'), children: selected?.externalCustomerId ?? '-' },
            { key: 'created', label: t('consumers.fields.firstSeen'), children: formatDateTime(selected?.createdAt, i18n.language) },
            { key: 'updated', label: t('consumers.fields.lastSeen'), children: formatDateTime(selected?.updatedAt, i18n.language) },
          ]}
          size="small"
        />
      </Drawer>
    </section>
  );
}
