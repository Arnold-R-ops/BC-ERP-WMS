import { Form, Input, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { SalesApprovalPayload, SalesOrder } from '../../api/sales';
import { WORKFLOW_MODAL_Z_INDEX } from '../../constants/layers';

interface SalesApprovalModalProps {
  loading: boolean;
  open: boolean;
  order?: SalesOrder;
  onCancel: () => void;
  onConfirm: (payload: SalesApprovalPayload) => Promise<void> | void;
}

export function SalesApprovalModal({
  loading,
  open,
  order,
  onCancel,
  onConfirm,
}: SalesApprovalModalProps): JSX.Element {
  const [form] = Form.useForm<SalesApprovalPayload>();
  const { t } = useTranslation();

  useEffect(() => {
    if (open) {
      form.setFieldsValue({ allocationPolicy: 'FULL_ONLY', comment: '' });
    }
  }, [form, open]);

  const submit = async (): Promise<void> => {
    await onConfirm(await form.validateFields());
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okText={t('sales.actions.approve')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={t('sales.approval.title', { orderNo: order?.orderNo ?? '-' })}
      width={620}
      zIndex={WORKFLOW_MODAL_Z_INDEX}
    >
      <p className="modal-description">{t('sales.approval.inventoryNotice')}</p>
      <Form form={form} layout="vertical">
        <Form.Item label={t('sales.fields.allocationPolicy')} name="allocationPolicy" rules={[{ required: true }]}>
          <Select
            options={[
              { label: t('sales.policies.FULL_ONLY'), value: 'FULL_ONLY' },
              { label: t('sales.policies.PARTIAL_BACKORDER'), value: 'PARTIAL_BACKORDER' },
              { label: t('sales.policies.WAIT_FOR_COMPLETE'), value: 'WAIT_FOR_COMPLETE' },
            ]}
          />
        </Form.Item>
        <Form.Item label={t('sales.fields.requestedShipDate')} name="requestedShipDate">
          <Input type="date" />
        </Form.Item>
        <Form.Item label={t('sales.fields.promisedShipDate')} name="promisedShipDate">
          <Input type="date" />
        </Form.Item>
        <Form.Item label={t('workflow.comment')} name="comment">
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
