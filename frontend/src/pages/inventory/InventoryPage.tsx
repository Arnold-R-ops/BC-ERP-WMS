import { BarcodeOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { App as AntdApp, Button, Input, Space, Tag, Tooltip } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { getErrorMessage } from '../../api/errors';
import {
  getInventorySummary,
  type InventorySummary,
} from '../../api/inventory';
import { toProTablePage, toSpringPage } from '../../api/pagination';
import { listProducts, PRODUCTS_QUERY_KEY } from '../../api/products';
import { InventoryDetailsDrawer } from './InventoryDetailsDrawer';
import { getFreshnessStatus } from './inventoryUtils';
import { LocationInventoryDrawer } from './LocationInventoryDrawer';

interface InventoryTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
}

export function InventoryPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [searchParams] = useSearchParams();
  const initialSearch = searchParams.get('search')?.trim() ?? '';
  const [selectedSummary, setSelectedSummary] = useState<InventorySummary>();
  const [locationCode, setLocationCode] = useState<string>();
  const [locationDrawerOpen, setLocationDrawerOpen] = useState(false);
  const { message } = AntdApp.useApp();
  const { i18n, t } = useTranslation();

  const productsQuery = useQuery({
    queryKey: PRODUCTS_QUERY_KEY,
    queryFn: () => listProducts(),
  });

  const nearExpiryThresholds = useMemo(
    () => new Map(
      (productsQuery.data ?? [])
        .filter((product) => product.id !== undefined)
        .map((product) => [product.id as number, product.nearExpiryDays ?? 90]),
    ),
    [productsQuery.data],
  );

  const formatDate = (value?: string): string =>
    value
      ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' }).format(
          new Date(`${value}T00:00:00`),
        )
      : '-';

  const columns = useMemo<ProColumns<InventorySummary>[]>(
    () => [
      {
        title: t('common.search'),
        dataIndex: 'search',
        hideInTable: true,
        fieldProps: { placeholder: t('inventory.searchPlaceholder') },
      },
      {
        title: t('inventory.fields.product'),
        dataIndex: ['skuInfo', 'name'],
        fixed: 'left',
        search: false,
        width: 250,
        render: (_, record) => (
          <Space direction="vertical" size={0}>
            <strong>{record.skuInfo?.name ?? '-'}</strong>
            <span className="table-secondary">{record.skuInfo?.skuCode ?? '-'}</span>
          </Space>
        ),
      },
      {
        title: t('inventory.fields.specs'),
        dataIndex: ['skuInfo', 'specs'],
        search: false,
        width: 180,
      },
      {
        title: t('inventory.fields.warehouse'),
        dataIndex: 'warehouseNames',
        search: false,
        width: 220,
        render: (_, record) => (
          <Space size={[4, 4]} wrap>
            {(record.warehouseNames ?? []).length > 0
              ? record.warehouseNames?.map((name) => <Tag key={name}>{name}</Tag>)
              : '-'}
          </Space>
        ),
      },
      {
        title: t('inventory.fields.onHand'),
        dataIndex: 'totalQuantity',
        search: false,
        align: 'right',
        width: 135,
        render: (_, record) => i18n.language === 'zh-CN'
          ? record.displayQuantity ?? record.totalQuantity ?? 0
          : t('common.unitsValue', { count: record.totalQuantity ?? 0 }),
      },
      {
        title: t('inventory.fields.reserved'),
        dataIndex: 'reservedQuantity',
        search: false,
        align: 'right',
        width: 105,
      },
      {
        title: t('inventory.fields.available'),
        dataIndex: 'availableQuantity',
        search: false,
        align: 'right',
        width: 145,
        render: (_, record) => (
          <strong className={(record.availableQuantity ?? 0) <= 0 ? 'quantity-danger' : 'quantity-ok'}>
            {i18n.language === 'zh-CN'
              ? record.displayAvailableQuantity ?? record.availableQuantity ?? 0
              : t('common.unitsValue', { count: record.availableQuantity ?? 0 })}
          </strong>
        ),
      },
      {
        title: t('inventory.fields.stockStatus'),
        dataIndex: 'stockStatus',
        search: false,
        width: 120,
        render: (_, record) => (
          <Tag color={record.stockStatus === 'LOW_STOCK' ? 'warning' : 'success'}>
            {t(`inventory.stockStatus.${record.stockStatus ?? 'LOW_STOCK'}`)}
          </Tag>
        ),
      },
      {
        title: t('inventory.fields.furthestExpiryDate'),
        dataIndex: 'furthestExpiryDate',
        search: false,
        width: 180,
        render: (_, record) => {
          const expiryDate = record.furthestExpiryDate;
          const threshold = nearExpiryThresholds.get(record.productId ?? -1) ?? 90;
          const status = getFreshnessStatus(expiryDate, threshold);
          return (
            <Space size={4}>
              <span>{formatDate(expiryDate)}</span>
              {(status === 'expired' || status === 'nearExpiry') && (
                <Tooltip title={t('inventory.furthestExpiryWarning')}>
                  <Tag color={status === 'expired' ? 'error' : 'warning'}>
                    {t(`inventory.freshness.${status}`, { days: 0 })}
                  </Tag>
                </Tooltip>
              )}
            </Space>
          );
        },
      },
      {
        title: t('common.actions'),
        valueType: 'option',
        fixed: 'right',
        width: 110,
        render: (_, record) => [
          <Button
            icon={<EyeOutlined />}
            key="details"
            onClick={() => setSelectedSummary(record)}
            size="small"
            type="link"
          >
            {t('inventory.viewBatches')}
          </Button>,
        ],
      },
    ],
    [i18n.language, nearExpiryThresholds, t],
  );

  const searchLocation = (value: string): void => {
    const code = value.trim();
    if (!code) {
      message.warning(t('inventory.validation.locationRequired'));
      return;
    }
    setLocationCode(code);
    setLocationDrawerOpen(true);
  };

  return (
    <section className="data-page">
      <ProTable<InventorySummary, InventoryTableParams>
        actionRef={actionRef}
        columns={columns}
        form={{ initialValues: initialSearch ? { search: initialSearch } : undefined, syncToUrl: false }}
        headerTitle={t('inventory.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const page = await getInventorySummary({
              ...toSpringPage(params),
              search: params.search,
            });
            return toProTablePage(page);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="productId"
        scroll={{ x: 1550 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <Input.Search
            allowClear
            aria-label={t('inventory.locationSearchLabel')}
            className="location-search"
            enterButton={<SearchOutlined />}
            key="location-search"
            onSearch={searchLocation}
            placeholder={t('inventory.locationSearchPlaceholder')}
            prefix={<BarcodeOutlined />}
          />,
        ]}
      />

      <InventoryDetailsDrawer
        nearExpiryDays={nearExpiryThresholds.get(selectedSummary?.productId ?? -1)}
        onClose={() => setSelectedSummary(undefined)}
        open={selectedSummary !== undefined}
        summary={selectedSummary}
      />

      <LocationInventoryDrawer
        locationCode={locationCode}
        onClose={() => setLocationDrawerOpen(false)}
        open={locationDrawerOpen}
      />
    </section>
  );
}
