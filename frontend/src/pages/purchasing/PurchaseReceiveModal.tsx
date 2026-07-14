import { Form, Input, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listActiveWarehouses, listLocationsByWarehouse } from '../../api/masterData';
import type { PurchaseOrder, PurchaseReceiptPayload } from '../../api/purchasing';
import { WORKFLOW_MODAL_Z_INDEX } from '../../constants/layers';

interface AssignmentRow {
  itemId: number;
  batchCode: string;
  productName?: string;
  warehouseId?: number;
  locationId?: number;
}

interface PurchaseReceiveModalProps {
  loading: boolean;
  open: boolean;
  operatorId?: number;
  operatorName: string;
  order?: PurchaseOrder;
  onCancel: () => void;
  onConfirm: (payload: PurchaseReceiptPayload) => Promise<void> | void;
}

function LocationAssignmentFields({ index }: { index: number }): JSX.Element {
  const { t } = useTranslation();
  const warehouseId = Form.useWatch(['assignments', index, 'warehouseId']) as number | undefined;
  const locationsQuery = useQuery({
    queryKey: ['locations', warehouseId],
    queryFn: () => listLocationsByWarehouse(warehouseId as number),
    enabled: warehouseId !== undefined,
  });

  return (
    <Form.Item
      label={t('purchasing.fields.location')}
      name={[index, 'locationId']}
      rules={[{ required: true, message: t('purchasing.validation.locationRequired') }]}
    >
      <Select
        disabled={warehouseId === undefined}
        loading={locationsQuery.isLoading}
        options={(locationsQuery.data ?? []).filter((location) => location.enabled !== false).map((location) => ({
          label: location.locationCode ?? `#${location.id}`,
          value: location.id,
        }))}
        showSearch
        optionFilterProp="label"
      />
    </Form.Item>
  );
}

export function PurchaseReceiveModal({
  loading,
  open,
  operatorId,
  operatorName,
  order,
  onCancel,
  onConfirm,
}: PurchaseReceiveModalProps): JSX.Element {
  const [form] = Form.useForm<{ assignments: AssignmentRow[] }>();
  const { t } = useTranslation();
  const warehousesQuery = useQuery({
    queryKey: ['warehouses', 'active'],
    queryFn: listActiveWarehouses,
    enabled: open,
  });

  const pendingAssignments = (order?.items ?? []).flatMap((item) =>
    (item.batches ?? [])
      .filter((batch) => !batch.entryDate && (!batch.locationCode || batch.locationCode === 'UNASSIGNED'))
      .map((batch) => ({ itemId: item.id as number, batchCode: batch.batchCode as string, productName: item.productName })),
  );

  useEffect(() => {
    if (open) {
      form.setFieldsValue({ assignments: pendingAssignments });
    }
  }, [form, open, order?.id]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    const grouped = new Map<number, { batchCode: string; locationId: number }[]>();
    values.assignments.forEach((assignment) => {
      const current = grouped.get(assignment.itemId) ?? [];
      current.push({ batchCode: assignment.batchCode, locationId: assignment.locationId as number });
      grouped.set(assignment.itemId, current);
    });
    await onConfirm({
      operatorId: operatorId as number,
      operatorName,
      receiveItems: Array.from(grouped, ([itemId, batches]) => ({ itemId, batches })),
    });
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okButtonProps={{ disabled: pendingAssignments.length === 0 || operatorId === undefined }}
      okText={t('purchasing.actions.receive')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={t('purchasing.receive.title', { poNumber: order?.poNumber ?? '-' })}
      width={900}
      zIndex={WORKFLOW_MODAL_Z_INDEX}
    >
      <p className="modal-description">{t('purchasing.receive.notice')}</p>
      <Form form={form} layout="vertical">
        <Form.List name="assignments">
          {(fields) => fields.map((field, index) => (
            <div className="workflow-line workflow-line-grid" key={field.key}>
              <Form.Item hidden name={[field.name, 'itemId']}><Input /></Form.Item>
              <Form.Item hidden name={[field.name, 'batchCode']}><Input /></Form.Item>
              <div>
                <strong>{pendingAssignments[index]?.productName ?? '-'}</strong>
                <div className="table-secondary">{pendingAssignments[index]?.batchCode ?? '-'}</div>
              </div>
              <Form.Item label={t('purchasing.fields.warehouse')} name={[field.name, 'warehouseId']} rules={[{ required: true, message: t('purchasing.validation.warehouseRequired') }]}>
                <Select
                  loading={warehousesQuery.isLoading}
                  options={(warehousesQuery.data ?? []).map((warehouse) => ({ label: warehouse.name ?? warehouse.code ?? `#${warehouse.id}`, value: warehouse.id }))}
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
              <LocationAssignmentFields index={field.name} />
            </div>
          ))}
        </Form.List>
      </Form>
    </Modal>
  );
}
