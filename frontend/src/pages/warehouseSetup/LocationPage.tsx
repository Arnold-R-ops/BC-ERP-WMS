import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Popconfirm, Select, Space, Tag, Tooltip } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { getErrorMessage } from '../../api/errors';
import { paginateArray } from '../../api/pagination';
import {
  createLocation,
  listLocations,
  listWarehouses,
  LOCATIONS_QUERY_KEY,
  setLocationEnabled,
  updateLocation,
  WAREHOUSES_QUERY_KEY,
  type Location,
  type Warehouse,
} from '../../api/warehouseSetup';
import { useAuth } from '../../auth/AuthProvider';
import { formatDateTime } from '../workflowUtils';
import { getWarehouseSetupCapabilities } from './capabilities';
import { LocationFormModal, type LocationFormValues } from './LocationFormModal';

interface LocationTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  enabled?: 'ALL' | 'ENABLED' | 'DISABLED';
  occupancy?: 'ALL' | 'EMPTY' | 'OCCUPIED';
  zone?: string;
}

export function LocationPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedWarehouseId = Number(searchParams.get('warehouseId')) || undefined;
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | undefined>(requestedWarehouseId);
  const [formOpen, setFormOpen] = useState(false);
  const [editingLocation, setEditingLocation] = useState<Location>();
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const capabilities = getWarehouseSetupCapabilities(session?.currentRole ?? '');

  const warehouseQuery = useQuery({ queryKey: WAREHOUSES_QUERY_KEY, queryFn: listWarehouses });
  const warehouses = warehouseQuery.data ?? [];
  const selectedWarehouse = warehouses.find((warehouse) => warehouse.id === selectedWarehouseId);

  useEffect(() => {
    if (selectedWarehouseId !== undefined || warehouses.length === 0) return;
    const firstWarehouse = warehouses.find((warehouse) => warehouse.isActive === true) ?? warehouses[0];
    if (firstWarehouse?.id === undefined) return;
    setSelectedWarehouseId(firstWarehouse.id);
    setSearchParams({ warehouseId: String(firstWarehouse.id) }, { replace: true });
  }, [selectedWarehouseId, setSearchParams, warehouses]);

  const saveMutation = useMutation({
    mutationFn: ({ id, values }: { id?: number; values: LocationFormValues }) => {
      if (id === undefined) return createLocation(values);
      return updateLocation(id, { posX: values.posX, posY: values.posY, remark: values.remark });
    },
  });
  const enabledMutation = useMutation({ mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => setLocationEnabled(id, enabled) });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: LOCATIONS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const save = async (values: LocationFormValues): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editingLocation?.id, values });
      message.success(t(editingLocation ? 'warehouseSetup.locations.messages.updated' : 'warehouseSetup.locations.messages.created'));
      setFormOpen(false);
      setEditingLocation(undefined);
      await refresh();
      return true;
    } catch (error) { message.error(getErrorMessage(error, t)); return false; }
  };

  const toggleEnabled = async (location: Location): Promise<void> => {
    if (location.id === undefined) return;
    const enabled = location.enabled !== true;
    try {
      await enabledMutation.mutateAsync({ id: location.id, enabled });
      message.success(t(enabled ? 'warehouseSetup.locations.messages.enabled' : 'warehouseSetup.locations.messages.disabled'));
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const zoneValueEnum = useMemo(() => ({
    ZONE_A: { text: t('warehouseSetup.locations.zones.ZONE_A') },
    ZONE_B: { text: t('warehouseSetup.locations.zones.ZONE_B') },
    ZONE_C: { text: t('warehouseSetup.locations.zones.ZONE_C') },
    ZONE_D: { text: t('warehouseSetup.locations.zones.ZONE_D') },
    ZONE_E: { text: t('warehouseSetup.locations.zones.ZONE_E') },
    ZONE_Q: { text: t('warehouseSetup.locations.zones.ZONE_Q') },
    ZONE_R: { text: t('warehouseSetup.locations.zones.ZONE_R') },
  }), [t]);

  const columns = useMemo<ProColumns<Location>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('warehouseSetup.locations.searchPlaceholder') } },
    {
      title: t('warehouseSetup.locations.fields.enabled'), dataIndex: 'enabled', hideInTable: true, initialValue: 'ALL', valueType: 'select',
      valueEnum: { ALL: { text: t('warehouseSetup.filters.all') }, ENABLED: { text: t('common.enabled') }, DISABLED: { text: t('common.disabled') } },
    },
    {
      title: t('warehouseSetup.locations.fields.status'), dataIndex: 'occupancy', hideInTable: true, initialValue: 'ALL', valueType: 'select',
      valueEnum: { ALL: { text: t('warehouseSetup.filters.all') }, EMPTY: { text: t('warehouseSetup.locations.status.EMPTY') }, OCCUPIED: { text: t('warehouseSetup.locations.status.OCCUPIED') } },
    },
    { title: t('warehouseSetup.locations.fields.zone'), dataIndex: 'zone', hideInTable: true, valueType: 'select', valueEnum: zoneValueEnum },
    { title: t('warehouseSetup.locations.fields.locationCode'), dataIndex: 'locationCode', width: 280, fixed: 'left', search: false, copyable: true },
    { title: t('warehouseSetup.locations.fields.zone'), dataIndex: 'zone', width: 120, search: false, valueEnum: zoneValueEnum },
    { title: t('warehouseSetup.locations.fields.shelfNumber'), dataIndex: 'shelfNumber', width: 110, search: false },
    { title: t('warehouseSetup.locations.fields.positionNumber'), dataIndex: 'positionNumber', width: 100, search: false },
    { title: t('warehouseSetup.locations.fields.posX'), dataIndex: 'posX', width: 80, align: 'right', search: false },
    { title: t('warehouseSetup.locations.fields.posY'), dataIndex: 'posY', width: 80, align: 'right', search: false },
    {
      title: t('warehouseSetup.locations.fields.status'), dataIndex: 'status', width: 105, search: false,
      render: (_, row) => <Tag color={row.status === 'OCCUPIED' ? 'processing' : 'default'}>{t(`warehouseSetup.locations.status.${row.status ?? 'EMPTY'}`)}</Tag>,
    },
    {
      title: t('warehouseSetup.locations.fields.enabled'), dataIndex: 'enabled', width: 95, search: false,
      render: (_, row) => <Tag color={row.enabled ? 'success' : 'default'}>{t(row.enabled ? 'common.enabled' : 'common.disabled')}</Tag>,
    },
    { title: t('common.remark'), dataIndex: 'remark', width: 240, search: false, ellipsis: true },
    { title: t('common.updatedAt'), dataIndex: 'updatedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: capabilities.canManage ? 120 : 0, fixed: 'right', hideInTable: !capabilities.canManage,
      render: (_, row) => [
        <Tooltip key="edit" title={t('common.edit')}><Button aria-label={t('common.edit')} icon={<EditOutlined />} onClick={() => { setEditingLocation(row); setFormOpen(true); }} size="small" type="text" /></Tooltip>,
        <Popconfirm
          description={row.enabled && row.status === 'OCCUPIED' ? t('warehouseSetup.locations.confirmDisableOccupiedDescription') : undefined}
          key="enabled"
          onConfirm={() => void toggleEnabled(row)}
          title={t(row.enabled ? 'warehouseSetup.locations.confirmDisable' : 'warehouseSetup.locations.confirmEnable')}
        >
          <Button danger={row.enabled === true} loading={enabledMutation.isPending} size="small" type="link">{t(row.enabled ? 'common.disabled' : 'common.enabled')}</Button>
        </Popconfirm>,
      ],
    },
  ], [capabilities.canManage, enabledMutation.isPending, i18n.language, t, zoneValueEnum]);

  const selectWarehouse = (warehouseId: number): void => {
    setSelectedWarehouseId(warehouseId);
    setSearchParams({ warehouseId: String(warehouseId) }, { replace: true });
    actionRef.current?.reloadAndRest?.();
  };

  return (
    <section className="data-page">
      <ProTable<Location, LocationTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={
          <Space>
            <span>{t('warehouseSetup.locations.title')}</span>
            {selectedWarehouse ? <Tag>{selectedWarehouse.code}</Tag> : null}
          </Space>
        }
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          if (selectedWarehouseId === undefined) return { data: [], success: true, total: 0 };
          try {
            const locations = await listLocations(selectedWarehouseId);
            const keyword = params.search?.trim().toLowerCase();
            const filtered = locations.filter((location) => {
              if (params.enabled === 'ENABLED' && location.enabled !== true) return false;
              if (params.enabled === 'DISABLED' && location.enabled === true) return false;
              if (params.occupancy && params.occupancy !== 'ALL' && location.status !== params.occupancy) return false;
              if (params.zone && location.zone !== params.zone) return false;
              return !keyword || [location.locationCode, location.shelfNumber, location.positionNumber, location.remark]
                .some((value) => value?.toLowerCase().includes(keyword));
            });
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey={(row) => String(row.id ?? row.locationCode)}
        scroll={{ x: 1660 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <Select
            key="warehouse"
            loading={warehouseQuery.isLoading}
            onChange={selectWarehouse}
            options={warehouses.map((warehouse: Warehouse) => ({ label: `${warehouse.code ?? '-'} · ${warehouse.name ?? '-'}`, value: warehouse.id }))}
            placeholder={t('warehouseSetup.locations.selectWarehouse')}
            style={{ minWidth: 260 }}
            value={selectedWarehouseId}
          />,
          ...(capabilities.canManage ? [
            <Tooltip key="create" title={selectedWarehouse?.isActive === false ? t('warehouseSetup.locations.inactiveWarehouseHint') : undefined}>
              <Button disabled={selectedWarehouseId === undefined || selectedWarehouse?.isActive === false} icon={<PlusOutlined />} onClick={() => { setEditingLocation(undefined); setFormOpen(true); }} type="primary">{t('warehouseSetup.locations.actions.create')}</Button>
            </Tooltip>,
          ] : []),
        ]}
      />
      <LocationFormModal
        defaultWarehouseId={selectedWarehouseId}
        loading={saveMutation.isPending}
        location={editingLocation}
        onClose={() => { setFormOpen(false); setEditingLocation(undefined); }}
        onSubmit={save}
        open={formOpen}
        warehouses={warehouses}
      />
    </section>
  );
}
