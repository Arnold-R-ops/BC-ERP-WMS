import { Form, Input, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listActiveWarehouses, listLocationsByWarehouse } from '../../api/masterData';
import type { InboundConfirmationPayload, InboundOrder } from '../../api/inbound';
import { WORKFLOW_MODAL_Z_INDEX } from '../../constants/layers';

function ConfirmationLocationField({ index }: { index: number }): JSX.Element {
  const { t } = useTranslation();
  const warehouseId = Form.useWatch(['confirmations', index, 'targetWarehouseId']) as number | undefined;
  const locationsQuery = useQuery({ queryKey: ['locations', warehouseId], queryFn: () => listLocationsByWarehouse(warehouseId as number), enabled: warehouseId !== undefined });
  return (
    <Form.Item label={t('inbound.fields.targetLocation')} name={[index, 'targetLocationId']} rules={[{ required: true, message: t('inbound.validation.locationRequired') }]}>
      <Select
        disabled={warehouseId === undefined}
        loading={locationsQuery.isLoading}
        options={(locationsQuery.data ?? []).filter((location) => location.enabled !== false).map((location) => ({ label: location.locationCode ?? `#${location.id}`, value: location.id }))}
        showSearch
        optionFilterProp="label"
      />
    </Form.Item>
  );
}

interface InboundConfirmModalProps {
  loading: boolean;
  open: boolean;
  order?: InboundOrder;
  onCancel: () => void;
  onConfirm: (payload: InboundConfirmationPayload) => Promise<void> | void;
}

export function InboundConfirmModal({ loading, open, order, onCancel, onConfirm }: InboundConfirmModalProps): JSX.Element {
  const [form] = Form.useForm<InboundConfirmationPayload>();
  const { t } = useTranslation();
  const warehousesQuery = useQuery({ queryKey: ['warehouses', 'active'], queryFn: listActiveWarehouses, enabled: open });
  const warehouseOptions = (warehousesQuery.data ?? []).map((warehouse) => ({ label: warehouse.name ?? warehouse.code ?? `#${warehouse.id}`, value: warehouse.id }));

  useEffect(() => {
    if (open && order) {
      form.setFieldsValue({
        comment: '',
        confirmations: (order.items ?? []).map((item) => ({
          itemId: item.id as number,
          confirmedQty: item.planQty ?? 0,
          expiryDate: item.expiryDate ?? '',
          productionDate: item.productionDate,
          externalBatchCode: item.externalBatchCode,
          targetWarehouseId: item.targetWarehouseId as number,
          targetLocationId: item.targetLocationId as number,
        })),
      });
    }
  }, [form, open, order]);

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okText={t('inbound.actions.confirmOrder')}
      onCancel={onCancel}
      onOk={() => void form.validateFields().then((values) => onConfirm({
        ...values,
        confirmations: values.confirmations.map((item) => ({
          ...item,
          confirmedQty: Number(item.confirmedQty),
        })),
      }))}
      open={open}
      title={t('inbound.confirm.title', { orderNo: order?.orderNo ?? '-' })}
      width={980}
      zIndex={WORKFLOW_MODAL_Z_INDEX}
    >
      <Form form={form} layout="vertical">
        <Form.Item label={t('workflow.comment')} name="comment"><Input.TextArea maxLength={500} rows={2} /></Form.Item>
        <Form.List name="confirmations">
          {(fields) => fields.map((field, index) => (
            <div className="workflow-line workflow-line-wide" key={field.key}>
              <Form.Item hidden name={[field.name, 'itemId']}><Input /></Form.Item>
              <strong>{order?.items?.[index]?.productName ?? '-'}</strong>
              <Form.Item label={t('inbound.fields.confirmedQty')} name={[field.name, 'confirmedQty']} rules={[{ required: true, message: t('inbound.validation.quantityRequired') }]}><Input min={0} type="number" /></Form.Item>
              <Form.Item label={t('inbound.fields.expiryDate')} name={[field.name, 'expiryDate']} rules={[{ required: true, message: t('inbound.validation.expiryRequired') }]}><Input type="date" /></Form.Item>
              <Form.Item label={t('inbound.fields.productionDate')} name={[field.name, 'productionDate']}><Input type="date" /></Form.Item>
              <Form.Item label={t('inbound.fields.externalBatchCode')} name={[field.name, 'externalBatchCode']}><Input maxLength={100} /></Form.Item>
              <Form.Item label={t('inbound.fields.targetWarehouse')} name={[field.name, 'targetWarehouseId']} rules={[{ required: true, message: t('inbound.validation.warehouseRequired') }]}>
                <Select options={warehouseOptions} showSearch optionFilterProp="label" />
              </Form.Item>
              <ConfirmationLocationField index={field.name} />
            </div>
          ))}
        </Form.List>
      </Form>
    </Modal>
  );
}
