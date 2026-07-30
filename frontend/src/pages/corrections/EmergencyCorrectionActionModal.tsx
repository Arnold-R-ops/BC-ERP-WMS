import { Alert, Form, Input, Modal } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { EmergencyCorrection, EmergencyCorrectionActionPayload } from '../../api/emergencyCorrections';

export type CorrectionAction = 'submit' | 'review' | 'approve' | 'reject';

interface EmergencyCorrectionActionModalProps {
  action?: CorrectionAction;
  correction?: EmergencyCorrection;
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: EmergencyCorrectionActionPayload) => Promise<void>;
}

export function EmergencyCorrectionActionModal({
  action,
  correction,
  loading,
  onCancel,
  onConfirm,
}: EmergencyCorrectionActionModalProps): JSX.Element {
  const [form] = Form.useForm<EmergencyCorrectionActionPayload>();
  const { t } = useTranslation();
  const open = action !== undefined && correction !== undefined;

  useEffect(() => {
    if (open) form.setFieldsValue({ comment: '' });
    else form.resetFields();
  }, [form, open]);

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okButtonProps={{ danger: action === 'approve' || action === 'reject' }}
      okText={action ? t(`corrections.actions.${action}`) : t('common.confirm')}
      onCancel={onCancel}
      onOk={() => form.submit()}
      open={open}
      title={action ? t('corrections.action.title', { action: t(`corrections.actions.${action}`), correctionNo: correction?.correctionNo ?? '-' }) : ''}
    >
      <Alert message={action ? t(`corrections.action.notices.${action}`) : ''} showIcon type={action === 'approve' || action === 'reject' ? 'warning' : 'info'} />
      <Form form={form} layout="vertical" onFinish={onConfirm} style={{ marginTop: 16 }}>
        <Form.Item label={t('workflow.comment')} name="comment" rules={[{ required: true, whitespace: true, message: t('corrections.validation.commentRequired') }]}>
          <Input.TextArea maxLength={500} rows={4} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
