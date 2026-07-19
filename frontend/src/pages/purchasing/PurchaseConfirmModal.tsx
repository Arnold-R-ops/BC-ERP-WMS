import { Form, Input, Modal, Table, type TableColumnsType } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { PurchaseConfirmPayload, PurchaseOrder, PurchaseOrderItem } from '../../api/purchasing';
import { WORKFLOW_MODAL_Z_INDEX } from '../../constants/layers';

interface PurchaseConfirmModalProps {
  loading: boolean;
  open: boolean;
  order?: PurchaseOrder;
  onCancel: () => void;
  onConfirm: (payload: PurchaseConfirmPayload) => Promise<void> | void;
}

interface ConfirmationRow {
  itemId: number;
  productName?: string;
  expiryDate?: string;
  productionDate?: string;
}

export function PurchaseConfirmModal({
  loading,
  open,
  order,
  onCancel,
  onConfirm,
}: PurchaseConfirmModalProps): JSX.Element {
  const [form] = Form.useForm<{ items: ConfirmationRow[] }>();
  const { t } = useTranslation();

  useEffect(() => {
    if (open && order) {
      form.setFieldsValue({
        items: (order.items ?? []).map((item) => ({
          itemId: item.id as number,
          productName: item.productName,
          expiryDate: item.expiryDate,
          productionDate: item.productionDate,
        })),
      });
    }
  }, [form, open, order]);

  const columns: TableColumnsType<PurchaseOrderItem> = [
    { title: t('purchasing.fields.product'), dataIndex: 'productName' },
    { title: t('purchasing.fields.orderedQuantity'), dataIndex: 'orderedQuantity', align: 'right', width: 120 },
  ];

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    await onConfirm({ items: values.items.map(({ itemId, expiryDate, productionDate }) => ({ itemId, expiryDate: expiryDate as string, productionDate })) });
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okText={t('purchasing.actions.confirmAsn')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={t('purchasing.confirm.title', { poNumber: order?.poNumber ?? '-' })}
      width={820}
      zIndex={WORKFLOW_MODAL_Z_INDEX}
    >
      <p className="modal-description">{t('purchasing.confirm.notice')}</p>
      <Table columns={columns} dataSource={order?.items ?? []} pagination={false} rowKey={(item) => item.id ?? item.productSkuId ?? 0} size="small" />
      <Form className="workflow-form-list" form={form} layout="vertical">
        <Form.List name="items">
          {(fields) => fields.map((field, index) => (
            <div className="workflow-line" key={field.key}>
              <Form.Item hidden name={[field.name, 'itemId']}><Input /></Form.Item>
              <strong>{order?.items?.[index]?.productName ?? '-'}</strong>
              <Form.Item label={t('purchasing.fields.expiryDate')} name={[field.name, 'expiryDate']} rules={[{ required: true, message: t('purchasing.validation.expiryRequired') }]}>
                <Input type="date" />
              </Form.Item>
              <Form.Item label={t('purchasing.fields.productionDate')} name={[field.name, 'productionDate']}>
                <Input type="date" />
              </Form.Item>
            </div>
          ))}
        </Form.List>
      </Form>
    </Modal>
  );
}
