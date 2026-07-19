import {
  DrawerForm,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert } from 'antd';
import { useTranslation } from 'react-i18next';
import { CATEGORIES_QUERY_KEY, getCategoryTree } from '../../api/categories';
import type { Product } from '../../api/products';

export interface ProductFormValues {
  productCode: string;
  productName: string;
  categoryId: number;
  brand?: string;
  description?: string;
  enabled?: boolean;
}

interface ProductFormDrawerProps {
  open: boolean;
  product?: Product;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (values: ProductFormValues) => Promise<boolean>;
}

export function ProductFormDrawer({
  open,
  product,
  submitting,
  onClose,
  onSubmit,
}: ProductFormDrawerProps): JSX.Element {
  const { t } = useTranslation();
  const editing = product?.id !== undefined;
  const categoriesQuery = useQuery({
    queryKey: [...CATEGORIES_QUERY_KEY, 'tree', 'enabled'],
    queryFn: () => getCategoryTree(true),
    enabled: open,
  });

  const categoryOptions = (categoriesQuery.data ?? []).flatMap((root) =>
    (root.children ?? [])
      .filter((child) => child.id !== undefined)
      .map((child) => ({
        label: `${root.categoryName ?? '-'} / ${child.categoryName ?? '-'}`,
        value: child.id as number,
      })),
  );

  return (
    <DrawerForm<ProductFormValues>
      key={product?.id ?? 'new-product'}
      drawerProps={{ destroyOnHidden: true, maskClosable: false }}
      initialValues={{
        productCode: product?.productCode ?? '',
        productName: product?.productName ?? '',
        categoryId: product?.categoryId,
        brand: product?.brand,
        description: product?.description,
        enabled: product?.enabled ?? true,
      }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: {
          resetText: t('common.cancel'),
          submitText: editing ? t('common.save') : t('common.create'),
        },
        submitButtonProps: { loading: submitting },
      }}
      title={editing ? t('products.form.editTitle') : t('products.form.createTitle')}
      width={620}
    >
      <Alert message={t('products.form.modelHint')} showIcon type="info" />
      <ProFormText
        disabled={editing}
        fieldProps={{ maxLength: 50 }}
        label={t('products.fields.productCode')}
        name="productCode"
        rules={[{ required: true, message: t('products.validation.codeRequired') }]}
      />
      <ProFormText
        fieldProps={{ maxLength: 200 }}
        label={t('products.fields.productName')}
        name="productName"
        rules={[{ required: true, message: t('products.validation.nameRequired') }]}
      />
      <ProFormSelect
        fieldProps={{ loading: categoriesQuery.isLoading, optionFilterProp: 'label', showSearch: true }}
        label={t('products.fields.category')}
        name="categoryId"
        options={categoryOptions}
        placeholder={t('products.form.categoryPlaceholder')}
        rules={[{ required: true, message: t('products.validation.categoryRequired') }]}
      />
      <ProFormText
        fieldProps={{ maxLength: 100 }}
        label={t('products.fields.brand')}
        name="brand"
      />
      <ProFormTextArea
        fieldProps={{ maxLength: 2000, showCount: true }}
        label={t('products.fields.description')}
        name="description"
      />
      {!editing ? <ProFormSwitch label={t('products.fields.enabled')} name="enabled" /> : null}
    </DrawerForm>
  );
}
