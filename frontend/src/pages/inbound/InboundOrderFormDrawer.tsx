import {
  DrawerForm,
  ProFormDependency,
  ProFormDigit,
  ProFormList,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { Alert } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listActiveWarehouses, listLocationsByWarehouse } from '../../api/masterData';
import { listProducts } from '../../api/products';
import type { InboundOrderPayload } from '../../api/inbound';

function LocationSelect({ warehouseId }: { warehouseId?: number }): JSX.Element {
  const { t } = useTranslation();
  const locationsQuery = useQuery({
    queryKey: ['locations', warehouseId],
    queryFn: () => listLocationsByWarehouse(warehouseId as number),
    enabled: warehouseId !== undefined,
  });
  return (
    <ProFormSelect
      disabled={warehouseId === undefined}
      fieldProps={{ loading: locationsQuery.isLoading, optionFilterProp: 'label', showSearch: true }}
      label={t('inbound.fields.targetLocation')}
      name="targetLocationId"
      options={(locationsQuery.data ?? []).filter((location) => location.enabled !== false).map((location) => ({ label: location.locationCode ?? `#${location.id}`, value: location.id }))}
      rules={[{ required: true, message: t('inbound.validation.locationRequired') }]}
      width="md"
    />
  );
}

interface InboundOrderFormDrawerProps {
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: InboundOrderPayload) => Promise<boolean>;
}

export function InboundOrderFormDrawer({ loading, open, onClose, onSubmit }: InboundOrderFormDrawerProps): JSX.Element {
  const { t } = useTranslation();
  const productsQuery = useQuery({ queryKey: ['products', 'active'], queryFn: () => listProducts(true), enabled: open });
  const warehousesQuery = useQuery({ queryKey: ['warehouses', 'active'], queryFn: listActiveWarehouses, enabled: open });
  const productOptions = (productsQuery.data ?? []).map((product) => ({ label: `${product.name ?? '-'} (${product.barcode ?? '-'})`, value: product.id }));
  const warehouseOptions = (warehousesQuery.data ?? []).map((warehouse) => ({ label: warehouse.name ?? warehouse.code ?? `#${warehouse.id}`, value: warehouse.id }));

  return (
    <DrawerForm<InboundOrderPayload>
      drawerProps={{ destroyOnHidden: true, maskClosable: false }}
      initialValues={{ items: [{ planQty: 1 }] }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{ searchConfig: { resetText: t('common.cancel'), submitText: t('common.create') }, submitButtonProps: { loading } }}
      title={t('inbound.create')}
      width={940}
    >
      <Alert message={t('inbound.form.supplierIdNotice')} showIcon type="info" />
      <ProFormDigit
        fieldProps={{ min: 1, precision: 0 }}
        label={t('inbound.fields.supplierId')}
        name="supplierId"
        rules={[{ required: true, message: t('inbound.validation.supplierRequired') }]}
        width="sm"
      />
      <ProFormText fieldProps={{ type: 'date' }} label={t('inbound.fields.expectedDate')} name="expectedDate" width="sm" />
      <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('common.remark')} name="remark" />

      <ProFormList copyIconProps={false} creatorButtonProps={{ creatorButtonText: t('inbound.form.addItem') }} label={t('inbound.form.items')} min={1} name="items">
        <ProFormSelect
          fieldProps={{ loading: productsQuery.isLoading, optionFilterProp: 'label', showSearch: true }}
          label={t('inbound.fields.product')}
          name="productId"
          options={productOptions}
          rules={[{ required: true, message: t('inbound.validation.productRequired') }]}
          width="md"
        />
        <ProFormDigit fieldProps={{ min: 1, precision: 0 }} label={t('inbound.fields.planQty')} name="planQty" rules={[{ required: true, message: t('inbound.validation.quantityRequired') }]} width="xs" />
        <ProFormDigit fieldProps={{ min: 0, precision: 2 }} label={t('inbound.fields.unitCost')} name="unitCost" width="sm" />
        <ProFormSelect label={t('inbound.fields.targetWarehouse')} name="targetWarehouseId" options={warehouseOptions} rules={[{ required: true, message: t('inbound.validation.warehouseRequired') }]} width="md" />
        <ProFormDependency name={['targetWarehouseId']}>
          {({ targetWarehouseId }) => <LocationSelect warehouseId={targetWarehouseId as number | undefined} />}
        </ProFormDependency>
        <ProFormText label={t('common.remark')} name="remark" width="md" />
      </ProFormList>
    </DrawerForm>
  );
}
