import { Form, Input, Modal } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { InboundOrder } from '../../api/inbound';
import { WORKFLOW_MODAL_Z_INDEX } from '../../constants/layers';

interface InboundApprovalModalProps {
  loading: boolean;
  open: boolean;
  order?: InboundOrder;
  onCancel: () => void;
  onConfirm: (comment?: string) => Promise<void> | void;
}

export function InboundApprovalModal({ loading, open, order, onCancel, onConfirm }: InboundApprovalModalProps): JSX.Element {
  const [form] = Form.useForm<{ comment?: string }>();
  const { t } = useTranslation();
  useEffect(() => { if (open) form.resetFields(); }, [form, open]);
  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okText={t('inbound.actions.approve')}
      onCancel={onCancel}
      onOk={() => void form.validateFields().then((values) => onConfirm(values.comment?.trim()))}
      open={open}
      title={t('inbound.approval.title', { orderNo: order?.orderNo ?? '-' })}
      zIndex={WORKFLOW_MODAL_Z_INDEX}
    >
      <Form form={form} layout="vertical">
        <Form.Item label={t('workflow.comment')} name="comment"><Input.TextArea maxLength={500} rows={4} showCount /></Form.Item>
      </Form>
    </Modal>
  );
}
