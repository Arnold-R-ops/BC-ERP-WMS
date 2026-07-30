import { useQuery } from '@tanstack/react-query';
import { Alert, Form, Input, InputNumber, Modal, Radio, Select } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { CreateEmergencyCorrectionPayload } from '../../api/emergencyCorrections';
import { listInventoryBatchesByProductSku } from '../../api/inventory';
import { listActiveWarehouses, listLocationsByWarehouse } from '../../api/masterData';
import { listProductSkus, PRODUCT_SKUS_QUERY_KEY } from '../../api/productSkus';

type CorrectionMode = 'EXISTING_BATCH' | 'NEW_BATCH';

interface CorrectionFormValues extends Omit<CreateEmergencyCorrectionPayload, 'locationId'> {
  locationId?: number;
  mode: CorrectionMode;
  warehouseId?: number;
}

interface EmergencyCorrectionCreateModalProps {
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: CreateEmergencyCorrectionPayload) => Promise<void>;
  open: boolean;
}

export function EmergencyCorrectionCreateModal({
  loading,
  onCancel,
  onConfirm,
  open,
}: EmergencyCorrectionCreateModalProps): JSX.Element {
  const [form] = Form.useForm<CorrectionFormValues>();
  const { t } = useTranslation();
  const mode = Form.useWatch('mode', form);
  const productSkuId = Form.useWatch('productSkuId', form);
  const warehouseId = Form.useWatch('warehouseId', form);
  const inventoryBatchId = Form.useWatch('inventoryBatchId', form);
  const productSkusQuery = useQuery({
    queryKey: [...PRODUCT_SKUS_QUERY_KEY, 'enabled'],
    queryFn: () => listProductSkus({ enabledOnly: true }),
    enabled: open,
  });
  const warehousesQuery = useQuery({ queryKey: ['active-warehouses'], queryFn: listActiveWarehouses, enabled: open });
  const locationsQuery = useQuery({
    queryKey: ['locations', warehouseId],
    queryFn: () => listLocationsByWarehouse(warehouseId as number),
    enabled: open && mode === 'NEW_BATCH' && warehouseId !== undefined,
  });
  const batchesQuery = useQuery({
    queryKey: ['inventory-batches', productSkuId],
    queryFn: () => listInventoryBatchesByProductSku(productSkuId as number),
    enabled: open && mode === 'EXISTING_BATCH' && productSkuId !== undefined,
  });
  const selectedBatch = (batchesQuery.data ?? []).find((batch) => batch.id === inventoryBatchId);

  useEffect(() => {
    if (open) form.setFieldsValue({ mode: 'EXISTING_BATCH', reasonCode: 'PHYSICAL_COUNT_MISMATCH' });
    else form.resetFields();
  }, [form, open]);

  useEffect(() => {
    form.setFieldsValue({ inventoryBatchId: undefined, batchCode: undefined, warehouseId: undefined, locationId: undefined });
  }, [form, mode, productSkuId]);

  const submit = async (values: CorrectionFormValues): Promise<void> => {
    if (values.mode === 'EXISTING_BATCH') {
      if (!selectedBatch?.id || !selectedBatch.locationId) return;
      await onConfirm({
        productSkuId: values.productSkuId,
        locationId: selectedBatch.locationId,
        inventoryBatchId: selectedBatch.id,
        countedQty: values.countedQty,
        reasonCode: values.reasonCode,
        reasonDetail: values.reasonDetail,
        evidenceUrl: values.evidenceUrl,
        relatedSalesOrderId: values.relatedSalesOrderId,
      });
      return;
    }
    await onConfirm({
      productSkuId: values.productSkuId,
      locationId: values.locationId as number,
      batchCode: values.batchCode?.trim(),
      productionDate: values.productionDate,
      expiryDate: values.expiryDate,
      countedQty: values.countedQty,
      reasonCode: values.reasonCode,
      reasonDetail: values.reasonDetail,
      evidenceUrl: values.evidenceUrl,
      relatedSalesOrderId: values.relatedSalesOrderId,
    });
  };

  const reasonCodes = ['PHYSICAL_COUNT_MISMATCH', 'UNRECORDED_STOCK_FOUND', 'DAMAGED_OR_LOST', 'MISPLACED_STOCK', 'SYSTEM_DATA_ERROR', 'OTHER'];

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okText={t('corrections.actions.create')}
      onCancel={onCancel}
      onOk={() => form.submit()}
      open={open}
      title={t('corrections.create.title')}
      width={720}
    >
      <Alert description={t('corrections.create.auditNotice')} message={t('corrections.create.auditTitle')} showIcon type="warning" />
      <Form form={form} layout="vertical" onFinish={submit} style={{ marginTop: 16 }}>
        <Form.Item label={t('corrections.create.mode')} name="mode" rules={[{ required: true }]}>
          <Radio.Group
            optionType="button"
            options={[
              { label: t('corrections.create.existingBatch'), value: 'EXISTING_BATCH' },
              { label: t('corrections.create.newBatch'), value: 'NEW_BATCH' },
            ]}
          />
        </Form.Item>
        <Form.Item label={t('corrections.fields.product')} name="productSkuId" rules={[{ required: true, message: t('corrections.validation.productRequired') }]}>
          <Select
            loading={productSkusQuery.isLoading}
            options={(productSkusQuery.data ?? []).filter((sku) => sku.id !== undefined).map((sku) => ({
              label: `${sku.skuCode ?? '-'} · ${sku.name ?? sku.skuName ?? sku.productName ?? '-'}`,
              value: sku.id,
            }))}
            placeholder={t('corrections.create.productPlaceholder')}
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>

        {mode === 'EXISTING_BATCH' ? (
          <Form.Item label={t('corrections.fields.batchCode')} name="inventoryBatchId" rules={[{ required: true, message: t('corrections.validation.batchRequired') }]}>
            <Select
              disabled={!productSkuId}
              loading={batchesQuery.isLoading}
              options={(batchesQuery.data ?? []).filter((batch) => batch.id !== undefined && batch.locationId !== undefined).map((batch) => ({
                label: `${batch.batchCode ?? '-'} · ${batch.locationCode ?? '-'} · ${t('corrections.create.bookQty', { count: batch.quantity ?? 0 })}`,
                value: batch.id,
              }))}
              placeholder={t('corrections.create.batchPlaceholder')}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
        ) : (
          <>
            <Form.Item label={t('corrections.fields.warehouse')} name="warehouseId" rules={[{ required: true }]}>
              <Select
                options={(warehousesQuery.data ?? []).filter((warehouse) => warehouse.id !== undefined).map((warehouse) => ({ label: warehouse.name ?? warehouse.code, value: warehouse.id }))}
                placeholder={t('corrections.create.warehousePlaceholder')}
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>
            <Form.Item label={t('corrections.fields.location')} name="locationId" rules={[{ required: true, message: t('corrections.validation.locationRequired') }]}>
              <Select
                disabled={!warehouseId}
                loading={locationsQuery.isLoading}
                options={(locationsQuery.data ?? []).filter((location) => location.id !== undefined).map((location) => ({ label: location.locationCode ?? String(location.id), value: location.id }))}
                placeholder={t('corrections.create.locationPlaceholder')}
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>
            <Form.Item label={t('corrections.fields.batchCode')} name="batchCode" rules={[{ required: true, whitespace: true, message: t('corrections.validation.batchCodeRequired') }]}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item label={t('corrections.fields.productionDate')} name="productionDate"><Input type="date" /></Form.Item>
            <Form.Item label={t('corrections.fields.expiryDate')} name="expiryDate" rules={[{ required: true, message: t('corrections.validation.expiryRequired') }]}><Input type="date" /></Form.Item>
          </>
        )}

        <Form.Item label={t('corrections.fields.countedQty')} name="countedQty" rules={[{ required: true, message: t('corrections.validation.countRequired') }]}>
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item label={t('corrections.fields.reasonCode')} name="reasonCode" rules={[{ required: true }]}>
          <Select options={reasonCodes.map((value) => ({ label: t(`corrections.reasons.${value}`), value }))} />
        </Form.Item>
        <Form.Item label={t('corrections.fields.reasonDetail')} name="reasonDetail" rules={[{ required: true, whitespace: true, message: t('corrections.validation.reasonRequired') }]}>
          <Input.TextArea maxLength={1000} rows={3} showCount />
        </Form.Item>
        <Form.Item label={t('corrections.fields.evidenceUrl')} name="evidenceUrl"><Input type="url" /></Form.Item>
        <Form.Item label={t('corrections.fields.relatedSalesOrderId')} name="relatedSalesOrderId"><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
      </Form>
    </Modal>
  );
}
