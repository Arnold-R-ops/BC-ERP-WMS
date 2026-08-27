import { Alert, Checkbox, Form, Input, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type {
  EmergencyCorrectionApprovalMode,
  EmergencyCorrectionPolicy,
  UpdateEmergencyCorrectionPolicyPayload,
} from '../../api/emergencyCorrections';

interface PolicyFormValues {
  mode: EmergencyCorrectionApprovalMode;
  reason: string;
  confirmed: boolean;
}

interface EmergencyCorrectionPolicyModalProps {
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: UpdateEmergencyCorrectionPolicyPayload) => Promise<void>;
  open: boolean;
  policy?: EmergencyCorrectionPolicy;
}

export function EmergencyCorrectionPolicyModal({
  loading,
  onCancel,
  onConfirm,
  open,
  policy,
}: EmergencyCorrectionPolicyModalProps): JSX.Element {
  const [form] = Form.useForm<PolicyFormValues>();
  const { t } = useTranslation();
  const mode = Form.useWatch('mode', form);

  useEffect(() => {
    if (open && policy) {
      form.setFieldsValue({ mode: policy.configuredMode, reason: '', confirmed: false });
    } else {
      form.resetFields();
    }
  }, [form, open, policy]);

  const submit = async (values: PolicyFormValues): Promise<void> => {
    await onConfirm({ mode: values.mode, reason: values.reason.trim(), confirmed: true });
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okText={t('common.save')}
      onCancel={onCancel}
      onOk={() => form.submit()}
      open={open}
      title={t('corrections.policy.title')}
      width={640}
    >
      <Alert
        description={t('corrections.policy.description')}
        message={t('corrections.policy.current', {
          configured: policy ? t(`corrections.modes.${policy.configuredMode}`) : '-',
          effective: policy ? t(`corrections.modes.${policy.effectiveMode}`) : '-',
        })}
        showIcon
        type="info"
      />
      <Form form={form} layout="vertical" onFinish={submit} style={{ marginTop: 16 }}>
        <Form.Item label={t('corrections.policy.mode')} name="mode" rules={[{ required: true }]}>
          <Select
            options={(policy?.availableModes ?? []).map((value) => ({
              label: t(`corrections.modes.${value}`),
              value,
            }))}
          />
        </Form.Item>
        {mode ? <Alert message={t(`corrections.policy.modeDescriptions.${mode}`)} showIcon style={{ marginBottom: 16 }} type="warning" /> : null}
        <Form.Item
          label={t('corrections.policy.reason')}
          name="reason"
          rules={[{ required: true, whitespace: true, message: t('corrections.validation.policyReasonRequired') }]}
        >
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
        <Form.Item
          name="confirmed"
          rules={[{
            validator: (_, value: boolean) => value
              ? Promise.resolve()
              : Promise.reject(new Error(t('corrections.validation.policyConfirmRequired'))),
          }]}
          valuePropName="checked"
        >
          <Checkbox>{t('corrections.policy.confirm')}</Checkbox>
        </Form.Item>
      </Form>
    </Modal>
  );
}
