import {
  DrawerForm,
  ProFormDigit,
  ProFormList,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { Alert } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listCustomers } from '../../api/masterData';
import { listProducts } from '../../api/products';
import type { SalesOrderItemPayload, SalesOrderPayload } from '../../api/sales';

interface SalesOrderFormDrawerProps {
  initialItems?: SalesOrderItemPayload[];
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: SalesOrderPayload) => Promise<boolean>;
}

export function SalesOrderFormDrawer({
  initialItems,
  loading,
  open,
  onClose,
  onSubmit,
}: SalesOrderFormDrawerProps): JSX.Element {
  const { t } = useTranslation();
  const customersQuery = useQuery({
    queryKey: ['customers', 'active'],
    queryFn: () => listCustomers(true),
    enabled: open,
  });
  const productsQuery = useQuery({
    queryKey: ['products', 'active'],
    queryFn: () => listProducts(true),
    enabled: open,
  });

  const customerOptions = (customersQuery.data ?? []).map((customer) => ({
    label: `${customer.name ?? '-'} (${customer.code ?? '-'})`,
    value: customer.id,
  }));
  const productOptions = (productsQuery.data ?? []).map((product) => ({
    label: `${product.name ?? '-'} (${product.barcode ?? '-'})`,
    value: product.id,
  }));

  return (
    <DrawerForm<SalesOrderPayload>
      drawerProps={{ destroyOnHidden: true, maskClosable: false }}
      initialValues={{
        channel: 'MANUAL',
        items: initialItems?.length
          ? initialItems
          : [{ quantity: 1, unitPrice: 0, rejectNearExpiry: false }],
      }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          onClose();
        }
      }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: t('common.create') },
        submitButtonProps: { loading },
      }}
      title={t('sales.create')}
      width={900}
    >
      {initialItems?.length ? (
        <Alert message={t('sales.import.loaded', { count: initialItems.length })} showIcon type="success" />
      ) : null}

      <ProFormSelect
        fieldProps={{ loading: customersQuery.isLoading, showSearch: true, optionFilterProp: 'label' }}
        label={t('sales.fields.customer')}
        name="customerId"
        options={customerOptions}
        rules={[{ required: true, message: t('sales.validation.customerRequired') }]}
      />
      <ProFormSelect
        label={t('sales.fields.channel')}
        name="channel"
        options={[
          { label: t('sales.channels.MANUAL'), value: 'MANUAL' },
          { label: t('sales.channels.WECHAT'), value: 'WECHAT' },
          { label: t('sales.channels.SHOPIFY'), value: 'SHOPIFY' },
        ]}
        width="sm"
      />

      <ProFormList
        copyIconProps={false}
        creatorButtonProps={{ creatorButtonText: t('sales.form.addItem') }}
        initialValue={initialItems}
        label={t('sales.form.items')}
        min={1}
        name="items"
      >
        <ProFormSelect
          fieldProps={{ loading: productsQuery.isLoading, showSearch: true, optionFilterProp: 'label' }}
          label={t('sales.fields.product')}
          name="productId"
          options={productOptions}
          rules={[{ required: true, message: t('sales.validation.productRequired') }]}
          width="md"
        />
        <ProFormDigit
          fieldProps={{ min: 1, precision: 0 }}
          label={t('sales.fields.quantity')}
          name="quantity"
          rules={[{ required: true, message: t('sales.validation.quantityRequired') }]}
          width="xs"
        />
        <ProFormDigit
          fieldProps={{ min: 0, precision: 2 }}
          label={t('sales.fields.unitPrice')}
          name="unitPrice"
          rules={[{ required: true, message: t('sales.validation.priceRequired') }]}
          width="sm"
        />
        <ProFormSwitch label={t('sales.fields.rejectNearExpiry')} name="rejectNearExpiry" />
        <ProFormText label={t('common.remark')} name="remark" width="md" />
      </ProFormList>
    </DrawerForm>
  );
}
