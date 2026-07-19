import {
  ModalForm,
  ProForm,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { Alert, Divider } from 'antd';
import { useTranslation } from 'react-i18next';
import type { Product } from '../../api/products';
import type { ProductSku, ProductSkuPayload } from '../../api/productSkus';

interface ProductSkuFormModalProps {
  open: boolean;
  product: Product;
  productSku?: ProductSku;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (payload: ProductSkuPayload) => Promise<boolean>;
}

export function ProductSkuFormModal({
  open,
  product,
  productSku,
  submitting,
  onClose,
  onSubmit,
}: ProductSkuFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const editing = productSku?.id !== undefined;

  return (
    <ModalForm<ProductSkuPayload>
      key={productSku?.id ?? `new-sku-${product.id}`}
      initialValues={{
        productId: product.id as number,
        barcode: productSku?.barcode ?? '',
        name: productSku?.name ?? product.productName ?? '',
        skuName: productSku?.skuName ?? '',
        specs: productSku?.specs,
        specification: productSku?.specification,
        unitPrice: productSku?.unitPrice ?? 0,
        minSalesPrice: productSku?.minSalesPrice,
        minStock: productSku?.minStock ?? 0,
        safetyStock: productSku?.safetyStock ?? 0,
        leadTime: productSku?.leadTime ?? 7,
        perPackQty: productSku?.perPackQty ?? 1,
        conversionRate: productSku?.conversionRate ?? 1,
        packUnit: productSku?.packUnit ?? t('common.unit'),
        nearExpiryDays: productSku?.nearExpiryDays ?? 90,
        supplier: productSku?.supplier,
        description: productSku?.description,
        batchTrackingMode: productSku?.batchTrackingMode ?? 'PRINTED_LABEL',
        enabled: productSku?.enabled ?? true,
      }}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: t('common.save') },
        submitButtonProps: { loading: submitting },
      }}
      title={editing ? t('productSkus.form.editTitle') : t('productSkus.form.createTitle')}
      width={860}
    >
      <Alert
        message={`${product.productCode ?? '-'} · ${product.productName ?? '-'}`}
        description={editing ? t('productSkus.form.codeImmutable', { code: productSku?.skuCode ?? '-' }) : t('productSkus.form.codeGenerated')}
        showIcon
        type="info"
      />
      <ProFormText hidden name="productId" />
      <Divider orientation="left" plain>{t('productSkus.form.basicGroup')}</Divider>
      <ProForm.Group>
        <ProFormText
          fieldProps={{ maxLength: 200 }}
          label={t('productSkus.fields.name')}
          name="name"
          rules={[{ required: true, message: t('productSkus.validation.nameRequired') }]}
          width="md"
        />
        <ProFormText
          fieldProps={{ maxLength: 100 }}
          label={t('productSkus.fields.skuName')}
          name="skuName"
          rules={[{ required: true, message: t('productSkus.validation.skuNameRequired') }]}
          width="md"
        />
      </ProForm.Group>
      <ProForm.Group>
        <ProFormText
          fieldProps={{ maxLength: 50 }}
          label={t('productSkus.fields.barcode')}
          name="barcode"
          rules={[{ required: true, message: t('productSkus.validation.barcodeRequired') }]}
          width="md"
        />
        <ProFormText fieldProps={{ maxLength: 100 }} label={t('productSkus.fields.specification')} name="specification" width="md" />
      </ProForm.Group>
      <ProFormText fieldProps={{ maxLength: 500 }} label={t('productSkus.fields.specs')} name="specs" />

      <Divider orientation="left" plain>{t('productSkus.form.pricingGroup')}</Divider>
      <ProForm.Group>
        <ProFormDigit fieldProps={{ min: 0, precision: 2 }} label={t('productSkus.fields.unitPrice')} name="unitPrice" rules={[{ required: true, message: t('productSkus.validation.unitPriceRequired') }]} width="sm" />
        <ProFormDigit fieldProps={{ min: 0, precision: 2 }} label={t('productSkus.fields.minSalesPrice')} name="minSalesPrice" width="sm" />
      </ProForm.Group>

      <Divider orientation="left" plain>{t('productSkus.form.packagingGroup')}</Divider>
      <ProForm.Group>
        <ProFormDigit fieldProps={{ min: 1, precision: 0 }} label={t('productSkus.fields.perPackQty')} name="perPackQty" width="xs" />
        <ProFormDigit fieldProps={{ min: 1, precision: 0 }} label={t('productSkus.fields.conversionRate')} name="conversionRate" width="xs" />
        <ProFormText fieldProps={{ maxLength: 20 }} label={t('productSkus.fields.packUnit')} name="packUnit" width="xs" />
      </ProForm.Group>

      <Divider orientation="left" plain>{t('productSkus.form.inventoryGroup')}</Divider>
      <ProForm.Group>
        <ProFormDigit fieldProps={{ min: 0, precision: 0 }} label={t('productSkus.fields.minStock')} name="minStock" width="xs" />
        <ProFormDigit fieldProps={{ min: 0, precision: 0 }} label={t('productSkus.fields.safetyStock')} name="safetyStock" width="xs" />
        <ProFormDigit fieldProps={{ min: 0, precision: 0 }} label={t('productSkus.fields.leadTime')} name="leadTime" width="xs" />
        <ProFormDigit fieldProps={{ min: 1, precision: 0 }} label={t('productSkus.fields.nearExpiryDays')} name="nearExpiryDays" width="xs" />
      </ProForm.Group>
      <ProForm.Group>
        <ProFormText fieldProps={{ maxLength: 200 }} label={t('productSkus.fields.supplier')} name="supplier" width="md" />
        <ProFormSelect
          label={t('productSkus.fields.batchTrackingMode')}
          name="batchTrackingMode"
          options={[
            { label: t('productSkus.batchTracking.PRINTED_LABEL'), value: 'PRINTED_LABEL' },
            { label: t('productSkus.batchTracking.LOCATION_VISUAL'), value: 'LOCATION_VISUAL' },
          ]}
          width="md"
        />
      </ProForm.Group>
      <ProFormTextArea fieldProps={{ maxLength: 1000, showCount: true }} label={t('productSkus.fields.description')} name="description" />
      <ProFormSwitch label={t('productSkus.fields.enabled')} name="enabled" />
    </ModalForm>
  );
}
