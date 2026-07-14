import { Alert, Descriptions, Drawer, Table, type TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { getLocationInventory, type InventoryDetail } from '../../api/inventory';

interface LocationInventoryDrawerProps {
  locationCode?: string;
  open: boolean;
  onClose: () => void;
}

export function LocationInventoryDrawer({
  locationCode,
  open,
  onClose,
}: LocationInventoryDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const locationQuery = useQuery({
    queryKey: ['inventory', 'location', locationCode],
    queryFn: () => getLocationInventory(locationCode as string),
    enabled: open && Boolean(locationCode),
    retry: false,
  });

  const columns: TableColumnsType<InventoryDetail> = [
    {
      title: t('inventory.fields.batchCode'),
      dataIndex: 'batchCode',
      width: 260,
    },
    {
      title: t('inventory.fields.traceCode'),
      dataIndex: 'traceCode',
      width: 260,
    },
    {
      title: t('inventory.fields.quantity'),
      dataIndex: 'quantity',
      align: 'right',
      width: 110,
    },
    {
      title: t('inventory.fields.reserved'),
      dataIndex: 'reservedQuantity',
      align: 'right',
      width: 110,
    },
    {
      title: t('inventory.fields.available'),
      dataIndex: 'availableQuantity',
      align: 'right',
      width: 110,
    },
    {
      title: t('inventory.fields.packageStatus'),
      dataIndex: 'packageStatus',
      width: 140,
    },
    {
      title: t('inventory.fields.expiryDate'),
      dataIndex: 'expiryDate',
      width: 150,
      render: (value: string | undefined) =>
        value
          ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' }).format(
              new Date(`${value}T00:00:00`),
            )
          : '-',
    },
  ];

  return (
    <Drawer
      destroyOnHidden
      onClose={onClose}
      open={open}
      title={t('inventory.locationTitle', { code: locationCode ?? '-' })}
      width="min(1000px, 92vw)"
    >
      {locationQuery.error && (
        <Alert
          description={getErrorMessage(locationQuery.error, t)}
          message={t('inventory.messages.locationFailed')}
          showIcon
          type="error"
        />
      )}

      {locationQuery.data && (
        <Descriptions
          className="inventory-descriptions"
          column={{ xs: 1, sm: 2 }}
          items={[
            {
              key: 'warehouse',
              label: t('inventory.fields.warehouse'),
              children: locationQuery.data.warehouseName ?? '-',
            },
            {
              key: 'location',
              label: t('inventory.fields.locationCode'),
              children: locationQuery.data.locationCode ?? '-',
            },
          ]}
          size="small"
        />
      )}

      <Table<InventoryDetail>
        columns={columns}
        dataSource={locationQuery.data?.batches ?? []}
        loading={locationQuery.isLoading}
        locale={{ emptyText: t('inventory.locationEmpty') }}
        pagination={false}
        rowKey={(record) => `${record.batchCode ?? 'batch'}-${record.traceCode ?? 'trace'}`}
        scroll={{ x: 1050 }}
        size="small"
      />
    </Drawer>
  );
}
