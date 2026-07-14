import {
  DrawerForm,
  ProForm,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { Divider } from 'antd';
import { useTranslation } from 'react-i18next';
import type { Product, ProductPayload } from '../../api/products';
import type { SpuOption } from './productUtils';

interface ProductFormDrawerProps {
  open: boolean;
  product?: Product;
  spuOptions: SpuOption[];
  submitting: boolean;
  onClose: () => void;
  onSubmit: (payload: ProductPayload) => Promise<boolean>;
}

function getInitialValues(product: Product | undefined, spuOptions: SpuOption[]): ProductPayload {
  return {
    barcode: product?.barcode ?? '',
    name: product?.name ?? '',
    skuName: product?.skuName ?? '',
    spuId: product?.spuId ?? spuOptions[0]?.value ?? 0,
    specs: product?.specs,
    specification: product?.specification,
    unitPrice: product?.unitPrice ?? 0,
    minSalesPrice: product?.minSalesPrice,
    minStock: product?.minStock ?? 0,
    safetyStock: product?.safetyStock ?? 0,
    leadTime: product?.leadTime ?? 0,
    perPackQty: product?.perPackQty ?? 1,
    conversionRate: product?.conversionRate ?? 1,
    packUnit: product?.packUnit,
    nearExpiryDays: product?.nearExpiryDays ?? 90,
    category: product?.category,
    supplier: product?.supplier,
    description: product?.description,
    batchTrackingMode: product?.batchTrackingMode ?? 'PRINTED_LABEL',
    enabled: product?.enabled ?? true,
  };
}

export function ProductFormDrawer({
  open,
  product,
  spuOptions,
  submitting,
  onClose,
  onSubmit,
}: ProductFormDrawerProps): JSX.Element {
  const { t } = useTranslation();
  const editing = product?.id !== undefined;

  return (
    <DrawerForm<ProductPayload>
      key={product?.id ?? 'new-product'}
      drawerProps={{
        destroyOnHidden: true,
        maskClosable: false,
      }}
      initialValues={getInitialValues(product, spuOptions)}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          onClose();
        }
      }}
      open={open}
      submitter={{
        searchConfig: {
          resetText: t('common.cancel'),
          submitText: editing ? t('common.save') : t('common.create'),
        },
        submitButtonProps: { loading: submitting },
      }}
      title={editing ? t('products.form.editTitle') : t('products.form.createTitle')}
      width={760}
    >
      <Divider orientation="left" plain>{t('products.form.basicGroup')}</Divider>
      <ProForm.Group>
        <ProFormText
          fieldProps={{ maxLength: 200 }}
          label={t('products.fields.name')}
          name="name"
          rules={[{ required: true, message: t('products.validation.nameRequired') }]}
          width="md"
        />
        <ProFormText
          fieldProps={{ maxLength: 100 }}
          label={t('products.fields.skuName')}
          name="skuName"
          rules={[{ required: true, message: t('products.validation.skuNameRequired') }]}
          width="md"
        />
      </ProForm.Group>
      <ProForm.Group>
        <ProFormText
          fieldProps={{ maxLength: 50 }}
          formItemProps={{ extra: t('products.form.barcodeHint') }}
          label={t('products.fields.barcode')}
          name="barcode"
          rules={[{ required: true, message: t('products.validation.barcodeRequired') }]}
          width="md"
        />
        <ProFormSelect
          fieldProps={{ optionFilterProp: 'label', showSearch: true }}
          label={t('products.fields.spu')}
          name="spuId"
          options={spuOptions}
          placeholder={t('products.form.spuPlaceholder')}
          rules={[{ required: true, message: t('products.validation.spuRequired') }]}
          tooltip={t('products.form.spuSourceHint')}
          width="md"
        />
      </ProForm.Group>
      <ProForm.Group>
        <ProFormText
          fieldProps={{ maxLength: 100 }}
          label={t('products.fields.specification')}
          name="specification"
          width="md"
        />
        <ProFormText
          fieldProps={{ maxLength: 500 }}
          label={t('products.fields.specs')}
          name="specs"
          width="md"
        />
      </ProForm.Group>
      <ProForm.Group>
        <ProFormText
          fieldProps={{ maxLength: 100 }}
          label={t('products.fields.category')}
          name="category"
          width="md"
        />
        <ProFormText
          fieldProps={{ maxLength: 200 }}
          label={t('products.fields.supplier')}
          name="supplier"
          width="md"
        />
      </ProForm.Group>
      <ProFormTextArea
        fieldProps={{ maxLength: 1000, showCount: true }}
        label={t('products.fields.description')}
        name="description"
      />

      <Divider orientation="left" plain>{t('products.form.pricingGroup')}</Divider>
      <ProForm.Group>
        <ProFormDigit
          fieldProps={{ min: 0, precision: 2 }}
          label={t('products.fields.unitPrice')}
          name="unitPrice"
          rules={[{ required: true, message: t('products.validation.unitPriceRequired') }]}
          width="sm"
        />
        <ProFormDigit
          fieldProps={{ min: 0, precision: 2 }}
          label={t('products.fields.minSalesPrice')}
          name="minSalesPrice"
          width="sm"
        />
      </ProForm.Group>

      <Divider orientation="left" plain>{t('products.form.packagingGroup')}</Divider>
      <ProForm.Group>
        <ProFormDigit
          fieldProps={{ min: 1, precision: 0 }}
          label={t('products.fields.perPackQty')}
          name="perPackQty"
          width="xs"
        />
        <ProFormDigit
          fieldProps={{ min: 1, precision: 0 }}
          label={t('products.fields.conversionRate')}
          name="conversionRate"
          width="xs"
        />
        <ProFormText
          fieldProps={{ maxLength: 20 }}
          label={t('products.fields.packUnit')}
          name="packUnit"
          width="xs"
        />
      </ProForm.Group>

      <Divider orientation="left" plain>{t('products.form.inventoryGroup')}</Divider>
      <ProForm.Group>
        <ProFormDigit
          fieldProps={{ min: 0, precision: 0 }}
          label={t('products.fields.minStock')}
          name="minStock"
          width="xs"
        />
        <ProFormDigit
          fieldProps={{ min: 0, precision: 0 }}
          label={t('products.fields.safetyStock')}
          name="safetyStock"
          width="xs"
        />
        <ProFormDigit
          fieldProps={{ min: 0, precision: 0 }}
          label={t('products.fields.leadTime')}
          name="leadTime"
          width="xs"
        />
        <ProFormDigit
          fieldProps={{ min: 1, precision: 0 }}
          label={t('products.fields.nearExpiryDays')}
          name="nearExpiryDays"
          width="xs"
        />
      </ProForm.Group>
      <ProForm.Group>
        <ProFormSelect
          label={t('products.fields.batchTrackingMode')}
          name="batchTrackingMode"
          options={[
            { label: t('products.batchTracking.PRINTED_LABEL'), value: 'PRINTED_LABEL' },
            { label: t('products.batchTracking.LOCATION_VISUAL'), value: 'LOCATION_VISUAL' },
          ]}
          width="md"
        />
        <ProFormSwitch label={t('products.fields.enabled')} name="enabled" />
      </ProForm.Group>
    </DrawerForm>
  );
}
