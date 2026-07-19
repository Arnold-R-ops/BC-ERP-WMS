import {
  DrawerForm,
  ProFormDigit,
  ProFormList,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listProductSkus } from '../../api/productSkus';
import type { PurchaseOrderPayload } from '../../api/purchasing';

export type PurchaseOrderFormValues = Omit<PurchaseOrderPayload, 'operatorId' | 'operatorName'>;

interface PurchaseOrderFormDrawerProps {
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (values: PurchaseOrderFormValues) => Promise<boolean>;
}

export function PurchaseOrderFormDrawer({
  loading,
  open,
  onClose,
  onSubmit,
}: PurchaseOrderFormDrawerProps): JSX.Element {
  const { t } = useTranslation();
  const productSkusQuery = useQuery({
    queryKey: ['product-skus', 'active'],
    queryFn: () => listProductSkus({ enabledOnly: true }),
    enabled: open,
  });
  const productSkuOptions = (productSkusQuery.data ?? []).map((productSku) => ({
    label: `${productSku.name ?? '-'} · ${productSku.skuName ?? '-'} (${productSku.skuCode ?? productSku.barcode ?? '-'})`,
    value: productSku.id,
  }));

  return (
    <DrawerForm<PurchaseOrderFormValues>
      drawerProps={{ destroyOnHidden: true, maskClosable: false }}
      initialValues={{ items: [{ orderedQuantity: 1 }] }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: t('common.create') },
        submitButtonProps: { loading },
      }}
      title={t('purchasing.create')}
      width={920}
    >
      <ProFormText
        fieldProps={{ maxLength: 200 }}
        label={t('purchasing.fields.supplier')}
        name="supplier"
        rules={[{ required: true, message: t('purchasing.validation.supplierRequired') }]}
        width="md"
      />
      <ProFormText fieldProps={{ type: 'date' }} label={t('purchasing.fields.expectedDate')} name="expectedDate" width="sm" />
      <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('common.remark')} name="remark" />

      <ProFormList
        copyIconProps={false}
        creatorButtonProps={{ creatorButtonText: t('purchasing.form.addItem') }}
        label={t('purchasing.form.items')}
        min={1}
        name="items"
      >
        <ProFormSelect
          fieldProps={{ loading: productSkusQuery.isLoading, optionFilterProp: 'label', showSearch: true }}
          label={t('purchasing.fields.product')}
          name="productSkuId"
          options={productSkuOptions}
          rules={[{ required: true, message: t('purchasing.validation.productRequired') }]}
          width="md"
        />
        <ProFormDigit
          fieldProps={{ min: 1, precision: 0 }}
          label={t('purchasing.fields.orderedQuantity')}
          name="orderedQuantity"
          rules={[{ required: true, message: t('purchasing.validation.quantityRequired') }]}
          width="xs"
        />
        <ProFormDigit fieldProps={{ min: 0, precision: 2 }} label={t('purchasing.fields.unitCost')} name="unitCost" width="sm" />
        <ProFormText fieldProps={{ type: 'date' }} label={t('purchasing.fields.expiryDate')} name="expiryDate" width="sm" />
        <ProFormText fieldProps={{ type: 'date' }} label={t('purchasing.fields.productionDate')} name="productionDate" width="sm" />
        <ProFormText label={t('purchasing.fields.externalBatchCode')} name="externalBatchCode" width="sm" />
        <ProFormText label={t('common.remark')} name="remark" width="md" />
      </ProFormList>
    </DrawerForm>
  );
}
