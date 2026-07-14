import {
  ModalForm,
  ProFormDependency,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import type { ChannelSkuMapping, ChannelSkuMappingPayload } from '../../api/integrations';
import type { Product } from '../../api/products';

interface SkuMappingFormModalProps {
  loading: boolean;
  mapping?: ChannelSkuMapping;
  open: boolean;
  products: Product[];
  onClose: () => void;
  onSubmit: (payload: ChannelSkuMappingPayload) => Promise<boolean>;
}

export function SkuMappingFormModal({ loading, mapping, open, products, onClose, onSubmit }: SkuMappingFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const editing = mapping?.id !== undefined;
  const productOptions = products.filter((product) => product.id !== undefined).map((product) => ({
    label: `${product.barcode ?? '-'} · ${product.name ?? product.skuName ?? `#${product.id}`}`,
    value: product.id as number,
  }));

  return (
    <ModalForm<ChannelSkuMappingPayload>
      key={mapping?.id ?? 'new-sku-mapping'}
      initialValues={{
        channel: mapping?.channel ?? 'SHOPIFY',
        storeIdentifier: mapping?.storeIdentifier,
        externalSku: mapping?.externalSku ?? '',
        mappingType: mapping?.mappingType ?? 'PRODUCT',
        productId: mapping?.productId,
        quantityRatio: mapping?.quantityRatio ?? 1,
        status: mapping?.status ?? 'ACTIVE',
        remark: mapping?.remark,
      }}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: editing ? t('common.save') : t('common.create') },
        submitButtonProps: { loading },
      }}
      title={editing ? t('integrations.mappings.form.editTitle') : t('integrations.mappings.form.createTitle')}
      width={680}
    >
      <ProFormSelect label={t('integrations.fields.channel')} name="channel" options={[{ label: 'Shopify', value: 'SHOPIFY' }]} rules={[{ required: true }]} />
      <ProFormText fieldProps={{ maxLength: 255 }} label={t('integrations.fields.store')} name="storeIdentifier" />
      <ProFormText
        disabled={editing}
        fieldProps={{ maxLength: 200 }}
        label={t('integrations.fields.externalSku')}
        name="externalSku"
        rules={[{ required: true, message: t('integrations.mappings.validation.externalSkuRequired') }]}
      />
      <ProFormSelect
        label={t('integrations.fields.mappingType')}
        name="mappingType"
        options={[
          { label: t('integrations.mappings.types.PRODUCT'), value: 'PRODUCT' },
          { label: t('integrations.mappings.types.VIRTUAL'), value: 'VIRTUAL' },
        ]}
        rules={[{ required: true }]}
      />
      <ProFormDependency name={['mappingType']}>
        {({ mappingType }) => mappingType === 'PRODUCT' ? (
          <>
            <ProFormSelect
              fieldProps={{ optionFilterProp: 'label', showSearch: true }}
              label={t('integrations.fields.internalProduct')}
              name="productId"
              options={productOptions}
              rules={[{ required: true, message: t('integrations.mappings.validation.productRequired') }]}
            />
            <ProFormDigit
              extra={t('integrations.mappings.form.ratioHint')}
              fieldProps={{ precision: 0 }}
              label={t('integrations.fields.quantityRatio')}
              min={1}
              name="quantityRatio"
              rules={[{ required: true }]}
            />
          </>
        ) : null}
      </ProFormDependency>
      {editing && (
        <ProFormSelect
          label={t('integrations.fields.status')}
          name="status"
          options={[
            { label: t('common.enabled'), value: 'ACTIVE' },
            { label: t('common.disabled'), value: 'DISABLED' },
          ]}
        />
      )}
      <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('common.remark')} name="remark" />
    </ModalForm>
  );
}
