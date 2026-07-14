import { Form, Input, Modal } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { WORKFLOW_MODAL_Z_INDEX } from '../constants/layers';

interface ReasonValues {
  reason: string;
}

interface ReasonModalProps {
  danger?: boolean;
  description?: string;
  loading?: boolean;
  open: boolean;
  title: string;
  onCancel: () => void;
  onConfirm: (reason: string) => Promise<void> | void;
}

export function ReasonModal({
  danger = false,
  description,
  loading = false,
  open,
  title,
  onCancel,
  onConfirm,
}: ReasonModalProps): JSX.Element {
  const [form] = Form.useForm<ReasonValues>();
  const { t } = useTranslation();

  useEffect(() => {
    if (open) {
      form.resetFields();
    }
  }, [form, open]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    await onConfirm(values.reason.trim());
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okButtonProps={{ danger }}
      okText={t('common.confirm')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={title}
      zIndex={WORKFLOW_MODAL_Z_INDEX}
    >
      {description && <p className="modal-description">{description}</p>}
      <Form form={form} layout="vertical">
        <Form.Item
          label={t('workflow.reason')}
          name="reason"
          rules={[
            { required: true, message: t('workflow.validation.reasonRequired') },
            { max: 500, message: t('workflow.validation.reasonTooLong') },
          ]}
        >
          <Input.TextArea maxLength={500} rows={4} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
