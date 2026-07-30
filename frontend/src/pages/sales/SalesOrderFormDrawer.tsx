import {
  DrawerForm,
  ProFormDependency,
  ProFormDigit,
  ProFormList,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { Alert, Divider } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listInventoryBatchesByProductSku } from '../../api/inventory';
import { listCustomers } from '../../api/masterData';
import { listProductSkus } from '../../api/productSkus';
import type { SalesOrderItemPayload, SalesOrderPayload } from '../../api/sales';

function BatchSelect({ productSkuId }: { productSkuId?: number }): JSX.Element {
  const { t } = useTranslation();
  const batchesQuery = useQuery({
    queryKey: ['inventory-batches', productSkuId, 'available'],
    queryFn: () => listInventoryBatchesByProductSku(productSkuId as number),
    enabled: productSkuId !== undefined,
  });
  const options = (batchesQuery.data ?? [])
    .filter((batch) => batch.id !== undefined && batch.active !== false && (batch.availableQuantity ?? 0) > 0)
    .map((batch) => ({
      label: `${batch.batchCode ?? `#${batch.id}`} · ${batch.locationCode ?? '-'} · ${t('sales.form.availableQty', { count: batch.availableQuantity ?? 0 })}`,
      value: batch.id as number,
    }));

  return (
    <ProFormSelect
      disabled={productSkuId === undefined}
      fieldProps={{ loading: batchesQuery.isLoading, mode: 'multiple', optionFilterProp: 'label', showSearch: true }}
      label={t('sales.fields.specifiedBatches')}
      name="specifiedBatchIds"
      options={options}
      width="xl"
    />
  );
}

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
    queryKey: ['customers', 'active', 'CLIENT'],
    queryFn: () => listCustomers(true, 'CLIENT'),
    enabled: open,
  });
  const productSkusQuery = useQuery({
    queryKey: ['product-skus', 'active'],
    queryFn: () => listProductSkus({ enabledOnly: true }),
    enabled: open,
  });

  const customerOptions = (customersQuery.data ?? []).map((customer) => ({
    label: `${customer.name ?? '-'} (${customer.code ?? '-'})`,
    value: customer.id,
  }));
  const productSkuOptions = (productSkusQuery.data ?? []).map((productSku) => ({
    label: `${productSku.name ?? '-'} · ${productSku.skuName ?? '-'} (${productSku.skuCode ?? productSku.barcode ?? '-'})`,
    value: productSku.id,
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

      <Divider orientation="left" plain>{t('sales.form.shipping')}</Divider>
      <ProFormText fieldProps={{ maxLength: 100 }} label={t('sales.fields.consigneeName')} name="consigneeName" width="md" />
      <ProFormText fieldProps={{ maxLength: 30 }} label={t('sales.fields.consigneePhone')} name="consigneePhone" width="sm" />
      <ProFormText fieldProps={{ maxLength: 255 }} label={t('sales.fields.shipAddress1')} name="shipAddress1" />
      <ProFormText fieldProps={{ maxLength: 255 }} label={t('sales.fields.shipAddress2')} name="shipAddress2" />
      <ProFormText fieldProps={{ maxLength: 100 }} label={t('sales.fields.shipCity')} name="shipCity" width="sm" />
      <ProFormText fieldProps={{ maxLength: 100 }} label={t('sales.fields.shipProvince')} name="shipProvince" width="sm" />
      <ProFormText fieldProps={{ maxLength: 30 }} label={t('sales.fields.shipZip')} name="shipZip" width="xs" />
      <ProFormText fieldProps={{ maxLength: 2 }} label={t('sales.fields.shipCountryCode')} name="shipCountryCode" width="xs" />

      <ProFormList
        copyIconProps={false}
        creatorButtonProps={{ creatorButtonText: t('sales.form.addItem') }}
        initialValue={initialItems}
        label={t('sales.form.items')}
        min={1}
        name="items"
      >
        <ProFormSelect
          fieldProps={{ loading: productSkusQuery.isLoading, showSearch: true, optionFilterProp: 'label' }}
          label={t('sales.fields.product')}
          name="productSkuId"
          options={productSkuOptions}
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
        <ProFormDependency name={['productSkuId']}>
          {({ productSkuId }) => <BatchSelect productSkuId={productSkuId as number | undefined} />}
        </ProFormDependency>
        <ProFormSwitch label={t('sales.fields.rejectNearExpiry')} name="rejectNearExpiry" />
        <ProFormText label={t('common.remark')} name="remark" width="md" />
      </ProFormList>
    </DrawerForm>
  );
}
