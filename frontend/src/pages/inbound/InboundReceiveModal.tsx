import { Form, Input, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listLocationsByWarehouse } from '../../api/masterData';
import type { InboundOrder, InboundReceiptPayload } from '../../api/inbound';
import { WORKFLOW_MODAL_Z_INDEX } from '../../constants/layers';

function ReceiptLocationField({ index, warehouseId }: { index: number; warehouseId?: number }): JSX.Element {
  const { t } = useTranslation();
  const locationsQuery = useQuery({ queryKey: ['locations', warehouseId], queryFn: () => listLocationsByWarehouse(warehouseId as number), enabled: warehouseId !== undefined });
  return (
    <Form.Item label={t('inbound.fields.actualLocation')} name={[index, 'locationId']} rules={[{ required: true, message: t('inbound.validation.locationRequired') }]}>
      <Select loading={locationsQuery.isLoading} options={(locationsQuery.data ?? []).filter((location) => location.enabled !== false).map((location) => ({ label: location.locationCode ?? `#${location.id}`, value: location.id }))} showSearch optionFilterProp="label" />
    </Form.Item>
  );
}

interface InboundReceiveModalProps {
  loading: boolean;
  open: boolean;
  order?: InboundOrder;
  onCancel: () => void;
  onConfirm: (payload: InboundReceiptPayload) => Promise<void> | void;
}

export function InboundReceiveModal({ loading, open, order, onCancel, onConfirm }: InboundReceiveModalProps): JSX.Element {
  const [form] = Form.useForm<InboundReceiptPayload>();
  const { t } = useTranslation();
  useEffect(() => {
    if (open && order) {
      form.setFieldsValue({ receipts: (order.items ?? []).map((item) => ({ itemId: item.id as number, actualQty: item.confirmedQty ?? item.planQty ?? 0, locationId: item.targetLocationId as number })) });
    }
  }, [form, open, order]);
  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okText={t('inbound.actions.receive')}
      onCancel={onCancel}
      onOk={() => void form.validateFields().then((values) => onConfirm({
        ...values,
        receipts: values.receipts.map((receipt) => ({
          ...receipt,
          actualQty: Number(receipt.actualQty),
        })),
      }))}
      open={open}
      title={t('inbound.receive.title', { orderNo: order?.orderNo ?? '-' })}
      width={820}
      zIndex={WORKFLOW_MODAL_Z_INDEX}
    >
      <p className="modal-description">{t('inbound.receive.notice')}</p>
      <Form form={form} layout="vertical">
        <Form.List name="receipts">
          {(fields) => fields.map((field, index) => (
            <div className="workflow-line workflow-line-grid" key={field.key}>
              <Form.Item hidden name={[field.name, 'itemId']}><Input /></Form.Item>
              <div><strong>{order?.items?.[index]?.productName ?? '-'}</strong><div className="table-secondary">{order?.items?.[index]?.batchCode ?? '-'}</div></div>
              <Form.Item label={t('inbound.fields.actualQty')} name={[field.name, 'actualQty']} rules={[{ required: true, message: t('inbound.validation.quantityRequired') }]}><Input min={0} type="number" /></Form.Item>
              <ReceiptLocationField index={field.name} warehouseId={order?.items?.[index]?.targetWarehouseId} />
            </div>
          ))}
        </Form.List>
      </Form>
    </Modal>
  );
}
