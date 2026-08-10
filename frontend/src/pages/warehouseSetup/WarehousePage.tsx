import { EditOutlined, EnvironmentOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Popconfirm, Space, Tag, Tooltip } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../../api/errors';
import { paginateArray } from '../../api/pagination';
import {
  createWarehouse,
  listWarehouses,
  setWarehouseActive,
  updateWarehouse,
  WAREHOUSES_QUERY_KEY,
  type Warehouse,
} from '../../api/warehouseSetup';
import { useAuth } from '../../auth/AuthProvider';
import { formatDateTime } from '../workflowUtils';
import { getWarehouseSetupCapabilities } from './capabilities';
import { WarehouseFormModal, type WarehouseFormValues } from './WarehouseFormModal';

interface WarehouseTableParams { current?: number; pageSize?: number; search?: string; active?: 'ALL' | 'ACTIVE' | 'INACTIVE' }

export function WarehousePage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [formOpen, setFormOpen] = useState(false);
  const [editingWarehouse, setEditingWarehouse] = useState<Warehouse>();
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const capabilities = getWarehouseSetupCapabilities(session?.currentRole ?? '', session?.permissionCodes);

  const saveMutation = useMutation({
    mutationFn: ({ id, values }: { id?: number; values: WarehouseFormValues }) => {
      if (id === undefined) return createWarehouse(values);
      const { code: _code, ...payload } = values;
      return updateWarehouse(id, payload);
    },
  });
  const activeMutation = useMutation({ mutationFn: ({ id, active }: { id: number; active: boolean }) => setWarehouseActive(id, active) });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: WAREHOUSES_QUERY_KEY });
    actionRef.current?.reload();
  };

  const save = async (values: WarehouseFormValues): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editingWarehouse?.id, values });
      message.success(t(editingWarehouse ? 'warehouseSetup.warehouses.messages.updated' : 'warehouseSetup.warehouses.messages.created'));
      setFormOpen(false);
      setEditingWarehouse(undefined);
      await refresh();
      return true;
    } catch (error) { message.error(getErrorMessage(error, t)); return false; }
  };

  const toggleActive = async (warehouse: Warehouse): Promise<void> => {
    if (warehouse.id === undefined) return;
    const active = warehouse.isActive !== true;
    try {
      await activeMutation.mutateAsync({ id: warehouse.id, active });
      message.success(t(active ? 'warehouseSetup.warehouses.messages.activated' : 'warehouseSetup.warehouses.messages.deactivated'));
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const columns = useMemo<ProColumns<Warehouse>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('warehouseSetup.warehouses.searchPlaceholder') } },
    {
      title: t('warehouseSetup.warehouses.fields.active'), dataIndex: 'active', hideInTable: true, initialValue: 'ALL', valueType: 'select',
      valueEnum: { ALL: { text: t('warehouseSetup.filters.all') }, ACTIVE: { text: t('common.enabled') }, INACTIVE: { text: t('common.disabled') } },
    },
    { title: t('warehouseSetup.warehouses.fields.code'), dataIndex: 'code', width: 160, fixed: 'left', search: false, copyable: true },
    { title: t('warehouseSetup.warehouses.fields.name'), dataIndex: 'name', width: 200, search: false },
    { title: t('warehouseSetup.warehouses.fields.address'), dataIndex: 'address', width: 260, search: false, ellipsis: true },
    { title: t('warehouseSetup.warehouses.fields.contact'), dataIndex: 'contact', width: 130, search: false },
    { title: t('warehouseSetup.warehouses.fields.phone'), dataIndex: 'phone', width: 150, search: false },
    { title: t('warehouseSetup.warehouses.fields.locationCount'), dataIndex: 'locationCount', width: 110, search: false, align: 'right' },
    {
      title: t('warehouseSetup.warehouses.fields.active'), dataIndex: 'isActive', width: 100, search: false,
      render: (_, row) => <Tag color={row.isActive ? 'success' : 'default'}>{t(row.isActive ? 'common.enabled' : 'common.disabled')}</Tag>,
    },
    { title: t('common.updatedAt'), dataIndex: 'updatedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: capabilities.canManage ? 155 : 90, fixed: 'right',
      render: (_, row) => {
        const items = [
          <Tooltip key="locations" title={t('warehouseSetup.warehouses.actions.locations')}>
            <Button aria-label={t('warehouseSetup.warehouses.actions.locations')} icon={<EnvironmentOutlined />} onClick={() => navigate(`/warehouse-setup/locations?warehouseId=${row.id ?? ''}`)} size="small" type="text" />
          </Tooltip>,
        ];
        if (capabilities.canManage) {
          items.push(
            <Tooltip key="edit" title={t('common.edit')}><Button aria-label={t('common.edit')} icon={<EditOutlined />} onClick={() => { setEditingWarehouse(row); setFormOpen(true); }} size="small" type="text" /></Tooltip>,
            <Popconfirm
              description={row.isActive ? t('warehouseSetup.warehouses.confirmDeactivateDescription') : undefined}
              key="active"
              onConfirm={() => void toggleActive(row)}
              title={t(row.isActive ? 'warehouseSetup.warehouses.confirmDeactivate' : 'warehouseSetup.warehouses.confirmActivate')}
            >
              <Button danger={row.isActive === true} loading={activeMutation.isPending} size="small" type="link">{t(row.isActive ? 'common.disabled' : 'common.enabled')}</Button>
            </Popconfirm>,
          );
        }
        return <Space size={2}>{items}</Space>;
      },
    },
  ], [activeMutation.isPending, capabilities.canManage, i18n.language, navigate, t]);

  return (
    <section className="data-page">
      <ProTable<Warehouse, WarehouseTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('warehouseSetup.warehouses.title')}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const warehouses = await listWarehouses();
            const keyword = params.search?.trim().toLowerCase();
            const filtered = warehouses.filter((warehouse) => {
              if (params.active === 'ACTIVE' && warehouse.isActive !== true) return false;
              if (params.active === 'INACTIVE' && warehouse.isActive === true) return false;
              return !keyword || [warehouse.code, warehouse.name, warehouse.address, warehouse.contact, warehouse.phone]
                .some((value) => value?.toLowerCase().includes(keyword));
            });
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey={(row) => String(row.id ?? row.code)}
        scroll={{ x: 1570 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => capabilities.canManage ? [
          <Button icon={<PlusOutlined />} key="create" onClick={() => { setEditingWarehouse(undefined); setFormOpen(true); }} type="primary">{t('warehouseSetup.warehouses.actions.create')}</Button>,
        ] : []}
      />
      <WarehouseFormModal
        loading={saveMutation.isPending}
        onClose={() => { setFormOpen(false); setEditingWarehouse(undefined); }}
        onSubmit={save}
        open={formOpen}
        warehouse={editingWarehouse}
      />
    </section>
  );
}
