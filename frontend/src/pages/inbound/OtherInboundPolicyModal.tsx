import { Alert, Checkbox, Form, Input, Modal, Switch } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { OtherInboundBusinessType, OtherInboundPolicy, OtherInboundPolicyUpdatePayload } from '../../api/inbound';

const BUSINESS_TYPES: OtherInboundBusinessType[] = [
  'FREE_SAMPLE',
  'CUSTOMER_SUPPLIED',
  'PRODUCTION_COMPLETION',
  'SUBCONTRACT_RETURN',
  'AUTHORIZED_SPECIAL',
];

interface PolicyFormValues {
  enabled: boolean;
  allowedBusinessTypes: OtherInboundBusinessType[];
  reason: string;
}

interface OtherInboundPolicyModalProps {
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: OtherInboundPolicyUpdatePayload) => Promise<void> | void;
  open: boolean;
  policy?: OtherInboundPolicy;
}

export function OtherInboundPolicyModal({
  loading,
  onCancel,
  onConfirm,
  open,
  policy,
}: OtherInboundPolicyModalProps): JSX.Element {
  const [form] = Form.useForm<PolicyFormValues>();
  const { t } = useTranslation();

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      enabled: policy?.enabled ?? false,
      allowedBusinessTypes: policy?.allowedBusinessTypes?.length ? policy.allowedBusinessTypes : ['FREE_SAMPLE'],
      reason: '',
    });
  }, [form, open, policy]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    await onConfirm({ ...values, confirmed: true });
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okText={t('common.confirm')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={t('inbound.policy.settingsTitle')}
    >
      <Alert message={t('inbound.policy.settingsNotice')} showIcon type="warning" />
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item label={t('inbound.policy.enabledField')} name="enabled" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item
          label={t('inbound.policy.allowedTypes')}
          name="allowedBusinessTypes"
          rules={[{ required: true, message: t('inbound.policy.allowedTypesRequired') }]}
        >
          <Checkbox.Group
            options={BUSINESS_TYPES.map((value) => ({ label: t(`inbound.businessTypes.${value}`), value }))}
          />
        </Form.Item>
        <Form.Item
          label={t('inbound.policy.changeReason')}
          name="reason"
          rules={[{ required: true, message: t('inbound.policy.reasonRequired') }]}
        >
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
