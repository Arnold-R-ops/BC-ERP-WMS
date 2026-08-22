import { Alert, Descriptions, Drawer, Empty, Table, Tag, type TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { getInventoryDetails, type InventoryDetail, type InventorySummary } from '../../api/inventory';
import { getDaysUntilExpiry, getFreshnessStatus } from './inventoryUtils';

interface InventoryDetailsDrawerProps {
  open: boolean;
  summary?: InventorySummary;
  nearExpiryDays?: number;
  onClose: () => void;
}

export function InventoryDetailsDrawer({
  open,
  summary,
  nearExpiryDays = 90,
  onClose,
}: InventoryDetailsDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const productSkuId = summary?.productSkuId;
  const detailsQuery = useQuery({
    queryKey: ['inventory', 'details', productSkuId],
    queryFn: () => getInventoryDetails(productSkuId as number),
    enabled: open && productSkuId !== undefined,
  });
  const details = detailsQuery.data ?? [];
  const emptyMessage = i18n.language === 'zh-CN'
    ? '当前商品库存为 0，暂无批次库存记录'
    : 'This item has zero inventory and no batch records';

  const formatDate = (value?: string): string =>
    value
      ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' }).format(
          new Date(`${value}T00:00:00`),
        )
      : '-';

  const columns: TableColumnsType<InventoryDetail> = [
    {
      title: t('inventory.fields.batchCode'),
      dataIndex: 'batchCode',
      width: 250,
      fixed: 'left',
    },
    {
      title: t('inventory.fields.warehouse'),
      dataIndex: 'warehouseName',
      width: 190,
    },
    {
      title: t('inventory.fields.locationCode'),
      dataIndex: 'locationCode',
      width: 250,
    },
    {
      title: t('inventory.fields.quantity'),
      dataIndex: 'quantity',
      align: 'right',
      width: 100,
    },
    {
      title: t('inventory.fields.reserved'),
      dataIndex: 'reservedQuantity',
      align: 'right',
      width: 100,
    },
    {
      title: t('inventory.fields.available'),
      dataIndex: 'availableQuantity',
      align: 'right',
      width: 100,
      render: (value: number | undefined) => (
        <strong className={(value ?? 0) <= 0 ? 'quantity-danger' : 'quantity-ok'}>
          {value ?? 0}
        </strong>
      ),
    },
    {
      title: t('inventory.fields.packageStatus'),
      dataIndex: 'packageStatus',
      width: 130,
    },
    {
      title: t('inventory.fields.expiryDate'),
      dataIndex: 'expiryDate',
      width: 180,
      fixed: 'right',
      render: (value: string | undefined) => {
        const status = getFreshnessStatus(value, nearExpiryDays);
        const days = getDaysUntilExpiry(value);
        const color = status === 'expired' ? 'error' : status === 'nearExpiry' ? 'warning' : 'success';
        return (
          <span>
            {formatDate(value)}{' '}
            {status !== 'unknown' && (
              <Tag color={color}>
                {t(`inventory.freshness.${status}`, { days: Math.abs(days ?? 0) })}
              </Tag>
            )}
          </span>
        );
      },
    },
  ];

  return (
    <Drawer
      destroyOnHidden
      onClose={onClose}
      open={open}
      title={t('inventory.detailsTitle', {
        name: summary?.skuInfo?.name ?? summary?.skuInfo?.skuCode ?? '-',
      })}
      width="min(1120px, 92vw)"
    >
      <Descriptions
        className="inventory-descriptions"
        column={{ xs: 1, sm: 2, md: 3 }}
        items={[
          {
            key: 'sku',
            label: t('inventory.fields.skuCode'),
            children: summary?.skuInfo?.skuCode ?? '-',
          },
          {
            key: 'total',
            label: t('inventory.fields.onHand'),
            children: i18n.language === 'zh-CN'
              ? summary?.displayQuantity ?? summary?.totalQuantity ?? 0
              : t('common.unitsValue', { count: summary?.totalQuantity ?? 0 }),
          },
          {
            key: 'available',
            label: t('inventory.fields.available'),
            children: i18n.language === 'zh-CN'
              ? summary?.displayAvailableQuantity ?? summary?.availableQuantity ?? 0
              : t('common.unitsValue', { count: summary?.availableQuantity ?? 0 }),
          },
        ]}
        size="small"
      />

      {detailsQuery.error && (
        <Alert
          description={getErrorMessage(detailsQuery.error, t)}
          message={t('inventory.messages.detailsFailed')}
          showIcon
          type="error"
        />
      )}

      {!detailsQuery.isLoading && !detailsQuery.error && details.length === 0 && (
        <Alert
          className="inventory-empty-alert"
          message={emptyMessage}
          showIcon
          type="info"
        />
      )}

      <Table<InventoryDetail>
        columns={columns}
        dataSource={details}
        loading={detailsQuery.isLoading}
        locale={{
          emptyText: <Empty description={emptyMessage} image={Empty.PRESENTED_IMAGE_SIMPLE} />,
        }}
        pagination={false}
        rowClassName={(record) => {
          const status = getFreshnessStatus(record.expiryDate, nearExpiryDays);
          return status === 'expired'
            ? 'inventory-row-expired'
            : status === 'nearExpiry'
              ? 'inventory-row-near-expiry'
              : '';
        }}
        rowKey={(record) => `${record.batchCode ?? 'batch'}-${record.locationCode ?? 'location'}`}
        scroll={{ x: 1250 }}
        size="small"
      />
    </Drawer>
  );
}
