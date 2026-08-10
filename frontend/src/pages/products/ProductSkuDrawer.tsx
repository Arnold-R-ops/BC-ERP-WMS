import { EditOutlined, PlusOutlined, PoweroffOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Drawer, Popconfirm, Space, Tag } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { getErrorMessage } from '../../api/errors';
import { paginateArray } from '../../api/pagination';
import type { Product } from '../../api/products';
import { PRODUCTS_QUERY_KEY } from '../../api/products';
import {
  createProductSku,
  listProductSkus,
  PRODUCT_SKUS_QUERY_KEY,
  setProductSkuEnabled,
  updateProductSku,
  type ProductSku,
  type ProductSkuPayload,
} from '../../api/productSkus';
import { ProductSkuFormModal } from './ProductSkuFormModal';

interface ProductSkuDrawerProps {
  open: boolean;
  product?: Product;
  onClose: () => void;
  permissionCodes?: string[];
  role?: string;
}

interface SkuTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  enabledStatus?: 'enabled' | 'disabled';
}

function filterSkus(skus: ProductSku[], params: SkuTableParams): ProductSku[] {
  const keyword = params.search?.trim().toLocaleLowerCase();
  const enabled = params.enabledStatus === 'enabled' ? true : params.enabledStatus === 'disabled' ? false : undefined;
  return skus.filter((sku) => {
    if (enabled !== undefined && sku.enabled !== enabled) return false;
    if (!keyword) return true;
    return [sku.skuCode, sku.barcode, sku.name, sku.skuName, sku.specification]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLocaleLowerCase().includes(keyword));
  });
}

export function ProductSkuDrawer({ open, permissionCodes, product, role, onClose }: ProductSkuDrawerProps): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [formOpen, setFormOpen] = useState(false);
  const [editingSku, setEditingSku] = useState<ProductSku>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const { i18n, t } = useTranslation();
  const productId = product?.id;
  const canCreate = hasPermission(role, permissionCodes, 'product-sku:create');
  const canEdit = hasPermission(role, permissionCodes, 'product-sku:edit');
  const canChangeStatus = hasPermission(role, permissionCodes, 'product-sku:status');

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id?: number; payload: ProductSkuPayload }) =>
      id === undefined ? createProductSku(payload) : updateProductSku(id, payload),
  });
  const statusMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => setProductSkuEnabled(id, enabled),
  });

  const reload = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: PRODUCT_SKUS_QUERY_KEY });
    await queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const saveSku = async (payload: ProductSkuPayload): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editingSku?.id, payload });
      message.success(editingSku ? t('productSkus.messages.updated') : t('productSkus.messages.created'));
      setFormOpen(false);
      setEditingSku(undefined);
      await reload();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const changeStatus = async (sku: ProductSku): Promise<void> => {
    if (sku.id === undefined) return;
    try {
      await statusMutation.mutateAsync({ id: sku.id, enabled: !sku.enabled });
      message.success(t('productSkus.messages.statusUpdated'));
      await reload();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const formatMoney = (value?: number): string => new Intl.NumberFormat(i18n.language, {
    style: 'currency', currency: 'GBP', minimumFractionDigits: 2,
  }).format(value ?? 0);

  const columns = useMemo<ProColumns<ProductSku>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('productSkus.searchPlaceholder') } },
    {
      title: t('productSkus.fields.enabled'), dataIndex: 'enabledStatus', hideInTable: true, valueType: 'select',
      valueEnum: { enabled: { text: t('common.enabled') }, disabled: { text: t('common.disabled') } },
    },
    { title: t('productSkus.fields.skuCode'), dataIndex: 'skuCode', width: 135, search: false, copyable: true, fixed: 'left' },
    {
      title: t('productSkus.fields.name'), dataIndex: 'name', width: 220, search: false,
      render: (_, record) => <Space direction="vertical" size={0}><strong>{record.name ?? '-'}</strong><span className="table-secondary">{record.skuName ?? '-'}</span></Space>,
    },
    { title: t('productSkus.fields.barcode'), dataIndex: 'barcode', width: 180, search: false, copyable: true },
    { title: t('productSkus.fields.specification'), dataIndex: 'specification', width: 140, search: false, renderText: (value, record) => value || record.specs || '-' },
    { title: t('productSkus.fields.unitPrice'), dataIndex: 'unitPrice', width: 110, search: false, align: 'right', renderText: (value) => formatMoney(value as number | undefined) },
    {
      title: t('productSkus.fields.packaging'), dataIndex: 'conversionRate', width: 150, search: false,
      renderText: (_, record) => t('productSkus.packagingValue', { quantity: record.conversionRate ?? 1, unit: record.packUnit ?? t('common.unit') }),
    },
    {
      title: t('productSkus.fields.batchTrackingMode'), dataIndex: 'batchTrackingMode', width: 145, search: false,
      render: (_, record) => <Tag color={record.batchTrackingMode === 'LOCATION_VISUAL' ? 'gold' : 'blue'}>{t(`productSkus.batchTracking.${record.batchTrackingMode ?? 'PRINTED_LABEL'}`)}</Tag>,
    },
    {
      title: t('productSkus.fields.enabled'), dataIndex: 'enabled', width: 80, search: false,
      render: (_, record) => <Tag color={record.enabled ? 'success' : 'default'}>{record.enabled ? t('common.enabled') : t('common.disabled')}</Tag>,
    },
    {
      title: t('common.actions'), valueType: 'option', width: canEdit || canChangeStatus ? 170 : 0, fixed: 'right', hideInTable: !canEdit && !canChangeStatus,
      render: (_, record) => [
        canEdit ? <Button icon={<EditOutlined />} key="edit" onClick={() => { setEditingSku(record); setFormOpen(true); }} size="small" type="link">{t('common.edit')}</Button> : null,
        canChangeStatus ? <Popconfirm key="status" onConfirm={() => void changeStatus(record)} title={record.enabled ? t('productSkus.confirmations.deactivate') : t('productSkus.confirmations.activate')}>
          <Button danger={Boolean(record.enabled)} icon={<PoweroffOutlined />} size="small" type="link">{record.enabled ? t('common.disabled') : t('common.enabled')}</Button>
        </Popconfirm> : null,
      ].filter(Boolean),
    },
  ], [canChangeStatus, canEdit, i18n.language, t]);

  return (
    <>
      <Drawer
        destroyOnHidden
        onClose={onClose}
        open={open}
        title={t('productSkus.drawerTitle', { code: product?.productCode ?? '-', name: product?.productName ?? '-' })}
        width="min(1180px, 94vw)"
      >
        <ProTable<ProductSku, SkuTableParams>
          actionRef={actionRef}
          columns={columns}
          options={{ density: true, reload: true, setting: true }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true }}
          request={async (params) => {
            if (productId === undefined) return { data: [], success: true, total: 0 };
            try {
              const skus = await queryClient.fetchQuery({
                queryKey: [...PRODUCT_SKUS_QUERY_KEY, productId],
                queryFn: () => listProductSkus({ productId }),
              });
              return paginateArray(filterSkus(skus, params), params);
            } catch (error) {
              message.error(getErrorMessage(error, t));
              return { data: [], success: false, total: 0 };
            }
          }}
          rowKey="id"
          scroll={{ x: 1450 }}
          search={{ defaultCollapsed: false, labelWidth: 'auto' }}
          toolBarRender={() => canCreate ? [
            <Button disabled={productId === undefined || product?.enabled === false} icon={<PlusOutlined />} key="create" onClick={() => { setEditingSku(undefined); setFormOpen(true); }} type="primary">
              {t('productSkus.create')}
            </Button>,
          ] : []}
        />
      </Drawer>
      {product && formOpen ? (
        <ProductSkuFormModal
          onClose={() => { setFormOpen(false); setEditingSku(undefined); }}
          onSubmit={saveSku}
          open={formOpen}
          product={product}
          productSku={editingSku}
          submitting={saveMutation.isPending}
        />
      ) : null}
    </>
  );
}
