import {
  ApiOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  Descriptions,
  Modal,
  Popconfirm,
  Switch,
  Tag,
  Tooltip,
} from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { useAuth } from '../../auth/AuthProvider';
import {
  createIntegrationConfig,
  deleteIntegrationConfig,
  INTEGRATION_CONFIGS_QUERY_KEY,
  listIntegrationConfigs,
  setIntegrationConfigActive,
  testIntegrationConnection,
  updateIntegrationConfig,
  type ConnectionTestResult,
  type IntegrationConfig,
  type IntegrationConfigPayload,
} from '../../api/integrations';
import { paginateArray } from '../../api/pagination';
import { formatDateTime } from '../workflowUtils';
import { IntegrationConfigFormModal } from './IntegrationConfigFormModal';
import { ShopifySyncButton } from './ShopifySyncButton';

interface ConfigTableParams { current?: number; pageSize?: number; search?: string }

export function IntegrationConfigPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [formOpen, setFormOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<IntegrationConfig>();
  const [connectionResult, setConnectionResult] = useState<ConnectionTestResult>();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const canManageRetailMode = session?.currentRole === 'TENANT_ADMIN';

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id?: number; payload: IntegrationConfigPayload }) =>
      id === undefined ? createIntegrationConfig(payload) : updateIntegrationConfig(id, payload),
  });
  const activeMutation = useMutation({ mutationFn: ({ id, active }: { id: number; active: boolean }) => setIntegrationConfigActive(id, active) });
  const deleteMutation = useMutation({ mutationFn: deleteIntegrationConfig });
  const testMutation = useMutation({ mutationFn: testIntegrationConnection });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: INTEGRATION_CONFIGS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const save = async (payload: IntegrationConfigPayload): Promise<boolean> => {
    try {
      const permittedPayload = canManageRetailMode ? payload : { ...payload, retailMode: undefined };
      await saveMutation.mutateAsync({ id: editingConfig?.id, payload: permittedPayload });
      message.success(t(editingConfig ? 'integrations.config.messages.updated' : 'integrations.config.messages.created'));
      setFormOpen(false);
      setEditingConfig(undefined);
      await refresh();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const toggleActive = async (config: IntegrationConfig, active: boolean): Promise<void> => {
    if (config.id === undefined) return;
    try {
      await activeMutation.mutateAsync({ id: config.id, active });
      message.success(t(active ? 'integrations.config.messages.activated' : 'integrations.config.messages.deactivated'));
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const remove = async (config: IntegrationConfig): Promise<void> => {
    if (config.id === undefined) return;
    try {
      await deleteMutation.mutateAsync(config.id);
      message.success(t('integrations.config.messages.deleted'));
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const testConnection = async (config: IntegrationConfig): Promise<void> => {
    if (config.id === undefined) return;
    try {
      setConnectionResult(await testMutation.mutateAsync(config.id));
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const columns = useMemo<ProColumns<IntegrationConfig>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('integrations.config.searchPlaceholder') } },
    { title: t('integrations.config.fields.storeUrl'), dataIndex: 'storeUrl', width: 260, fixed: 'left', search: false, copyable: true },
    { title: t('integrations.config.fields.platform'), dataIndex: 'platform', width: 110, search: false, render: (_, row) => <Tag color="green">{row.platform ?? '-'}</Tag> },
    { title: t('integrations.config.fields.authMode'), dataIndex: 'authMode', width: 190, search: false, renderText: (value) => t(`integrations.config.authModes.${String(value ?? 'UNCONFIGURED')}`, { defaultValue: String(value ?? '-') }) },
    { title: t('integrations.config.fields.clientId'), dataIndex: 'clientId', width: 190, search: false, ellipsis: true },
    { title: t('integrations.config.fields.secret'), dataIndex: 'clientSecretMasked', width: 145, search: false, renderText: (_, row) => row.clientSecretMasked ?? (row.hasStaticToken ? t('integrations.config.staticTokenConfigured') : '-') },
    {
      title: t('integrations.config.fields.active'), dataIndex: 'isActive', width: 105, search: false,
      render: (_, row) => (
        <Switch
          checked={row.isActive === true}
          checkedChildren={t('common.enabled')}
          loading={activeMutation.isPending}
          onChange={(checked) => void toggleActive(row, checked)}
          unCheckedChildren={t('common.disabled')}
        />
      ),
    },
    ...(canManageRetailMode ? [{
      title: t('integrations.config.fields.retailMode'), dataIndex: 'retailMode', width: 150, search: false,
      render: (_: unknown, row: IntegrationConfig) => (
        <Tag color={row.retailMode ? 'processing' : 'default'}>
          {t(row.retailMode ? 'common.enabled' : 'common.disabled')}
        </Tag>
      ),
    } satisfies ProColumns<IntegrationConfig>] : []),
    { title: t('integrations.config.fields.lastSyncAt'), dataIndex: 'lastSyncAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: 160, fixed: 'right',
      render: (_, row) => [
        <Tooltip key="test" title={t('integrations.config.actions.test')}>
          <Button aria-label={t('integrations.config.actions.test')} icon={<ApiOutlined />} loading={testMutation.isPending} onClick={() => void testConnection(row)} size="small" type="text" />
        </Tooltip>,
        <Tooltip key="edit" title={t('common.edit')}>
          <Button aria-label={t('common.edit')} icon={<EditOutlined />} onClick={() => { setEditingConfig(row); setFormOpen(true); }} size="small" type="text" />
        </Tooltip>,
        <Popconfirm
          disabled={row.isActive === true}
          key="delete"
          onConfirm={() => void remove(row)}
          title={t('integrations.config.confirmDelete')}
        >
          <Tooltip title={row.isActive ? t('integrations.config.deactivateBeforeDelete') : t('common.delete')}>
            <Button aria-label={t('common.delete')} danger disabled={row.isActive === true} icon={<DeleteOutlined />} loading={deleteMutation.isPending} size="small" type="text" />
          </Tooltip>
        </Popconfirm>,
      ],
    },
  ], [activeMutation.isPending, canManageRetailMode, deleteMutation.isPending, i18n.language, t, testMutation.isPending]);

  return (
    <section className="data-page integration-page">
      <ProTable<IntegrationConfig, ConfigTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('integrations.config.title')}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const configs = await listIntegrationConfigs();
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword ? configs.filter((item) => [item.storeUrl, item.platform, item.clientId].some((value) => value?.toLowerCase().includes(keyword))) : configs;
            return paginateArray(filtered, params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1450 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <ShopifySyncButton key="sync" onComplete={() => void refresh()} />,
          <Button icon={<PlusOutlined />} key="create" onClick={() => { setEditingConfig(undefined); setFormOpen(true); }} type="primary">{t('integrations.config.actions.create')}</Button>,
        ]}
      />

      <IntegrationConfigFormModal canManageRetailMode={canManageRetailMode} config={editingConfig} loading={saveMutation.isPending} onClose={() => { setFormOpen(false); setEditingConfig(undefined); }} onSubmit={save} open={formOpen} />

      <Modal footer={null} onCancel={() => setConnectionResult(undefined)} open={connectionResult !== undefined} title={t('integrations.config.connection.title')}>
        <Descriptions
          column={1}
          items={[
            { key: 'connected', label: t('integrations.config.connection.status'), children: <Tag color="success">{t('integrations.config.connection.connected')}</Tag> },
            { key: 'shop', label: t('integrations.config.connection.shopName'), children: connectionResult?.shopName ?? '-' },
            { key: 'domain', label: t('integrations.config.connection.domain'), children: connectionResult?.domain ?? connectionResult?.storeUrl ?? '-' },
            { key: 'currency', label: t('integrations.config.connection.currency'), children: connectionResult?.currency ?? '-' },
            { key: 'timezone', label: t('integrations.config.connection.timezone'), children: connectionResult?.timezone ?? '-' },
            { key: 'plan', label: t('integrations.config.connection.plan'), children: connectionResult?.plan ?? '-' },
          ]}
          size="small"
        />
      </Modal>
    </section>
  );
}
