import { EditOutlined, PlusOutlined, PoweroffOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Popconfirm, Progress, Space, Tag } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { getErrorMessage } from '../../api/errors';
import { paginateArray } from '../../api/pagination';
import {
  createProduct,
  listProducts,
  PRODUCTS_QUERY_KEY,
  setProductEnabled,
  updateProduct,
  type Product,
} from '../../api/products';
import { ProductFormDrawer, type ProductFormValues } from './ProductFormDrawer';
import { ProductSkuDrawer } from './ProductSkuDrawer';
import { filterProducts } from './productUtils';

interface ProductTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
  enabledStatus?: 'enabled' | 'disabled';
}

export function ProductPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [formOpen, setFormOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product>();
  const [skuProduct, setSkuProduct] = useState<Product>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const { i18n, t } = useTranslation();
  const { session } = useAuth();
  const canCreate = hasPermission(session?.currentRole, session?.permissionCodes, 'product:create');
  const canEdit = hasPermission(session?.currentRole, session?.permissionCodes, 'product:edit');
  const canChangeStatus = hasPermission(session?.currentRole, session?.permissionCodes, 'product:status');

  const saveMutation = useMutation({
    mutationFn: ({ id, values }: { id?: number; values: ProductFormValues }) => {
      if (id === undefined) return createProduct(values);
      return updateProduct(id, {
        productName: values.productName,
        categoryId: values.categoryId,
        brand: values.brand,
        description: values.description,
      });
    },
  });
  const statusMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => setProductEnabled(id, enabled),
  });

  const reload = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const saveProduct = async (values: ProductFormValues): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editingProduct?.id, values });
      message.success(editingProduct ? t('products.messages.updated') : t('products.messages.created'));
      setFormOpen(false);
      setEditingProduct(undefined);
      await reload();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const changeStatus = async (product: Product): Promise<void> => {
    if (product.id === undefined) return;
    try {
      await statusMutation.mutateAsync({ id: product.id, enabled: !product.enabled });
      message.success(t('products.messages.statusUpdated'));
      await reload();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const formatDateTime = (value?: string): string => value
    ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '-';

  const columns = useMemo<ProColumns<Product>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('products.searchPlaceholder') } },
    {
      title: t('products.fields.enabled'), dataIndex: 'enabledStatus', hideInTable: true, valueType: 'select',
      valueEnum: { enabled: { text: t('common.enabled') }, disabled: { text: t('common.disabled') } },
    },
    { title: t('products.fields.productCode'), dataIndex: 'productCode', width: 150, search: false, copyable: true, fixed: 'left' },
    {
      title: t('products.fields.productName'), dataIndex: 'productName', width: 220, search: false,
      render: (_, record) => <Space direction="vertical" size={0}><strong>{record.productName ?? '-'}</strong><span className="table-secondary">{record.brand ?? t('products.noBrand')}</span></Space>,
    },
    {
      title: t('products.fields.category'), dataIndex: 'categoryName', width: 210, search: false,
      renderText: (_, record) => [record.parentCategoryName, record.categoryName].filter(Boolean).join(' / ') || '-',
    },
    {
      title: t('products.fields.skuCount'), dataIndex: 'skuCount', width: 170, search: false,
      render: (_, record) => {
        const total = Number(record.skuCount ?? 0);
        const enabled = Number(record.enabledSkuCount ?? 0);
        const percent = total > 0 ? Math.round((enabled / total) * 100) : 0;
        return <Space direction="vertical" size={0}><span>{t('products.skuCountValue', { enabled, total })}</span><Progress percent={percent} showInfo={false} size="small" /></Space>;
      },
    },
    {
      title: t('products.fields.enabled'), dataIndex: 'enabled', width: 90, search: false,
      render: (_, record) => <Tag color={record.enabled ? 'success' : 'default'}>{record.enabled ? t('common.enabled') : t('common.disabled')}</Tag>,
    },
    { title: t('common.updatedAt'), dataIndex: 'updatedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined) },
    {
      title: t('common.actions'), valueType: 'option', width: 255, fixed: 'right',
      render: (_, record) => [
        <Button icon={<UnorderedListOutlined />} key="skus" onClick={() => setSkuProduct(record)} size="small" type="link">{t('products.actions.manageSkus')}</Button>,
        canEdit ? <Button icon={<EditOutlined />} key="edit" onClick={() => { setEditingProduct(record); setFormOpen(true); }} size="small" type="link">{t('common.edit')}</Button> : null,
        canChangeStatus ? <Popconfirm key="status" onConfirm={() => void changeStatus(record)} title={record.enabled ? t('products.confirmations.deactivate') : t('products.confirmations.activate')}>
          <Button danger={Boolean(record.enabled)} icon={<PoweroffOutlined />} size="small" type="link">{record.enabled ? t('common.disabled') : t('common.enabled')}</Button>
        </Popconfirm> : null,
      ].filter(Boolean),
    },
  ], [canChangeStatus, canEdit, i18n.language, t]);

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
            const products = await queryClient.fetchQuery({ queryKey: PRODUCTS_QUERY_KEY, queryFn: () => listProducts() });
            const enabled = params.enabledStatus === 'enabled' ? true : params.enabledStatus === 'disabled' ? false : undefined;
            return paginateArray(filterProducts(products, { search: params.search, enabled }), params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1400 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => canCreate ? [
          <Button icon={<PlusOutlined />} key="create" onClick={() => { setEditingProduct(undefined); setFormOpen(true); }} type="primary">{t('products.create')}</Button>,
        ] : []}
      />
      <ProductFormDrawer
        onClose={() => { setFormOpen(false); setEditingProduct(undefined); }}
        onSubmit={saveProduct}
        open={formOpen}
        product={editingProduct}
        submitting={saveMutation.isPending}
      />
      <ProductSkuDrawer onClose={() => setSkuProduct(undefined)} open={Boolean(skuProduct)} permissionCodes={session?.permissionCodes} product={skuProduct} role={session?.currentRole} />
    </section>
  );
}
