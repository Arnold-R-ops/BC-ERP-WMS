import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Space, Tag } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { paginateArray } from '../../api/pagination';
import {
  createProduct,
  listProducts,
  PRODUCTS_QUERY_KEY,
  updateProduct,
  type Product,
  type ProductPayload,
} from '../../api/products';
import { ProductFormDrawer } from './ProductFormDrawer';
import { filterProducts, getSpuOptions, type SpuOption } from './productUtils';

interface ProductTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  enabledStatus?: 'enabled' | 'disabled';
}

interface SaveProductVariables {
  id?: number;
  payload: ProductPayload;
}

export function ProductPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product>();
  const [spuOptions, setSpuOptions] = useState<SpuOption[]>([]);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const { i18n, t } = useTranslation();

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: SaveProductVariables) =>
      id === undefined ? createProduct(payload) : updateProduct(id, payload),
  });

  const closeDrawer = (): void => {
    setDrawerOpen(false);
    setEditingProduct(undefined);
  };

  const openCreateDrawer = (): void => {
    setEditingProduct(undefined);
    setDrawerOpen(true);
  };

  const openEditDrawer = (product: Product): void => {
    setEditingProduct(product);
    setDrawerOpen(true);
  };

  const saveProduct = async (payload: ProductPayload): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editingProduct?.id, payload });
      message.success(
        editingProduct ? t('products.messages.updated') : t('products.messages.created'),
      );
      await queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY });
      actionRef.current?.reload();
      closeDrawer();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const formatMoney = (value?: number): string =>
    new Intl.NumberFormat(i18n.language, {
      style: 'currency',
      currency: 'GBP',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value ?? 0);

  const formatDateTime = (value?: string): string =>
    value ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-';

  const columns = useMemo<ProColumns<Product>[]>(
    () => [
      {
        title: t('common.search'),
        dataIndex: 'search',
        hideInTable: true,
        fieldProps: { placeholder: t('products.searchPlaceholder') },
      },
      {
        title: t('products.fields.enabled'),
        dataIndex: 'enabledStatus',
        hideInTable: true,
        valueType: 'select',
        valueEnum: {
          enabled: { text: t('common.enabled') },
          disabled: { text: t('common.disabled') },
        },
      },
      {
        title: t('products.fields.name'),
        dataIndex: 'name',
        width: 220,
        search: false,
        fixed: 'left',
        render: (_, record) => (
          <Space direction="vertical" size={0}>
            <strong>{record.name ?? '-'}</strong>
            <span className="table-secondary">{record.skuName ?? '-'}</span>
          </Space>
        ),
      },
      {
        title: t('products.fields.barcode'),
        dataIndex: 'barcode',
        width: 190,
        search: false,
        copyable: true,
      },
      {
        title: t('products.fields.spu'),
        dataIndex: 'spuName',
        width: 180,
        search: false,
        renderText: (_, record) => record.spuName ?? `#${record.spuId ?? '-'}`,
      },
      {
        title: t('products.fields.unitPrice'),
        dataIndex: 'unitPrice',
        width: 120,
        search: false,
        align: 'right',
        renderText: (value) => formatMoney(value as number | undefined),
      },
      {
        title: t('products.fields.minSalesPrice'),
        dataIndex: 'minSalesPrice',
        width: 130,
        search: false,
        align: 'right',
        renderText: (value) => formatMoney(value as number | undefined),
      },
      {
        title: t('products.fields.packaging'),
        dataIndex: 'conversionRate',
        width: 170,
        search: false,
        renderText: (_, record) =>
          t('products.packagingValue', {
            quantity: record.conversionRate ?? record.perPackQty ?? 1,
            unit: record.packUnit ?? t('common.unit'),
          }),
      },
      {
        title: t('products.fields.nearExpiryDays'),
        dataIndex: 'nearExpiryDays',
        width: 120,
        search: false,
        renderText: (value) => t('common.daysValue', { count: value ?? 0 }),
      },
      {
        title: t('products.fields.batchTrackingMode'),
        dataIndex: 'batchTrackingMode',
        width: 150,
        search: false,
        render: (_, record) => (
          <Tag color={record.batchTrackingMode === 'LOCATION_VISUAL' ? 'gold' : 'blue'}>
            {t(`products.batchTracking.${record.batchTrackingMode ?? 'PRINTED_LABEL'}`)}
          </Tag>
        ),
      },
      {
        title: t('products.fields.enabled'),
        dataIndex: 'enabled',
        width: 90,
        search: false,
        render: (_, record) => (
          <Tag color={record.enabled ? 'success' : 'default'}>
            {record.enabled ? t('common.enabled') : t('common.disabled')}
          </Tag>
        ),
      },
      {
        title: t('common.updatedAt'),
        dataIndex: 'updatedAt',
        width: 180,
        search: false,
        renderText: (value) => formatDateTime(value as string | undefined),
      },
      {
        title: t('common.actions'),
        valueType: 'option',
        width: 90,
        fixed: 'right',
        render: (_, record) => [
          <Button
            icon={<EditOutlined />}
            key="edit"
            onClick={() => openEditDrawer(record)}
            size="small"
            type="link"
          >
            {t('common.edit')}
          </Button>,
        ],
      },
    ],
    [i18n.language, t],
  );

  return (
    <section className="data-page">
      <ProTable<Product, ProductTableParams>
        actionRef={actionRef}
        columns={columns}
        form={{ syncToUrl: false }}
        headerTitle={t('products.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const products = await queryClient.fetchQuery({
              queryKey: PRODUCTS_QUERY_KEY,
              queryFn: () => listProducts(),
            });
            setSpuOptions(getSpuOptions(products));
            const enabled = params.enabledStatus === 'enabled'
              ? true
              : params.enabledStatus === 'disabled'
                ? false
                : undefined;
            return paginateArray(
              filterProducts(products, { search: params.search, enabled }),
              params,
            );
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1650 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button icon={<PlusOutlined />} key="create" onClick={openCreateDrawer} type="primary">
            {t('products.create')}
          </Button>,
        ]}
      />

      <ProductFormDrawer
        onClose={closeDrawer}
        onSubmit={saveProduct}
        open={drawerOpen}
        product={editingProduct}
        spuOptions={spuOptions}
        submitting={saveMutation.isPending}
      />
    </section>
  );
}
