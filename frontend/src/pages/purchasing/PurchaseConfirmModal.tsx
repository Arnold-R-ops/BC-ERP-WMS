import { useQuery } from '@tanstack/react-query';
import { Alert, Checkbox, Form, Input, InputNumber, Modal, Select } from 'antd';
import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getPurchaseOrderAllocations,
  listPurchaseReferenceSkus,
  type CreatePurchaseAsnRequest,
  type PurchaseAsn,
  type PurchaseOrder,
} from '../../api/purchasing';
import { WORKFLOW_MODAL_Z_INDEX } from '../../constants/layers';

interface PurchaseConfirmModalProps {
  asn?: PurchaseAsn;
  intent?: 'quick-confirm' | 'draft';
  loading: boolean;
  open: boolean;
  order?: PurchaseOrder;
  onCancel: () => void;
  onConfirm: (payload: CreatePurchaseAsnRequest) => Promise<void> | void;
}

interface ShipmentLine {
  included: boolean;
  purchaseOrderItemId: number;
  productSkuId?: number;
  shippedQuantity: number;
  productionDate?: string;
  expiryDate?: string;
  externalBatchCode?: string;
  remark?: string;
}

interface ShipmentFormValues extends Omit<CreatePurchaseAsnRequest, 'items'> {
  items: ShipmentLine[];
}

export function PurchaseConfirmModal({
  asn,
  intent = 'quick-confirm',
  loading,
  open,
  order,
  onCancel,
  onConfirm,
}: PurchaseConfirmModalProps): JSX.Element {
  const [form] = Form.useForm<ShipmentFormValues>();
  const { t } = useTranslation();
  const orderId = order?.id;
  const allocationsQuery = useQuery({
    queryKey: ['purchase-order-allocations', orderId],
    queryFn: () => getPurchaseOrderAllocations(orderId as number),
    enabled: open && orderId !== undefined,
  });
  const skusQuery = useQuery({
    queryKey: ['purchase-reference-skus'],
    queryFn: listPurchaseReferenceSkus,
    enabled: open,
  });
  const allocations = allocationsQuery.data ?? [];
  const warehouses = useMemo(() => Array.from(new Map(allocations.map((allocation) => [
    allocation.warehouseId,
    { label: allocation.warehouseCode ?? `#${allocation.warehouseId}`, value: allocation.warehouseId },
  ])).values()), [allocations]);
  const skuMap = useMemo(() => new Map((skusQuery.data ?? []).map((sku) => [sku.id, sku])), [skusQuery.data]);
  const selectedWarehouseId = Form.useWatch('warehouseId', form);

  const buildLines = (warehouseId?: number): ShipmentLine[] => allocations
    .filter((allocation) => allocation.warehouseId === warehouseId && (allocation.availableToShipQuantity ?? 0) > 0)
    .map((allocation) => {
      const orderItem = order?.items?.find((item) => item.id === allocation.purchaseOrderItemId);
      return {
        included: true,
        purchaseOrderItemId: allocation.purchaseOrderItemId,
        productSkuId: orderItem?.productSkuId,
        shippedQuantity: allocation.availableToShipQuantity ?? allocation.plannedQuantity,
        productionDate: orderItem?.productionDate,
        expiryDate: orderItem?.expiryDate,
        externalBatchCode: orderItem?.externalBatchCode,
      };
    });

  useEffect(() => {
    if (!open) return;
    form.resetFields();
  }, [asn?.id, form, open, orderId]);

  useEffect(() => {
    if (!open || allocations.length === 0) return;
    if (asn) {
      form.setFieldsValue({
        warehouseId: asn.warehouseId,
        supplierShipmentNo: asn.supplierShipmentNo,
        carrier: asn.carrier,
        trackingNo: asn.trackingNo,
        expectedArrivalDate: asn.expectedArrivalDate,
        remark: asn.remark,
        items: asn.items.map((item) => ({
          included: true,
          purchaseOrderItemId: item.purchaseOrderItemId,
          productSkuId: item.productSkuId,
          shippedQuantity: item.shippedQuantity,
          productionDate: item.productionDate,
          expiryDate: item.expiryDate,
          externalBatchCode: item.externalBatchCode,
          remark: item.remark,
        })),
      });
      return;
    }
    const warehouseId = warehouses[0]?.value;
    form.setFieldsValue({ warehouseId, items: buildLines(warehouseId) });
  }, [allocations, asn, form, open, warehouses]);

  const changeWarehouse = (warehouseId: number): void => {
    form.setFieldsValue({ warehouseId, items: buildLines(warehouseId) });
  };

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    const items = values.items
      .filter((item) => item.included && Number(item.shippedQuantity) > 0)
      .map(({ included: _included, productSkuId: _productSkuId, ...item }) => item);
    if (items.length === 0) {
      form.setFields([{ name: 'items', errors: [t('purchasing.validation.shipmentItemRequired')] }]);
      return;
    }
    await onConfirm({ ...values, items });
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okText={intent === 'quick-confirm' ? t('purchasing.actions.confirmShipment') : t('purchasing.asn.saveDraft')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={intent === 'quick-confirm'
        ? t('purchasing.confirm.title', { poNumber: order?.poNumber ?? '-' })
        : t(asn ? 'purchasing.asn.editTitle' : 'purchasing.asn.createTitle', { poNumber: order?.poNumber ?? '-', asnNumber: asn?.asnNumber ?? '-' })}
      width={1060}
      zIndex={WORKFLOW_MODAL_Z_INDEX}
    >
      <Alert message={t(intent === 'quick-confirm' ? 'purchasing.confirm.notice' : 'purchasing.asn.draftNotice')} showIcon type="info" />
      {allocationsQuery.isError && <Alert message={t('purchasing.messages.allocationLoadFailed')} showIcon type="error" />}
      <Form className="shipment-confirm-form" form={form} layout="vertical">
        <div className="shipment-meta-grid">
          <Form.Item label={t('purchasing.fields.targetWarehouse')} name="warehouseId" rules={[{ required: true, message: t('purchasing.validation.warehouseRequired') }]}>
            <Select onChange={changeWarehouse} options={warehouses} />
          </Form.Item>
          <Form.Item label={t('purchasing.fields.supplierShipmentNo')} name="supplierShipmentNo"><Input maxLength={100} /></Form.Item>
          <Form.Item label={t('purchasing.fields.carrier')} name="carrier"><Input maxLength={100} /></Form.Item>
          <Form.Item label={t('purchasing.fields.trackingNo')} name="trackingNo"><Input maxLength={100} /></Form.Item>
          <Form.Item label={t('purchasing.fields.expectedArrivalDate')} name="expectedArrivalDate"><Input type="date" /></Form.Item>
          <Form.Item label={t('common.remark')} name="remark"><Input maxLength={500} /></Form.Item>
        </div>
        <Form.List name="items">
          {(fields) => (
            <div className="shipment-lines">
              {fields.map((field, index) => {
                const line = form.getFieldValue(['items', field.name]) as ShipmentLine | undefined;
                const orderItem = order?.items?.find((item) => item.id === line?.purchaseOrderItemId);
                const allocation = allocations.find((item) => item.purchaseOrderItemId === line?.purchaseOrderItemId && item.warehouseId === selectedWarehouseId);
                const expiryManaged = line?.productSkuId !== undefined
                  && skuMap.get(line.productSkuId)?.expiryManagementEnabled === true;
                return (
                  <div className="shipment-line" key={field.key}>
                    <Form.Item hidden name={[field.name, 'purchaseOrderItemId']}><Input /></Form.Item>
                    <Form.Item hidden name={[field.name, 'productSkuId']}><Input /></Form.Item>
                    <Form.Item name={[field.name, 'included']} valuePropName="checked"><Checkbox aria-label={t('purchasing.confirm.includeLine')} /></Form.Item>
                    <div className="shipment-line-product"><strong>{orderItem?.productName ?? '-'}</strong><span>{orderItem?.productBarcode ?? '-'}</span></div>
                    <Form.Item label={t('purchasing.fields.shippedQuantity')} name={[field.name, 'shippedQuantity']} rules={[{ required: true, message: t('purchasing.validation.shippedQuantityRequired') }]}>
                      <InputNumber max={allocation?.availableToShipQuantity ?? allocation?.plannedQuantity} min={1} precision={0} />
                    </Form.Item>
                    <Form.Item label={t('purchasing.fields.externalBatchCode')} name={[field.name, 'externalBatchCode']}><Input maxLength={100} /></Form.Item>
                    <Form.Item label={t('purchasing.fields.productionDate')} name={[field.name, 'productionDate']} rules={[{ required: false, message: t('purchasing.validation.productionRequired') }]}><Input type="date" /></Form.Item>
                    <Form.Item label={t('purchasing.fields.expiryDate')} name={[field.name, 'expiryDate']} rules={[{ required: false, message: t('purchasing.validation.expiryRequired') }]}><Input type="date" /></Form.Item>
                    <Form.Item label={t('common.remark')} name={[field.name, 'remark']}><Input maxLength={500} /></Form.Item>
                    {expiryManaged && <span className="shipment-expiry-note">{t('purchasing.confirm.expiryManaged')}</span>}
                  </div>
                );
              })}
              {fields.length === 0 && <Alert message={t('purchasing.confirm.noAvailableLines')} showIcon type="warning" />}
            </div>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}
